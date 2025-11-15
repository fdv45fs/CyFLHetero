import pickle
import torch
import torch.nn.functional as F
from torch_geometric.data import Data
from torch_geometric.nn import GATConv

# GAT Model Definition (must match train_GAT.py)
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


# Function to load the trained GAT model
def load_model(model_path, input_dim, hidden_dim=8, output_dim=7, heads=8, dropout=0.6):
    """Load trained GAT model from file"""
    model = GAT(
        input_dim=input_dim, 
        hidden_dim=hidden_dim, 
        output_dim=output_dim,
        heads=heads,
        dropout=dropout
    )
    model.load_state_dict(torch.load(model_path))
    model.eval()
    return model


# Function to predict node label (aligns with Flask route structure)
def predict_node_label_GAT(node_name):
    """
    Predict the class label for a given node using trained GAT model.
    
    Args:
        node_name: Name of the node to predict
        
    Returns:
        Dictionary with status and predicted label or error message
    """
    try:
        # Load embeddings and metadata
        with open("GAT_embeddings.pkl", "rb") as f:
            data = pickle.load(f)
            embeddings = data["embeddings"]
            node_mapping = data["node_mapping"]
            label_mapping = {v: k for k, v in data["label_mapping"].items()}  # Reverse mapping
            edge_index = data["edge_index"]

        # Load features
        with open("features.pkl", "rb") as f:
            features = torch.tensor(pickle.load(f), dtype=torch.float)

        # Check if node exists in the node_mapping
        if node_name not in node_mapping:
            return {
                "status": "error", 
                "message": f"Node '{node_name}' not found in node mapping"
            }

        # Get the corresponding node index
        node_idx = node_mapping[node_name]

        # Get feature dimension for model initialization
        input_dim = features.size(1)
        output_dim = len(label_mapping)

        # Load the trained GAT model
        model = load_model(
            "GAT_trained_model.pth",
            input_dim=input_dim,
            hidden_dim=8,
            output_dim=output_dim,
            heads=8,
            dropout=0.6
        )

        # Convert edge_index to tensor
        edge_index_tensor = torch.tensor(edge_index, dtype=torch.long).contiguous()

        # Create Data object with the full set of node features and the edge_index
        graph_data = Data(x=features, edge_index=edge_index_tensor)

        # Predict the label
        with torch.no_grad():
            out, _ = model(graph_data.x, graph_data.edge_index)  # Pass all node features to the model

        # Get the predicted class label for the specific node
        _, predicted_class = out[node_idx].max(dim=0)  # Get prediction for the specific node
        predicted_label = label_mapping[predicted_class.item()]
        
        return {
            "status": "success", 
            "predicted_label": predicted_label,
            "node_name": node_name
        }

    except FileNotFoundError as e:
        return {
            "status": "error", 
            "message": f"Required file not found: {str(e)}. Please train GAT model first."
        }
    except Exception as e:
        return {
            "status": "error", 
            "message": f"Error during prediction: {str(e)}"
        }

