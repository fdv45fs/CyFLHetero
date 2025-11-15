import torch
import torch.nn as nn
import torch.nn.functional as F
from torch_geometric.nn import GCNConv
from torch_geometric.data import Data
import pickle
import numpy as np

# DGI Encoder (GCN-based)
class Encoder(nn.Module):
    def __init__(self, input_dim, hidden_dim):
        super(Encoder, self).__init__()
        self.conv1 = GCNConv(input_dim, hidden_dim)
        self.conv2 = GCNConv(hidden_dim, hidden_dim)
        
    def forward(self, x, edge_index):
        x = F.relu(self.conv1(x, edge_index))
        x = self.conv2(x, edge_index)
        return x


# Discriminator
class Discriminator(nn.Module):
    def __init__(self, hidden_dim):
        super(Discriminator, self).__init__()
        self.weight = nn.Parameter(torch.Tensor(hidden_dim, hidden_dim))
        self.reset_parameters()
        
    def reset_parameters(self):
        nn.init.xavier_uniform_(self.weight)
        
    def forward(self, z, summary, sigmoid=True):
        """
        Args:
            z: Node embeddings (num_nodes, hidden_dim)
            summary: Graph-level summary (1, hidden_dim)
            sigmoid: Whether to apply sigmoid
        """
        # z @ W @ summary.T
        value = torch.matmul(z, torch.matmul(self.weight, summary.t()))
        return torch.sigmoid(value) if sigmoid else value


# DGI Model
class DGI(nn.Module):
    def __init__(self, input_dim, hidden_dim):
        super(DGI, self).__init__()
        self.encoder = Encoder(input_dim, hidden_dim)
        self.discriminator = Discriminator(hidden_dim)
        
    def forward(self, x, edge_index):
        # Positive sample (real graph)
        pos_z = self.encoder(x, edge_index)
        
        # Global summary (readout)
        summary = torch.sigmoid(pos_z.mean(dim=0, keepdim=True))
        
        # Negative sample (corrupted graph - shuffle node features)
        perm = torch.randperm(x.size(0))
        neg_x = x[perm]
        neg_z = self.encoder(neg_x, edge_index)
        
        return pos_z, neg_z, summary
    
    def loss(self, pos_z, neg_z, summary):
        """
        Contrastive loss: Discriminate real vs fake
        """
        # Positive scores (should be high)
        pos_scores = self.discriminator(pos_z, summary, sigmoid=False)
        # Negative scores (should be low)
        neg_scores = self.discriminator(neg_z, summary, sigmoid=False)
        
        # Binary cross-entropy loss
        pos_loss = F.binary_cross_entropy_with_logits(
            pos_scores, torch.ones_like(pos_scores)
        )
        neg_loss = F.binary_cross_entropy_with_logits(
            neg_scores, torch.zeros_like(neg_scores)
        )
        
        return pos_loss + neg_loss


def create_node_features(num_nodes, edge_index, feature_type='degree'):
    """
    Create node features automatically from graph structure.
    
    Args:
        num_nodes: Number of nodes
        edge_index: Edge indices (2, num_edges)
        feature_type: 'degree', 'identity', or 'one_hot'
    
    Returns:
        Node features tensor
    """
    if feature_type == 'degree':
        # Degree-based features (normalized)
        degree = torch.zeros(num_nodes)
        for i in range(edge_index.size(1)):
            degree[edge_index[0, i]] += 1
            degree[edge_index[1, i]] += 1
        
        # Normalize
        degree = degree / (degree.max() + 1e-6)
        
        # Add more structural features
        degree_squared = degree ** 2
        features = torch.stack([degree, degree_squared], dim=1)
        
        print(f"[DGI] Created degree-based features: {features.shape}")
        return features
        
    elif feature_type == 'identity':
        # Identity matrix (one-hot encoding)
        features = torch.eye(num_nodes)
        print(f"[DGI] Created identity features: {features.shape}")
        return features
        
    elif feature_type == 'one_hot':
        # Simple one-hot
        features = torch.eye(min(num_nodes, 128))  # Limit to 128 dims
        if num_nodes > 128:
            # Random projection for large graphs
            features = torch.randn(num_nodes, 128)
        print(f"[DGI] Created one-hot features: {features.shape}")
        return features
    
    else:
        raise ValueError(f"Unknown feature type: {feature_type}")


def train_model_DGI(edges, num_epochs=300, hidden_dim=512, learning_rate=0.001, 
                    feature_type='degree'):
    """
    Train DGI model for UNSUPERVISED node embedding learning.
    Perfect for graphs WITHOUT node features!
    
    Args:
        edges: List of edge dictionaries with 'source' and 'target' keys
        num_epochs: Number of training epochs
        hidden_dim: Hidden dimension size for embeddings
        learning_rate: Learning rate
        feature_type: How to create features ('degree', 'identity', 'one_hot')
    """
    print(f"\n{'='*70}")
    print(f"Deep Graph Infomax (DGI) - Unsupervised Training")
    print(f"{'='*70}")
    print(f"Epochs: {num_epochs} | Hidden: {hidden_dim} | LR: {learning_rate}")
    print(f"Feature type: {feature_type} (auto-generated from graph structure)")
    print(f"{'='*70}\n")
    
    # Create node mappings
    nodes = set()
    for edge in edges:
        nodes.add(edge['source'])
        nodes.add(edge['target'])
    node_mapping = {node: idx for idx, node in enumerate(sorted(nodes))}
    num_nodes = len(node_mapping)
    print(f"Total nodes: {num_nodes}")
    
    # Convert edges to index pairs
    edge_index = torch.tensor(
        [[node_mapping[edge['source']], node_mapping[edge['target']]] for edge in edges],
        dtype=torch.long
    ).t()
    print(f"Total edges: {edge_index.size(1)}")
    
    # Make graph undirected (add reverse edges)
    edge_index = torch.cat([edge_index, edge_index.flip(0)], dim=1)
    print(f"Total edges (undirected): {edge_index.size(1)}")
    
    # Create node features automatically from graph structure
    node_features = create_node_features(num_nodes, edge_index, feature_type)
    input_dim = node_features.size(1)
    print(f"Input feature dimension: {input_dim}")
    
    # Create PyTorch Geometric Data object
    graph = Data(x=node_features, edge_index=edge_index)
    
    # Define device
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")
    graph = graph.to(device)
    
    # Model and optimizer
    model = DGI(input_dim=input_dim, hidden_dim=hidden_dim).to(device)
    optimizer = torch.optim.Adam(model.parameters(), lr=learning_rate)
    
    # Count parameters
    total_params = sum(p.numel() for p in model.parameters())
    print(f"Total parameters: {total_params:,}")
    
    # Training loop
    print(f"\n{'='*70}")
    print(f"Training DGI Model")
    print(f"{'='*70}")
    
    model.train()
    best_loss = float('inf')
    
    for epoch in range(num_epochs):
        optimizer.zero_grad()
        
        # Forward pass
        pos_z, neg_z, summary = model(graph.x, graph.edge_index)
        
        # Compute loss
        loss = model.loss(pos_z, neg_z, summary)
        
        # Backward pass
        loss.backward()
        optimizer.step()
        
        # Logging
        if epoch % 20 == 0 or epoch == num_epochs - 1:
            print(f"Epoch {epoch:4d} | Loss: {loss.item():.6f}")
            
        if loss.item() < best_loss:
            best_loss = loss.item()
    
    print(f"{'='*70}")
    print(f"Training Complete! Best Loss: {best_loss:.6f}")
    print(f"{'='*70}\n")
    
    # Get final embeddings
    model.eval()
    with torch.no_grad():
        embeddings, _, _ = model(graph.x, graph.edge_index)
        embeddings = embeddings.cpu().numpy()
    
    # Save model
    model_path = 'DGI_trained_model.pth'
    torch.save({
        'model_state_dict': model.state_dict(),
        'hidden_dim': hidden_dim,
        'input_dim': input_dim,
        'node_mapping': node_mapping
    }, model_path)
    print(f"✅ Model saved at {model_path}")
    
    # Save embeddings for clustering
    embedding_path = 'DGI_embeddings.pkl'
    with open(embedding_path, 'wb') as f:
        pickle.dump({
            "embeddings": embeddings,
            "node_mapping": node_mapping,
            "edge_index": edge_index.cpu().numpy()
        }, f)
    print(f"✅ Embeddings saved at {embedding_path}")
    print(f"   Shape: {embeddings.shape}")
    print(f"   Ready for clustering!\n")
    
    print(f"{'='*70}")
    print(f"✅ DGI training completed successfully!")
    print(f"{'='*70}\n")
    
    return embeddings, node_mapping


if __name__ == "__main__":
    # Test with dummy data
    print("Testing DGI training...")
    
    # Create dummy edges
    dummy_edges = [
        {'source': 'A', 'target': 'B'},
        {'source': 'B', 'target': 'C'},
        {'source': 'C', 'target': 'D'},
        {'source': 'D', 'target': 'A'},
        {'source': 'A', 'target': 'C'},
    ]
    
    train_model_DGI(dummy_edges, num_epochs=50, hidden_dim=32)
    print("✅ Test completed!")

