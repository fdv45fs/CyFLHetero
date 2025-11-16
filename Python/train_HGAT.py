"""
HGAT (Heterogeneous Graph Attention Network) for Unsupervised Learning

This script implements HGAT using PyTorch Geometric's HGTConv (Heterogeneous Graph Transformer)
for unsupervised node embedding learning on heterogeneous graphs (e.g., Drug-Gene networks).

Key Features:
- Works on heterogeneous graphs (multiple node types, e.g., Drug, Gene)
- Unsupervised learning (no labels needed)
- Type-aware attention mechanism
- Automatic feature generation if not provided
- Supports node clustering and link prediction

Author: CyFLHetero Team
"""

import torch
import torch.nn as nn
import torch.nn.functional as F
from torch_geometric.nn import HGTConv, Linear
from torch_geometric.data import HeteroData
import pickle
import numpy as np
from sklearn.preprocessing import StandardScaler

# Global variables to store trained model and data
model = None
data = None
node_map = None


class HGAT(nn.Module):
    """
    Heterogeneous Graph Attention Network using HGTConv layers.
    
    Architecture:
    - Input: Heterogeneous graph with node features
    - HGT Layers: 2 layers with type-aware attention
    - Output: Node embeddings for each node type
    """
    def __init__(self, hidden_channels, out_channels, num_heads, num_layers, metadata):
        super().__init__()
        self.hidden_channels = hidden_channels
        self.out_channels = out_channels
        self.num_layers = num_layers
        
        # Linear layers to project input features to hidden dimension
        self.lin_dict = nn.ModuleDict()
        for node_type in metadata[0]:
            self.lin_dict[node_type] = Linear(-1, hidden_channels)
        
        # HGT Convolutional layers
        self.convs = nn.ModuleList()
        for _ in range(num_layers):
            # Note: 'group' parameter removed in newer PyG versions
            conv = HGTConv(hidden_channels, hidden_channels, metadata, num_heads)
            self.convs.append(conv)
        
        # Output projection layers
        self.out_dict = nn.ModuleDict()
        for node_type in metadata[0]:
            self.out_dict[node_type] = Linear(hidden_channels, out_channels)

    def forward(self, x_dict, edge_index_dict):
        # Project input features to hidden dimension
        x_dict = {
            node_type: self.lin_dict[node_type](x).relu()
            for node_type, x in x_dict.items()
        }
        
        # Apply HGT layers
        for conv in self.convs:
            x_dict = conv(x_dict, edge_index_dict)
            x_dict = {key: F.relu(x) for key, x in x_dict.items()}
        
        # Project to output dimension
        out_dict = {
            node_type: self.out_dict[node_type](x)
            for node_type, x in x_dict.items()
        }
        
        return out_dict


def create_hetero_data(edges, node_types=None):
    """
    Create HeteroData object from edge list.
    
    Args:
        edges: List of edge dicts with 'source', 'target', 'source_type', 'target_type'
        node_types: List of node types (e.g., ['drug', 'gene'])
    
    Returns:
        data: HeteroData object
        node_map: Dict mapping {node_type: {node_name: node_idx}}
    """
    if node_types is None:
        node_types = ['drug', 'gene']  # Default for ChG-Miner
    
    print(f"[HGAT] Creating heterogeneous graph with node types: {node_types}")
    
    # Create node mappings for each type
    node_map = {node_type: {} for node_type in node_types}
    
    # Collect all nodes by type
    for edge in edges:
        source_type = edge.get('source_type', node_types[0])
        target_type = edge.get('target_type', node_types[1])
        
        source_node = edge['source']
        target_node = edge['target']
        
        if source_node not in node_map[source_type]:
            node_map[source_type][source_node] = len(node_map[source_type])
        
        if target_node not in node_map[target_type]:
            node_map[target_type][target_node] = len(node_map[target_type])
    
    print(f"[HGAT] Node counts: {[(t, len(node_map[t])) for t in node_types]}")
    
    # Create HeteroData object
    data = HeteroData()
    
    # Auto-generate features for each node type (identity matrix or degree-based)
    for node_type in node_types:
        num_nodes = len(node_map[node_type])
        # Use identity matrix as initial features (each node gets a unique one-hot vector)
        # For large graphs, use a smaller dimension
        feature_dim = min(128, num_nodes)
        if num_nodes <= 128:
            features = torch.eye(num_nodes)
        else:
            # Random initialization for large graphs
            features = torch.randn(num_nodes, feature_dim)
            features = F.normalize(features, dim=1)
        
        data[node_type].x = features
        print(f"[HGAT] Generated features for '{node_type}': {features.shape}")
    
    # Build edge indices - Group by actual edge types from data
    edge_groups = {}
    for edge in edges:
        source_type = edge.get('source_type', node_types[0])
        target_type = edge.get('target_type', node_types[1])
        
        # Create edge type key
        edge_type_key = (source_type, 'interacts', target_type)
        
        if edge_type_key not in edge_groups:
            edge_groups[edge_type_key] = []
        
        source_idx = node_map[source_type][edge['source']]
        target_idx = node_map[target_type][edge['target']]
        
        edge_groups[edge_type_key].append([source_idx, target_idx])
    
    # Add all edge types to data
    for edge_type_key, edge_list in edge_groups.items():
        if edge_list:
            edge_index = torch.tensor(edge_list, dtype=torch.long).t().contiguous()
            data[edge_type_key].edge_index = edge_index
            
            # Add reverse edges for bidirectional message passing
            src_type, rel, dst_type = edge_type_key
            reverse_edge_type = (dst_type, f'{rel}_rev', src_type)
            data[reverse_edge_type].edge_index = edge_index.flip([0])
            
            print(f"[HGAT] Created edge type '{edge_type_key}': {edge_index.shape[1]} edges")
            print(f"[HGAT] Created reverse edge type '{reverse_edge_type}': {edge_index.shape[1]} edges")
    
    return data, node_map


def unsupervised_loss(embeddings_dict, edge_index_dict, neg_sampling_ratio=1.0):
    """
    Unsupervised loss based on link prediction (binary cross-entropy).
    
    Positive samples: Existing edges
    Negative samples: Randomly sampled non-existing edges
    """
    total_loss = 0.0
    num_edge_types = 0
    
    for edge_type, edge_index in edge_index_dict.items():
        if edge_index.size(1) == 0:
            continue
        
        src_type, _, dst_type = edge_type
        
        if src_type not in embeddings_dict or dst_type not in embeddings_dict:
            continue
        
        src_emb = embeddings_dict[src_type]
        dst_emb = embeddings_dict[dst_type]
        
        # Positive edges
        pos_src = edge_index[0]
        pos_dst = edge_index[1]
        pos_score = (src_emb[pos_src] * dst_emb[pos_dst]).sum(dim=-1)
        pos_loss = F.binary_cross_entropy_with_logits(pos_score, torch.ones_like(pos_score))
        
        # Negative sampling
        num_neg = int(edge_index.size(1) * neg_sampling_ratio)
        neg_src = torch.randint(0, src_emb.size(0), (num_neg,), device=edge_index.device)
        neg_dst = torch.randint(0, dst_emb.size(0), (num_neg,), device=edge_index.device)
        neg_score = (src_emb[neg_src] * dst_emb[neg_dst]).sum(dim=-1)
        neg_loss = F.binary_cross_entropy_with_logits(neg_score, torch.zeros_like(neg_score))
        
        total_loss += (pos_loss + neg_loss)
        num_edge_types += 1
    
    return total_loss / max(num_edge_types, 1)


def train_model_HGAT(edges, hidden_channels=128, out_channels=64, num_heads=4, num_layers=2, 
                     num_epochs=5, learning_rate=0.001, weight_decay=5e-4):
    """
    Train HGAT model for unsupervised node embedding learning.
    
    Args:
        edges: List of edge dicts with 'source', 'target', 'source_type', 'target_type'
        hidden_channels: Hidden dimension
        out_channels: Output embedding dimension
        num_heads: Number of attention heads
        num_layers: Number of HGT layers
        num_epochs: Number of training epochs
        learning_rate: Learning rate
        weight_decay: Weight decay for regularization
    """
    global model, data, node_map
    
    print("\n" + "="*60)
    print("HGAT (Heterogeneous Graph Attention Network) Training")
    print("="*60)
    
    # Detect node types from edges (assume first edge has type info)
    if edges and 'source_type' in edges[0]:
        node_types = list(set([e['source_type'] for e in edges] + [e['target_type'] for e in edges]))
    else:
        # Default: drug-gene graph
        node_types = ['drug', 'gene']
    
    print(f"[HGAT] Detected node types: {node_types}")
    
    # Create HeteroData
    data, node_map = create_hetero_data(edges, node_types)
    
    # Check device (auto-detect GPU if available)
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    print(f"[HGAT] Using device: {device}")
    if device.type == 'cuda':
        print(f"[HGAT] GPU detected: {torch.cuda.get_device_name(0)}")
        print(f"[HGAT] CUDA version: {torch.version.cuda}")
    
    # Validate edge indices before moving to device
    print(f"[HGAT] Validating edge indices...")
    for edge_type, edge_index in data.edge_index_dict.items():
        src_type, _, dst_type = edge_type
        num_src = data[src_type].x.size(0)
        num_dst = data[dst_type].x.size(0)
        
        if edge_index.size(0) != 2:
            raise ValueError(f"Edge index for {edge_type} must have shape [2, num_edges]")
        
        max_src_idx = edge_index[0].max().item()
        max_dst_idx = edge_index[1].max().item()
        
        if max_src_idx >= num_src:
            raise ValueError(f"Edge index for {edge_type}: source index {max_src_idx} >= num nodes {num_src}")
        if max_dst_idx >= num_dst:
            raise ValueError(f"Edge index for {edge_type}: target index {max_dst_idx} >= num nodes {num_dst}")
    
    print(f"[HGAT] Edge indices validated successfully!")
    data = data.to(device)
    
    # Initialize model
    metadata = data.metadata()
    model = HGAT(hidden_channels, out_channels, num_heads, num_layers, metadata).to(device)
    
    print(f"[HGAT] Model architecture:")
    print(f"  - Hidden channels: {hidden_channels}")
    print(f"  - Output channels: {out_channels}")
    print(f"  - Attention heads: {num_heads}")
    print(f"  - Number of layers: {num_layers}")
    # Note: Cannot count parameters before forward pass due to lazy initialization
    print(f"  - Using lazy initialization (parameters will be initialized on first forward pass)")
    
    # Optimizer
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate, weight_decay=weight_decay)
    
    # Training loop
    print("\n" + "="*60)
    print("Training Progress")
    print("="*60)
    
    model.train()
    for epoch in range(num_epochs):
        optimizer.zero_grad()
        
        # Forward pass
        embeddings_dict = model(data.x_dict, data.edge_index_dict)
        
        # Compute unsupervised loss
        loss = unsupervised_loss(embeddings_dict, data.edge_index_dict)
        
        # Backward pass
        loss.backward()
        optimizer.step()
        
        # Print every epoch for quick testing (num_epochs=5)
        print(f"Epoch {epoch:4d} | Loss: {loss.item():.6f}")
    
    print("="*60)
    print("Training Complete!")
    print("="*60 + "\n")
    
    # Save model and embeddings
    model.eval()
    with torch.no_grad():
        embeddings_dict = model(data.x_dict, data.edge_index_dict)
        embeddings_dict = {k: v.cpu().numpy() for k, v in embeddings_dict.items()}
    
    # Save to disk
    model_path = 'HGAT_trained_model.pth'
    embedding_path = 'HGAT_embeddings.pkl'
    
    torch.save({
        'model_state_dict': model.state_dict(),
        'metadata': metadata,
        'hidden_channels': hidden_channels,
        'out_channels': out_channels,
        'num_heads': num_heads,
        'num_layers': num_layers
    }, model_path)
    print(f"✅ Model saved to {model_path}")
    
    with open(embedding_path, 'wb') as f:
        pickle.dump({
            'embeddings': embeddings_dict,
            'node_mapping': node_map,
            'edge_index_dict': {k: v.cpu().numpy() for k, v in data.edge_index_dict.items()}
        }, f)
    print(f"✅ Embeddings saved to {embedding_path}")
    
    print(f"\n[HGAT] Embedding dimensions:")
    for node_type, emb in embeddings_dict.items():
        print(f"  - {node_type}: {emb.shape}")


def get_embeddings():
    """
    Get node embeddings from trained model.
    
    Returns:
        embeddings_dict: Dict mapping {node_type: embeddings}
        node_map: Dict mapping {node_type: {node_name: node_idx}}
    """
    global model, data, node_map
    
    if model is None or data is None:
        raise ValueError("Model not trained! Please train HGAT first.")
    
    model.eval()
    with torch.no_grad():
        embeddings_dict = model(data.x_dict, data.edge_index_dict)
        embeddings_dict = {k: v.cpu() for k, v in embeddings_dict.items()}
    
    return embeddings_dict, node_map


if __name__ == "__main__":
    # Test with sample Drug-Gene network
    print("Testing HGAT with sample Drug-Gene network...")
    
    sample_edges = [
        {'source': 'DB00001', 'target': 'P12345', 'source_type': 'drug', 'target_type': 'gene'},
        {'source': 'DB00001', 'target': 'P23456', 'source_type': 'drug', 'target_type': 'gene'},
        {'source': 'DB00002', 'target': 'P12345', 'source_type': 'drug', 'target_type': 'gene'},
        {'source': 'DB00002', 'target': 'P34567', 'source_type': 'drug', 'target_type': 'gene'},
        {'source': 'DB00003', 'target': 'P23456', 'source_type': 'drug', 'target_type': 'gene'},
    ]
    
    train_model_HGAT(sample_edges, num_epochs=50)
    
    print("\n✅ HGAT test completed!")

