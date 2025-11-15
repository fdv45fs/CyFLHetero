import torch
import torch.nn.functional as F
from torch_geometric.nn import GATConv
import torch.optim as optim
from torch_geometric.data import Data
import pickle

# Define the GAT model for unsupervised learning
class GAT_Unsupervised(torch.nn.Module):
    def __init__(self, input_dim, hidden_dim, output_dim, heads=8, dropout=0.6):
        super(GAT_Unsupervised, self).__init__()
        # First GAT layer with multi-head attention
        self.conv1 = GATConv(input_dim, hidden_dim, heads=heads, dropout=dropout)
        # Second GAT layer
        self.conv2 = GATConv(hidden_dim * heads, hidden_dim, heads=heads, dropout=dropout)
        # Output layer (single head)
        self.conv3 = GATConv(hidden_dim * heads, output_dim, heads=1, concat=False, dropout=dropout)
        self.dropout = dropout

    def forward(self, x, edge_index):
        # First GAT layer
        x = F.dropout(x, p=self.dropout, training=self.training)
        x = F.elu(self.conv1(x, edge_index))
        # Second GAT layer
        x = F.dropout(x, p=self.dropout, training=self.training)
        x = F.elu(self.conv2(x, edge_index))
        # Output layer (embeddings)
        x = F.dropout(x, p=self.dropout, training=self.training)
        embeddings = self.conv3(x, edge_index)
        return embeddings


def train_model_GAT_unsupervised(edges, node_features, num_epochs=50, hidden_dim=16, 
                                  heads=4, learning_rate=0.01, weight_decay=5e-4, 
                                  dropout=0.3, output_dim=64):
    """
    Train GAT model for UNSUPERVISED node clustering.
    Uses reconstruction loss to learn meaningful embeddings.
    
    Args:
        edges: List of edge dictionaries with 'source' and 'target' keys
        node_features: List of node feature dictionaries
        num_epochs: Number of training epochs (fewer for unsupervised)
        hidden_dim: Hidden dimension size
        heads: Number of attention heads
        learning_rate: Learning rate for optimizer
        weight_decay: L2 regularization coefficient
        dropout: Dropout rate
        output_dim: Output embedding dimension
    """
    print(f"\n{'='*60}")
    print(f"Starting UNSUPERVISED GAT training for node clustering")
    print(f"Epochs: {num_epochs} | Hidden: {hidden_dim} | Heads: {heads} | Output: {output_dim}")
    print(f"{'='*60}\n")
    
    # Create node mappings
    nodes = set()
    for edge in edges:
        nodes.add(edge['source'])
        nodes.add(edge['target'])
    node_mapping = {node: idx for idx, node in enumerate(nodes)}
    print(f"Total nodes: {len(node_mapping)}")

    # Convert edges to index pairs
    edge_index = torch.tensor(
        [[node_mapping[edge['source']], node_mapping[edge['target']]] for edge in edges],
        dtype=torch.long
    ).t()
    print(f"Total edges: {edge_index.size(1)}")

    # Convert node features to tensor
    features_tensor = []
    for node in node_features:
        node_feats = node.get('Features', '')
        if node_feats:  # Only if features exist
            feature_values = list(map(float, node_feats.split()))
            features_tensor.append(feature_values)
        else:
            # If no features, create random features
            print(f"Warning: Node has no 'Features' attribute, using random features")
            features_tensor.append([0.0] * 128)  # Default dimension
    
    node_features_tensor = torch.tensor(features_tensor, dtype=torch.float)
    print(f"Feature dimension: {node_features_tensor.size(1)}")

    # Create PyTorch Geometric Data object (no labels needed!)
    graph = Data(x=node_features_tensor, edge_index=edge_index)
    
    # Define device
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")
    graph = graph.to(device)

    # Model, optimizer
    model = GAT_Unsupervised(
        input_dim=node_features_tensor.size(1), 
        hidden_dim=hidden_dim, 
        output_dim=output_dim,
        heads=heads,
        dropout=dropout
    ).to(device)
    
    optimizer = optim.Adam(model.parameters(), lr=learning_rate, weight_decay=weight_decay)

    # Training function using reconstruction loss
    def train():
        model.train()
        optimizer.zero_grad()
        
        # Forward pass to get embeddings
        embeddings = model(graph.x, graph.edge_index)
        
        # Reconstruction loss: Try to reconstruct node features from embeddings
        # Simple approach: Use MSE between original features and reconstructed features
        reconstruction = torch.mm(embeddings, embeddings.t())  # Similarity matrix
        target = torch.mm(graph.x, graph.x.t())  # Target similarity
        
        # Normalize
        reconstruction = F.normalize(reconstruction, p=2, dim=1)
        target = F.normalize(target, p=2, dim=1)
        
        # MSE loss
        loss = F.mse_loss(reconstruction, target)
        
        # Backward pass
        loss.backward()
        optimizer.step()
        
        return loss.item()

    # Training loop
    print(f"\n{'='*60}")
    print(f"Training GAT Model (Unsupervised)")
    print(f"{'='*60}")
    
    for epoch in range(num_epochs):
        train_loss = train()
        
        if epoch % 10 == 0 or epoch == num_epochs - 1:
            print(f"Epoch {epoch:3d} | Reconstruction Loss: {train_loss:.6f}")

    print(f"{'='*60}")
    print(f"Training Complete!")
    print(f"{'='*60}\n")

    # Save the model
    model_path = 'GAT_trained_model.pth'
    torch.save(model.state_dict(), model_path)
    print(f"✅ Model saved at {model_path}")

    # Get embeddings for clustering
    model.eval()
    with torch.no_grad():
        embeddings = model(graph.x, graph.edge_index)

    # Save embeddings
    embedding_path = 'GAT_embeddings.pkl'
    with open(embedding_path, 'wb') as f:
        pickle.dump({
            "embeddings": embeddings.cpu().numpy(), 
            "node_mapping": node_mapping,
            "edge_index": edge_index.cpu().numpy()
        }, f)
    print(f"✅ Embeddings saved at {embedding_path}")

    # Save features
    with open('features.pkl', 'wb') as f:
        pickle.dump(node_features_tensor.cpu().numpy(), f)
    print(f"✅ Features saved\n")

    print(f"{'='*60}")
    print(f"✅ GAT training completed! Ready for clustering.")
    print(f"{'='*60}\n")

