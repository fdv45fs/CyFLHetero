import torch
import torch.nn.functional as F
from torch_geometric.nn import GATConv
import torch.optim as optim
from torch_geometric.data import Data
import pickle

# Define the GAT model
class GAT(torch.nn.Module):
    def __init__(self, input_dim, hidden_dim, output_dim, heads=8, dropout=0.6):
        super(GAT, self).__init__()
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
        # Output layer
        x = F.dropout(x, p=self.dropout, training=self.training)
        x = self.conv3(x, edge_index)
        return x, F.log_softmax(x, dim=1)


def train_model_GAT(edges, node_features, labels, splits, num_epochs=200, hidden_dim=8, 
                    heads=8, learning_rate=0.005, weight_decay=5e-4, dropout=0.6, output_dim=7):
    """
    Train a Graph Attention Network (GAT) model on homogeneous graph data.
    
    Args:
        edges: List of edge dictionaries with 'source' and 'target' keys
        node_features: List of node feature dictionaries
        labels: List of node labels
        splits: List of split indicators ('Train' or 'Test')
        num_epochs: Number of training epochs
        hidden_dim: Hidden dimension size
        heads: Number of attention heads
        learning_rate: Learning rate for optimizer
        weight_decay: L2 regularization coefficient
        dropout: Dropout rate
        output_dim: Number of output classes
    """
    print(f"Starting GAT training with {num_epochs} epochs...")
    
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
    num_nodes = len(node_mapping)
    features_tensor = []
    for node in node_features:
        node_feats = node.get('Features', '')
        feature_values = list(map(float, node_feats.split()))
        features_tensor.append(feature_values)
    
    node_features_tensor = torch.tensor(features_tensor, dtype=torch.float)
    print(f"Feature dimension: {node_features_tensor.size(1)}")

    # Create label mapping
    unique_labels = list(set(labels))
    label_mapping = {label: idx for idx, label in enumerate(unique_labels)}
    integer_labels = [label_mapping[label] for label in labels]
    labels_tensor = torch.tensor(integer_labels, dtype=torch.long)
    print(f"Number of classes: {len(unique_labels)}")

    # Create PyTorch Geometric Data object
    graph = Data(x=node_features_tensor, edge_index=edge_index, y=labels_tensor)
    
    # Define device
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Using device: {device}")
    graph = graph.to(device)

    # Create boolean masks for train and test sets
    train_mask = torch.tensor([split == "Train" for split in splits], dtype=torch.bool)
    test_mask = torch.tensor([split == "Test" for split in splits], dtype=torch.bool)
    print(f"Train nodes: {train_mask.sum().item()}, Test nodes: {test_mask.sum().item()}")

    # Model, loss function, optimizer
    model = GAT(
        input_dim=node_features_tensor.size(1), 
        hidden_dim=hidden_dim, 
        output_dim=output_dim,
        heads=heads,
        dropout=dropout
    ).to(device)
    
    optimizer = optim.Adam(model.parameters(), lr=learning_rate, weight_decay=weight_decay)
    criterion = torch.nn.CrossEntropyLoss()

    # Training function
    def train():
        model.train()
        optimizer.zero_grad()
        # Forward pass
        _, out = model(graph.x, graph.edge_index)
        loss = criterion(out[train_mask], graph.y[train_mask])
        # Backward pass and optimization
        loss.backward()
        optimizer.step()
        return loss.item()

    # Test function
    def test():
        model.eval()
        with torch.no_grad():
            _, out = model(graph.x, graph.edge_index)
            test_loss = criterion(out[test_mask], graph.y[test_mask])
            # Calculate accuracy
            _, pred = out[test_mask].max(dim=1)
            correct = pred.eq(graph.y[test_mask]).sum().item()
            accuracy = correct / test_mask.sum().item()
        return test_loss.item(), accuracy

    # Training loop
    print("\n" + "="*50)
    print("Training GAT Model")
    print("="*50)
    
    best_test_acc = 0
    for epoch in range(num_epochs):
        train_loss = train()
        if epoch % 10 == 0:
            test_loss, test_acc = test()
            print(f"Epoch {epoch:3d} | Train Loss: {train_loss:.4f} | Test Loss: {test_loss:.4f} | Test Acc: {test_acc:.4f}")
            if test_acc > best_test_acc:
                best_test_acc = test_acc

    # Final evaluation
    test_loss, test_acc = test()
    print("="*50)
    print(f"Training Complete!")
    print(f"Best Test Accuracy: {best_test_acc:.4f}")
    print(f"Final Test Accuracy: {test_acc:.4f}")
    print("="*50 + "\n")

    # Save the model and embeddings after training
    model_path = 'GAT_trained_model.pth'
    embedding_path = 'GAT_embeddings.pkl'

    # Save the model
    torch.save(model.state_dict(), model_path)
    print(f"✅ Model saved at {model_path}")

    # Get embeddings (from the layer before softmax)
    model.eval()
    with torch.no_grad():
        embeddings, _ = model(graph.x, graph.edge_index)

    # Save embeddings as a pickle file
    with open(embedding_path, 'wb') as f:
        pickle.dump({
            "embeddings": embeddings.cpu().numpy(), 
            "node_mapping": node_mapping,
            "edge_index": edge_index.cpu().numpy(),
            "label_mapping": label_mapping
        }, f)
    print(f"✅ Embeddings saved at {embedding_path}")

    # Save features
    with open('features.pkl', 'wb') as f:
        pickle.dump(node_features_tensor.cpu().numpy(), f)
    print(f"✅ Features saved\n")

