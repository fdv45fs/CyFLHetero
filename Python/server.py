from flask import Flask, request, jsonify
from train_Node2Vec import train_model_Node2Vec
from predict_Node2Vec import predict_node_label_Node2Vec
from train_GCN import train_model_GCN
from predict_GCN import predict_node_label_GCN
from train_GAT_unsupervised import train_model_GAT_unsupervised  # Unsupervised for clustering
from train_DGI import train_model_DGI  # Deep Graph Infomax - Unsupervised
from train_HGAT import train_model_HGAT, get_embeddings as get_embeddings_HGAT  # Heterogeneous GAT
from train_GTN import train_model_GTN, get_embeddings_GTN  # Graph Transformer Network

# MetaPath2Vec imports (merged from serverDetero.py)
import torch
from torch_geometric.data import HeteroData
from torch_geometric.nn import MetaPath2Vec
from sklearn.cluster import KMeans
from typing import List, Tuple, Dict
import logging

app = Flask(__name__)

# Configure logging
logging.basicConfig(level=logging.DEBUG,
                    format='%(asctime)s - %(levelname)s - %(message)s')

# Global variables for MetaPath2Vec
metapath2vec_model = None
metapath2vec_data = None
metapath2vec_node_map = None
device_metapath2vec = torch.device('cuda' if torch.cuda.is_available() else 'cpu')

# ============================================================
# MetaPath2Vec Helper Functions (merged from serverDetero.py)
# ============================================================

def create_hetero_data_metapath2vec(edge_index: List[Dict]) -> HeteroData:
    """Create HeteroData for MetaPath2Vec from edge list"""
    global metapath2vec_node_map
    data = HeteroData()
    metapath2vec_node_map = {'drug': {}, 'gene': {}}
    edge_index_dict = {
        ('drug', 'to', 'gene'): [[], []],
        ('gene', 'to', 'drug'): [[], []]
    }

    for edge in edge_index:
        source_name, target_name = edge['source'], edge['target']

        # Detect node type based on "DB" prefix
        source_type = 'drug' if source_name.startswith('DB') else 'gene'
        target_type = 'gene' if source_type == 'drug' else 'drug'

        # Validate node types
        if source_type == 'drug' and not source_name.startswith('DB'):
            logging.warning(f"Node {source_name} expected to be drug but isn't.")
            continue
        if target_type == 'gene' and target_name.startswith('DB'):
            logging.warning(f"Node {target_name} expected to be gene but is drug.")
            continue

        # Add nodes to map
        if source_name not in metapath2vec_node_map[source_type]:
            metapath2vec_node_map[source_type][source_name] = len(metapath2vec_node_map[source_type])
        if target_name not in metapath2vec_node_map[target_type]:
            metapath2vec_node_map[target_type][target_name] = len(metapath2vec_node_map[target_type])

        # Get node indices
        source_index = metapath2vec_node_map[source_type][source_name]
        target_index = metapath2vec_node_map[target_type][target_name]

        # Add edges (bidirectional)
        edge_tuple_forward = (source_type, 'to', target_type)
        edge_tuple_backward = (target_type, 'to', source_type)

        edge_index_dict[edge_tuple_forward][0].append(source_index)
        edge_index_dict[edge_tuple_forward][1].append(target_index)
        edge_index_dict[edge_tuple_backward][0].append(target_index)
        edge_index_dict[edge_tuple_backward][1].append(source_index)

    for edge_type, ei in edge_index_dict.items():
        data[edge_type].edge_index = torch.tensor(ei, dtype=torch.long)

    # Initialize node features (identity matrix)
    if metapath2vec_node_map['drug']:
        data['drug'].x = torch.eye(len(metapath2vec_node_map['drug']), dtype=torch.float)
    if metapath2vec_node_map['gene']:
        data['gene'].x = torch.eye(len(metapath2vec_node_map['gene']), dtype=torch.float)

    # Remove empty node types
    for node_type in list(data.node_types):
        if node_type not in metapath2vec_node_map or not metapath2vec_node_map[node_type]:
            del data[node_type]

    return data

def train_metapath2vec(data: HeteroData, metapath: List[Tuple[str, str, str]], 
                       embedding_dim: int = 128, walk_length: int = 50, 
                       context_size: int = 7, walks_per_node: int = 5,
                       num_negative_samples: int = 5, epochs: int = 2):
    """Train MetaPath2Vec model"""
    global metapath2vec_model
    metapath2vec_model = MetaPath2Vec(
        data.edge_index_dict, 
        embedding_dim=embedding_dim,
        metapath=metapath, 
        walk_length=walk_length, 
        context_size=context_size,
        walks_per_node=walks_per_node, 
        num_negative_samples=num_negative_samples
    ).to(device_metapath2vec)
    
    loader = metapath2vec_model.loader(batch_size=128, shuffle=True, num_workers=0)
    optimizer = torch.optim.Adam(metapath2vec_model.parameters(), lr=0.01)
    metapath2vec_model.train()
    
    for epoch in range(1, epochs + 1):
        total_loss = 0
        for pos_rw, neg_rw in loader:
            optimizer.zero_grad()
            loss = metapath2vec_model.loss(pos_rw.to(device_metapath2vec), neg_rw.to(device_metapath2vec))
            loss.backward()
            optimizer.step()
            total_loss += loss.item()
        logging.info(f'Epoch: {epoch:02d}, Loss: {total_loss / len(loader):.4f}')
    
    return metapath2vec_model

def get_node_embeddings_metapath2vec(model, data):
    """Get embeddings for all nodes"""
    z_dict = {}
    with torch.no_grad():
        for node_type in data.node_types:
            z_dict[node_type] = model(node_type)
    return z_dict

def perform_clustering_metapath2vec(embeddings, num_clusters=10):
    """Perform clustering on embeddings"""
    node_types = sorted(embeddings.keys())
    all_embeddings = torch.cat([embeddings[node_type] for node_type in node_types], dim=0)
    kmeans = KMeans(n_clusters=num_clusters, random_state=0, n_init='auto')
    cluster_labels = kmeans.fit_predict(all_embeddings.cpu().detach().numpy())
    return cluster_labels, node_types

def get_node_embedding_metapath2vec(model, node_name: str, node_map):
    """Get embedding for a single node"""
    node_type = 'drug' if node_name.startswith('DB') else 'gene'

    if node_type not in node_map or node_name not in node_map[node_type]:
        logging.warning(f"Node '{node_name}' (type: {node_type}) not found in node_map.")
        return None, None

    node_index = node_map[node_type][node_name]
    try:
        with torch.no_grad():
            embedding = model(node_type, torch.tensor([node_index], device=device_metapath2vec)).cpu().numpy()
        return embedding[0], node_type
    except Exception as e:
        logging.error(f"Error getting embedding for node '{node_name}': {e}")
        return None, None

# ============================================================
# MetaPath2Vec Endpoints (merged from serverDetero.py)
# ============================================================

@app.route('/receive_hetero_data', methods=['POST'])
def receive_hetero_data():
    """Train MetaPath2Vec on heterogeneous graph"""
    global metapath2vec_data, metapath2vec_model, metapath2vec_node_map
    try:
        req_data = request.get_json()
        logging.debug(f"[MetaPath2Vec] Received training request")
        edge_index = req_data.get('edge_index', [])
        metapath = req_data.get('metapath', [])

        if not edge_index or not metapath:
            return jsonify({"status": "error", "message": "Missing edge_index or metapath"}), 400

        metapath_tuples = [(metapath[i], metapath[i+1], metapath[i+2]) for i in range(0, len(metapath) - 2, 2)]
        
        metapath2vec_data = create_hetero_data_metapath2vec(edge_index)
        
        if not metapath2vec_data.node_types or not any(metapath2vec_data[node_type].num_nodes > 0 for node_type in metapath2vec_data.node_types):
            logging.error("[MetaPath2Vec] No nodes found in network data.")
            return jsonify({"status": "error", "message": "No nodes found in network data"}), 400

        # Validate metapath
        valid_metapath = True
        current_node_type = metapath_tuples[0][0]
        if current_node_type not in metapath2vec_data.node_types:
            valid_metapath = False
        else:
            for i in range(len(metapath_tuples)):
                source_type, _, target_type = metapath_tuples[i]
                if source_type not in metapath2vec_data.node_types or target_type not in metapath2vec_data.node_types:
                    valid_metapath = False
                    break
                if (source_type, metapath_tuples[i][1], target_type) not in metapath2vec_data.edge_index_dict:
                    valid_metapath = False
                    break
        
        if not valid_metapath:
            logging.error(f"[MetaPath2Vec] Metapath {metapath_tuples} is not valid")
            return jsonify({"status": "error", "message": f"Metapath {metapath_tuples} is not valid"}), 400

        metapath2vec_model = train_metapath2vec(metapath2vec_data, metapath_tuples)
        
        # Get embeddings and map to original names
        type_embeddings = get_node_embeddings_metapath2vec(metapath2vec_model, metapath2vec_data)
        embeddings_by_original_name = {}
        
        for node_type in type_embeddings:
            index_to_name_map = {idx: name for name, idx in metapath2vec_node_map[node_type].items()}
            
            for i, emb in enumerate(type_embeddings[node_type]):
                original_name = index_to_name_map.get(i)
                if original_name:
                    embeddings_by_original_name[original_name] = emb.cpu().tolist()

        logging.info(f"[MetaPath2Vec] Successfully trained model, got {len(embeddings_by_original_name)} embeddings")
        return jsonify({"status": "success", "embeddings": embeddings_by_original_name}), 200

    except Exception as e:
        logging.exception("[MetaPath2Vec] Error during training:")
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/cluster_nodes_metapath2vec', methods=['POST'])
def cluster_nodes_metapath2vec():
    """Cluster nodes using MetaPath2Vec embeddings (for routing from ClusterNodesTask)"""
    global metapath2vec_model, metapath2vec_data, metapath2vec_node_map
    if metapath2vec_model is None or metapath2vec_data is None or metapath2vec_node_map is None:
        return jsonify({"status": "error", "message": "MetaPath2Vec model not trained"}), 400

    try:
        req_data = request.get_json()
        num_clusters = req_data.get('num_clusters', 10)

        embeddings = get_node_embeddings_metapath2vec(metapath2vec_model, metapath2vec_data)
        cluster_labels, node_types = perform_clustering_metapath2vec(embeddings, num_clusters)

        # Map node names to cluster IDs
        node_to_cluster = {}
        label_index = 0
        for node_type in node_types:
            if node_type in metapath2vec_node_map:
                for node_name in sorted(metapath2vec_node_map[node_type].keys(), key=lambda n: metapath2vec_node_map[node_type][n]):
                    node_to_cluster[node_name] = int(cluster_labels[label_index])
                    label_index += 1

        logging.info(f"[MetaPath2Vec] Clustered {len(node_to_cluster)} nodes into {num_clusters} clusters")
        return jsonify({"status": "success", "node_to_cluster": node_to_cluster}), 200

    except Exception as e:
        logging.exception("[MetaPath2Vec] Error during clustering:")
        return jsonify({"status": "error", "message": str(e)}), 500

@app.route('/predict_links_metapath2vec', methods=['POST'])
def predict_links_metapath2vec():
    """Predict link score between two nodes using MetaPath2Vec (for routing from PredictLinksTask)"""
    global metapath2vec_model, metapath2vec_data, metapath2vec_node_map
    if metapath2vec_model is None or metapath2vec_data is None:
        return jsonify({"status": "error", "message": "MetaPath2Vec model not trained"}), 400

    try:
        req_data = request.get_json()
        node1_name = req_data.get('node1_name')
        node2_name = req_data.get('node2_name')

        if not node1_name or not node2_name:
            return jsonify({"status": "error", "message": "Missing node names"}), 400

        node1_embedding, node1_type = get_node_embedding_metapath2vec(metapath2vec_model, node1_name, metapath2vec_node_map)
        node2_embedding, node2_type = get_node_embedding_metapath2vec(metapath2vec_model, node2_name, metapath2vec_node_map)

        if node1_embedding is None or node2_embedding is None:
            return jsonify({"status": "error", "message": "Node not found"}), 404

        # Predict link using dot product
        score = torch.dot(torch.tensor(node1_embedding), torch.tensor(node2_embedding)).item()
        
        logging.info(f"[MetaPath2Vec] Link prediction: {node1_name} - {node2_name} = {score:.4f}")
        return jsonify({"status": "success", "score": score}), 200

    except Exception as e:
        logging.exception("[MetaPath2Vec] Error during link prediction:")
        return jsonify({"status":"error", "message":str(e)}), 500

@app.route('/predict_all_links_metapath2vec', methods=['POST'])
def predict_all_links_metapath2vec():
    """Predict all drug-gene links and return top N using MetaPath2Vec (for routing from PredictAllLinksTask)"""
    global metapath2vec_model, metapath2vec_data, metapath2vec_node_map
    if metapath2vec_model is None or metapath2vec_data is None or metapath2vec_node_map is None:
        return jsonify({"status": "error", "message": "MetaPath2Vec model not trained"}), 400

    try:
        req_data = request.get_json()
        top_n = req_data.get('top_n', 10)
        
        logging.info(f"[MetaPath2Vec] Predicting top {top_n} drug-gene links")
        
        embeddings = get_node_embeddings_metapath2vec(metapath2vec_model, metapath2vec_data)
        
        drug_nodes = []
        gene_nodes = []
        
        if 'drug' in metapath2vec_node_map:
            for node_name in metapath2vec_node_map['drug']:
                node_index = metapath2vec_node_map['drug'][node_name]
                embedding = embeddings['drug'][node_index].detach().cpu()
                drug_nodes.append({'name': node_name, 'embedding': embedding})
        
        if 'gene' in metapath2vec_node_map:
            for node_name in metapath2vec_node_map['gene']:
                node_index = metapath2vec_node_map['gene'][node_name]
                embedding = embeddings['gene'][node_index].detach().cpu()
                gene_nodes.append({'name': node_name, 'embedding': embedding})
        
        if len(drug_nodes) == 0 or len(gene_nodes) == 0:
            return jsonify({"status": "error", "message": "Need both drug and gene nodes"}), 400
        
        # Vectorized computation
        drug_embeddings = torch.stack([d['embedding'] for d in drug_nodes])
        gene_embeddings = torch.stack([g['embedding'] for g in gene_nodes])
        
        scores_matrix = torch.matmul(drug_embeddings, gene_embeddings.t())
        scores_flat = scores_matrix.flatten()
        top_scores, top_indices = torch.topk(scores_flat, min(top_n, len(scores_flat)))
        
        top_links = []
        n_genes = len(gene_nodes)
        for score, idx in zip(top_scores.tolist(), top_indices.tolist()):
            drug_idx = idx // n_genes
            gene_idx = idx % n_genes
            top_links.append({
                'node1': drug_nodes[drug_idx]['name'],
                'node2': gene_nodes[gene_idx]['name'],
                'score': score
            })
        
        logging.info(f"[MetaPath2Vec] Predicted top {len(top_links)} links")
        return jsonify({"status": "success", "top_links": top_links, "total_links": len(drug_nodes) * len(gene_nodes)}), 200

    except Exception as e:
        logging.exception("[MetaPath2Vec] Error during all link prediction:")
        return jsonify({"status": "error", "message": str(e)}), 500

# ============================================================
# Homogeneous Graph Models (Node2Vec, GCN, GAT, DGI)
# ============================================================

@app.route('/receive_edge_indices', methods=['POST'])
def receive_edge_indices():
    try:
        data = request.get_json()
        edges = data.get('edge_index', [])
        if not edges:
            return jsonify({"status": "error", "message": "No edges provided"}), 400

        train_model_Node2Vec(edges)
        return jsonify({"status": "success", "message": "Edges received and Node2Vec model trained"}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 400

@app.route('/predict_node_label_Node2Vec', methods=['POST'])
def predict_node_label_Node2Vec_endpoint():
    try:
        data = request.get_json()
        node_name = data.get('node_name')
        if not node_name:
            return jsonify({"status": "error", "message": "Node not provided"}), 400

        result = predict_node_label_Node2Vec(node_name)
        return jsonify({"status": "success", "message": result}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 400


@app.route('/receive_edge_indices_and_features', methods=['POST'])
def receive_edge_indices_and_features():
    try:
        # Parse the JSON data from the request
        data = request.get_json()

        # Extract edge indices
        edges = data.get('edge_index', [])
        if not edges:
            return jsonify({"status": "error", "message": "No edges provided"}), 400

        # print("Received Edges:")
        # for edge in edges:
        #     print(f"{edge['source']} -> {edge['target']}")

        # Extract node features
        nodes = data.get('node_features', [])
        if not nodes:
            return jsonify({"status": "error", "message": "No node features provided"}), 400

        # print("Received Node Features:")

        # Initialize lists to store features, labels, and splits
        features = []
        labels = []
        splits = []

        for node in nodes:
            node_name = node.get("name")
            node_feats = node.get("features", {})

            # Extract 'label' and 'split' from features
            label = node_feats.get('Label')
            split = node_feats.get('Split')

            # Add to the respective lists
            labels.append(label)
            splits.append(split)

            # Remove 'label' and 'split' from features
            node_feats.pop('Label')
            node_feats.pop('Split')

            features.append(node_feats)
            # print(f"Node: {node_name}, Features: {node_feats}, Label: {label}, Split: {split}")

        # Call the train_model function
        train_model_GCN(edges, features, labels, splits)

        # Return success response
        return jsonify({"status": "success", "message": "Edges and node features received, model trained"}), 200

    except Exception as e:
        # Handle errors
        print(f"Error processing request: {str(e)}")
        return jsonify({"status": "error", "message": str(e)}), 400


@app.route('/predict_node_label_GCN', methods=['POST'])
def predict_node_label_GCN_endpoint():
    try:
        data = request.get_json()
        node_name = data.get('node_name')
        if not node_name:
            return jsonify({"status": "error", "message": "Node not provided"}), 400

        result = predict_node_label_GCN(node_name)
        return jsonify({"status": "success", "message": result}), 200
    except Exception as e:
        return jsonify({"status": "error", "message": str(e)}), 400

@app.route('/cluster_nodes', methods=['POST'])
def cluster_nodes_node2vec():
    """Cluster all nodes using Node2Vec embeddings"""
    import pickle
    from sklearn.cluster import KMeans
    
    try:
        # Get number of clusters from request (default 10)
        data = request.get_json()
        num_clusters = data.get('num_clusters', 10)
        
        # Load embeddings and node mapping from Node2Vec training
        try:
            with open("Node2Vec_embeddings.pkl", "rb") as f:
                saved_data = pickle.load(f)
                embeddings = saved_data["embeddings"]
                node_mapping = saved_data["node_mapping"]
        except FileNotFoundError:
            return jsonify({
                "status": "error", 
                "message": "Model not trained. Please train Node2Vec first."
            }), 400
        
        # Perform K-Means clustering on all embeddings
        kmeans = KMeans(n_clusters=num_clusters, random_state=0)
        kmeans.fit(embeddings)
        cluster_labels = kmeans.labels_
        
        # Create mapping: node_name -> cluster_id
        node_to_cluster = {}
        for node_name, node_idx in node_mapping.items():
            node_to_cluster[node_name] = int(cluster_labels[node_idx])
        
        return jsonify({
            "status": "success",
            "node_to_cluster": node_to_cluster
        }), 200
        
    except Exception as e:
        return jsonify({
            "status": "error",
            "message": f"Error during clustering: {str(e)}"
        }), 500

@app.route('/receive_edge_indices_and_features_GAT', methods=['POST'])
def receive_edge_indices_and_features_GAT():
    """Train GAT model for UNSUPERVISED node clustering (no labels needed!)"""
    try:
        # Parse the JSON data from the request
        data = request.get_json()
        
        print(f"\n[GAT Unsupervised] Received training request")

        # Extract edge indices
        edges = data.get('edge_index', [])
        if not edges:
            print("[GAT Unsupervised] ERROR: No edges provided")
            return jsonify({"status": "error", "message": "No edges provided"}), 400
        print(f"[GAT Unsupervised] Number of edges: {len(edges)}")

        # Extract node features
        nodes = data.get('node_features', [])
        if not nodes:
            print("[GAT Unsupervised] ERROR: No node features provided")
            return jsonify({"status": "error", "message": "No node features provided"}), 400
        print(f"[GAT Unsupervised] Number of nodes: {len(nodes)}")

        # For UNSUPERVISED learning, we only need 'Features' attribute
        # No need for 'Label' or 'Split'!
        features = []
        missing_features = []

        for node in nodes:
            node_name = node.get("name")
            node_feats = node.get("features", {})

            # Check if 'Features' exists
            if 'Features' not in node_feats:
                missing_features.append(node_name)
            
            features.append(node_feats)
        
        if missing_features:
            error_msg = f"Missing 'Features' attribute for {len(missing_features)} nodes. First 5: {missing_features[:5]}"
            print(f"[GAT Unsupervised] ERROR: {error_msg}")
            return jsonify({"status": "error", "message": error_msg}), 400

        print(f"[GAT Unsupervised] Starting UNSUPERVISED training for clustering...")
        
        # Call UNSUPERVISED training (no labels/splits needed!)
        train_model_GAT_unsupervised(edges, features)

        print(f"[GAT Unsupervised] Training completed successfully!")
        
        # Return success response
        return jsonify({
            "status": "success", 
            "message": "GAT model trained for clustering (unsupervised)"
        }), 200

    except Exception as e:
        # Handle errors
        import traceback
        error_details = traceback.format_exc()
        print(f"[GAT Unsupervised] ERROR: {str(e)}")
        print(error_details)
        return jsonify({"status": "error", "message": str(e)}), 400

@app.route('/cluster_nodes_GAT', methods=['POST'])
def cluster_nodes_GAT():
    """Cluster all nodes using GAT embeddings"""
    import pickle
    from sklearn.cluster import KMeans
    
    try:
        # Get number of clusters from request (default 10)
        data = request.get_json()
        num_clusters = data.get('num_clusters', 10)
        
        # Load embeddings and node mapping from GAT training
        try:
            with open("GAT_embeddings.pkl", "rb") as f:
                saved_data = pickle.load(f)
                embeddings = saved_data["embeddings"]
                node_mapping = saved_data["node_mapping"]
        except FileNotFoundError:
            return jsonify({
                "status": "error", 
                "message": "Model not trained. Please train GAT first."
            }), 400
        
        # Perform K-Means clustering on all embeddings
        kmeans = KMeans(n_clusters=num_clusters, random_state=0)
        kmeans.fit(embeddings)
        cluster_labels = kmeans.labels_
        
        # Create mapping: node_name -> cluster_id
        node_to_cluster = {}
        for node_name, node_idx in node_mapping.items():
            node_to_cluster[node_name] = int(cluster_labels[node_idx])
        
        return jsonify({
            "status": "success",
            "node_to_cluster": node_to_cluster
        }), 200
        
    except Exception as e:
        return jsonify({
            "status": "error",
            "message": f"Error during GAT clustering: {str(e)}"
        }), 500

@app.route('/receive_edge_indices_DGI', methods=['POST'])
def receive_edge_indices_DGI():
    """Train DGI model for UNSUPERVISED clustering (NO features needed!)"""
    try:
        data = request.get_json()
        
        print(f"\n[DGI] Received training request")
        
        # Extract edge indices
        edges = data.get('edge_index', [])
        if not edges:
            print("[DGI] ERROR: No edges provided")
            return jsonify({"status": "error", "message": "No edges provided"}), 400
        
        print(f"[DGI] Number of edges: {len(edges)}")
        print(f"[DGI] DGI does NOT need node features - will auto-generate from graph structure!")
        
        # Train DGI (no features needed!)
        train_model_DGI(edges, num_epochs=300, hidden_dim=512)
        
        print(f"[DGI] Training completed successfully!")
        
        return jsonify({
            "status": "success",
            "message": "DGI model trained successfully (unsupervised)"
        }), 200
        
    except Exception as e:
        import traceback
        error_details = traceback.format_exc()
        print(f"[DGI] ERROR: {str(e)}")
        print(error_details)
        return jsonify({"status": "error", "message": str(e)}), 400

@app.route('/cluster_nodes_DGI', methods=['POST'])
def cluster_nodes_DGI():
    """Cluster all nodes using DGI embeddings"""
    import pickle
    from sklearn.cluster import KMeans
    
    try:
        # Get number of clusters from request (default 10)
        data = request.get_json()
        num_clusters = data.get('num_clusters', 10)
        
        print(f"[DGI Clustering] Starting clustering with {num_clusters} clusters")
        
        # Load embeddings and node mapping from DGI training
        try:
            with open("DGI_embeddings.pkl", "rb") as f:
                saved_data = pickle.load(f)
                embeddings = saved_data["embeddings"]
                node_mapping = saved_data["node_mapping"]
        except FileNotFoundError:
            return jsonify({
                "status": "error",
                "message": "Model not trained. Please train DGI first."
            }), 400
        
        print(f"[DGI Clustering] Loaded embeddings: {embeddings.shape}")
        
        # Perform K-Means clustering on all embeddings
        kmeans = KMeans(n_clusters=num_clusters, random_state=0)
        kmeans.fit(embeddings)
        cluster_labels = kmeans.labels_
        
        # Create mapping: node_name -> cluster_id
        node_to_cluster = {}
        for node_name, node_idx in node_mapping.items():
            node_to_cluster[node_name] = int(cluster_labels[node_idx])
        
        print(f"[DGI Clustering] Successfully clustered {len(node_to_cluster)} nodes")
        
        return jsonify({
            "status": "success",
            "node_to_cluster": node_to_cluster
        }), 200
        
    except Exception as e:
        import traceback
        print(f"[DGI Clustering] ERROR: {str(e)}")
        print(traceback.format_exc())
        return jsonify({
            "status": "error",
            "message": f"Error during DGI clustering: {str(e)}"
        }), 500


# ==========================================
# HGAT (Heterogeneous GAT) Endpoints
# ==========================================

@app.route('/receive_hetero_data_HGAT', methods=['POST'])
def receive_hetero_data_HGAT():
    """Train HGAT model for UNSUPERVISED learning on heterogeneous graphs"""
    try:
        data = request.get_json()
        
        print(f"\n[HGAT] Received training request")
        
        # Extract edges with node types
        edges = data.get('edges', [])
        if not edges:
            print("[HGAT] ERROR: No edges provided")
            return jsonify({"status": "error", "message": "No edges provided"}), 400
        
        print(f"[HGAT] Number of edges: {len(edges)}")
        
        # Extract node types (e.g., ['drug', 'gene'])
        node_types = data.get('node_types', ['drug', 'gene'])
        print(f"[HGAT] Node types: {node_types}")
        
        # Check edge format
        if edges and 'source_type' not in edges[0]:
            # If no type info, add default types
            for edge in edges:
                edge['source_type'] = node_types[0]
                edge['target_type'] = node_types[1]
        
        print(f"[HGAT] Starting UNSUPERVISED HGAT training...")
        
        # Train HGAT (unsupervised) - Quick testing with 5 epochs
        train_model_HGAT(
            edges, 
            hidden_channels=128, 
            out_channels=64, 
            num_heads=4, 
            num_layers=2,
            num_epochs=200
        )
        
        print(f"[HGAT] Training completed successfully!")
        
        return jsonify({
            "status": "success",
            "message": "HGAT model trained successfully (unsupervised)"
        }), 200
        
    except Exception as e:
        import traceback
        error_details = traceback.format_exc()
        print(f"[HGAT] ERROR: {str(e)}")
        print(error_details)
        return jsonify({"status": "error", "message": str(e)}), 400


@app.route('/cluster_nodes_HGAT', methods=['POST'])
def cluster_nodes_HGAT():
    """Cluster nodes using HGAT embeddings"""
    import pickle
    from sklearn.cluster import KMeans
    
    try:
        data = request.get_json()
        num_clusters = data.get('num_clusters', 10)
        
        print(f"[HGAT Clustering] Starting clustering with {num_clusters} clusters")
        
        # Load embeddings from trained HGAT
        try:
            with open("HGAT_embeddings.pkl", "rb") as f:
                saved_data = pickle.load(f)
                embeddings_dict = saved_data["embeddings"]
                node_mapping = saved_data["node_mapping"]
        except FileNotFoundError:
            return jsonify({
                "status": "error",
                "message": "Model not trained. Please train HGAT first."
            }), 400
        
        print(f"[HGAT Clustering] Loaded embeddings for node types: {list(embeddings_dict.keys())}")
        
        # Cluster each node type separately
        node_to_cluster = {}
        
        for node_type, embeddings in embeddings_dict.items():
            print(f"[HGAT Clustering] Clustering '{node_type}' nodes: {embeddings.shape}")
            
            # Perform K-Means clustering
            kmeans = KMeans(n_clusters=min(num_clusters, embeddings.shape[0]), random_state=0)
            kmeans.fit(embeddings)
            cluster_labels = kmeans.labels_
            
            # Create mapping: node_name -> cluster_id
            for node_name, node_idx in node_mapping[node_type].items():
                node_to_cluster[node_name] = int(cluster_labels[node_idx])
        
        print(f"[HGAT Clustering] Successfully clustered {len(node_to_cluster)} nodes")
        
        return jsonify({
            "status": "success",
            "node_to_cluster": node_to_cluster
        }), 200
        
    except Exception as e:
        import traceback
        print(f"[HGAT Clustering] ERROR: {str(e)}")
        print(traceback.format_exc())
        return jsonify({
            "status": "error",
            "message": f"Error during HGAT clustering: {str(e)}"
        }), 500


@app.route('/predict_links_HGAT', methods=['POST'])
def predict_links_HGAT():
    """Predict link scores for heterogeneous graphs using HGAT embeddings"""
    import pickle
    import numpy as np
    
    try:
        data = request.get_json()
        
        # Get nodes to predict link between
        node1 = data.get('node1')
        node2 = data.get('node2')
        
        if not node1 or not node2:
            return jsonify({
                "status": "error",
                "message": "Both node1 and node2 must be provided"
            }), 400
        
        print(f"[HGAT Link Prediction] Predicting link between '{node1}' and '{node2}'")
        
        # Load embeddings
        try:
            with open("HGAT_embeddings.pkl", "rb") as f:
                saved_data = pickle.load(f)
                embeddings_dict = saved_data["embeddings"]
                node_mapping = saved_data["node_mapping"]
        except FileNotFoundError:
            return jsonify({
                "status": "error",
                "message": "Model not trained. Please train HGAT first."
            }), 400
        
        # Find which type each node belongs to
        node1_type = None
        node1_idx = None
        node2_type = None
        node2_idx = None
        
        for node_type, mapping in node_mapping.items():
            if node1 in mapping:
                node1_type = node_type
                node1_idx = mapping[node1]
            if node2 in mapping:
                node2_type = node_type
                node2_idx = mapping[node2]
        
        if node1_type is None or node2_type is None:
            return jsonify({
                "status": "error",
                "message": f"Node(s) not found in trained model"
            }), 400
        
        # Get embeddings
        node1_emb = embeddings_dict[node1_type][node1_idx]
        node2_emb = embeddings_dict[node2_type][node2_idx]
        
        # Compute similarity score (dot product)
        score = float(np.dot(node1_emb, node2_emb))
        
        print(f"[HGAT Link Prediction] Score: {score:.6f}")
        print(f"  - {node1} (type={node1_type})")
        print(f"  - {node2} (type={node2_type})")
        
        return jsonify({
            "status": "success",
            "node1": node1,
            "node2": node2,
            "node1_type": node1_type,
            "node2_type": node2_type,
            "score": score
        }), 200
        
    except Exception as e:
        import traceback
        print(f"[HGAT Link Prediction] ERROR: {str(e)}")
        print(traceback.format_exc())
        return jsonify({
            "status": "error",
            "message": f"Error during HGAT link prediction: {str(e)}"
        }), 500


@app.route('/predict_all_links_HGAT', methods=['POST'])
def predict_all_links_HGAT():
    """Predict all possible links and return top N for heterogeneous graphs"""
    import pickle
    import numpy as np
    
    try:
        data = request.get_json()
        top_n = data.get('top_n', 10)
        source_type = data.get('source_type', 'drug')
        target_type = data.get('target_type', 'gene')
        
        print(f"[HGAT All Links] Predicting top {top_n} {source_type}-{target_type} links")
        
        # Load embeddings
        try:
            with open("HGAT_embeddings.pkl", "rb") as f:
                saved_data = pickle.load(f)
                embeddings_dict = saved_data["embeddings"]
                node_mapping = saved_data["node_mapping"]
        except FileNotFoundError:
            return jsonify({
                "status": "error",
                "message": "Model not trained. Please train HGAT first."
            }), 400
        
        # Get embeddings for source and target types
        if source_type not in embeddings_dict or target_type not in embeddings_dict:
            return jsonify({
                "status": "error",
                "message": f"Node types '{source_type}' or '{target_type}' not found"
            }), 400
        
        source_emb = embeddings_dict[source_type]
        target_emb = embeddings_dict[target_type]
        source_nodes = list(node_mapping[source_type].keys())
        target_nodes = list(node_mapping[target_type].keys())
        
        print(f"[HGAT All Links] {len(source_nodes)} {source_type} nodes x {len(target_nodes)} {target_type} nodes")
        
        # Compute all pairwise scores (vectorized)
        scores_matrix = np.dot(source_emb, target_emb.T)
        
        # Get top N links
        flat_indices = np.argsort(scores_matrix.flatten())[::-1][:top_n]
        top_links = []
        
        for idx in flat_indices:
            source_idx = idx // len(target_nodes)
            target_idx = idx % len(target_nodes)
            score = scores_matrix[source_idx, target_idx]
            
            top_links.append({
                'node1': source_nodes[source_idx],
                'node2': target_nodes[target_idx],
                'node1_type': source_type,
                'node2_type': target_type,
                'score': float(score)
            })
        
        print(f"[HGAT All Links] Successfully predicted top {len(top_links)} links")
        
        return jsonify({
            "status": "success",
            "top_links": top_links,
            "total_links": len(source_nodes) * len(target_nodes)
        }), 200
        
    except Exception as e:
        import traceback
        print(f"[HGAT All Links] ERROR: {str(e)}")
        print(traceback.format_exc())
        return jsonify({
            "status": "error",
            "message": f"Error during HGAT all links prediction: {str(e)}"
        }), 500


# ============================================================
# GTN (Graph Transformer Network) ENDPOINTS
# ============================================================

@app.route('/receive_hetero_data_GTN', methods=['POST'])
def receive_hetero_data_GTN():
    """Train GTN model on heterogeneous graph data"""
    import pickle
    
    try:
        data = request.get_json()
        edges = data.get('edges', [])
        node_types = data.get('node_types', {})
        
        if not edges:
            return jsonify({
                "status": "error",
                "message": "No edges provided"
            }), 400
        
        if not node_types:
            return jsonify({
                "status": "error",
                "message": "No node types provided"
            }), 400
        
        print(f"\n[GTN] Received heterogeneous graph data:")
        print(f"  - Edges: {len(edges)}")
        print(f"  - Nodes: {len(node_types)}")
        print(f"  - Node types: {set(node_types.values())}")
        
        # Convert edges to tuples
        edges = [(e[0], e[1]) for e in edges]
        
        # Train GTN model
        print("[GTN] Starting GTN training...")
        embeddings, node_map = train_model_GTN(
            edges=edges,
            node_types=node_types,
            feature_dim=64,
            hidden_dim=128,
            out_dim=64,
            num_epochs=5,  # Quick training for testing
            lr=0.005
        )
        
        print("[GTN] Training completed successfully!")
        
        return jsonify({
            "status": "success",
            "message": "GTN model trained successfully",
            "num_edges": len(edges),
            "num_nodes": {ntype: len(mapping) for ntype, mapping in node_map.items()},
            "node_types": list(node_map.keys())
        }), 200
        
    except Exception as e:
        import traceback
        print(f"[GTN] ERROR during training: {str(e)}")
        print(traceback.format_exc())
        return jsonify({
            "status": "error",
            "message": f"Error during GTN training: {str(e)}"
        }), 500


@app.route('/cluster_nodes_GTN', methods=['POST'])
def cluster_nodes_GTN():
    """Perform K-means clustering on GTN embeddings"""
    import pickle
    from sklearn.cluster import KMeans
    
    try:
        data = request.get_json()
        num_clusters = data.get('num_clusters', 3)
        
        print(f"\n[GTN Clustering] Clustering with k={num_clusters}")
        
        # Load embeddings
        try:
            with open("GTN_embeddings.pkl", "rb") as f:
                saved_data = pickle.load(f)
                embeddings_dict = saved_data["embeddings"]
                node_mapping = saved_data["node_mapping"]
        except FileNotFoundError:
            return jsonify({
                "status": "error",
                "message": "Model not trained. Please train GTN first."
            }), 400
        
        # Combine all embeddings for clustering
        all_embeddings = []
        node_names = []
        node_types_list = []
        
        for node_type, embeddings in embeddings_dict.items():
            all_embeddings.append(embeddings)
            # Get node names in order
            mapping = node_mapping[node_type]
            sorted_nodes = sorted(mapping.items(), key=lambda x: x[1])
            node_names.extend([name for name, _ in sorted_nodes])
            node_types_list.extend([node_type] * len(mapping))
        
        import numpy as np
        all_embeddings = np.vstack(all_embeddings)
        
        print(f"[GTN Clustering] Total nodes: {len(node_names)}")
        print(f"[GTN Clustering] Embedding shape: {all_embeddings.shape}")
        
        # Perform K-means clustering
        kmeans = KMeans(n_clusters=num_clusters, random_state=42, n_init=10)
        cluster_labels = kmeans.fit_predict(all_embeddings)
        
        # Create result dictionary (use node_to_cluster for consistency with other models)
        node_to_cluster = {}
        for node_name, cluster_id in zip(node_names, cluster_labels):
            node_to_cluster[node_name] = int(cluster_id)
        
        # Count clusters
        cluster_counts = {}
        for cluster_id in range(num_clusters):
            cluster_counts[f"cluster_{cluster_id}"] = int((cluster_labels == cluster_id).sum())
        
        print(f"[GTN Clustering] Cluster distribution: {cluster_counts}")
        print(f"[GTN Clustering] Successfully clustered {len(node_names)} nodes")
        
        return jsonify({
            "status": "success",
            "node_to_cluster": node_to_cluster
        }), 200
        
    except Exception as e:
        import traceback
        print(f"[GTN Clustering] ERROR: {str(e)}")
        print(traceback.format_exc())
        return jsonify({
            "status": "error",
            "message": f"Error during GTN clustering: {str(e)}"
        }), 500


@app.route('/predict_links_GTN', methods=['POST'])
def predict_links_GTN():
    """Predict link scores for heterogeneous graphs using GTN embeddings"""
    import pickle
    import numpy as np
    
    try:
        data = request.get_json()
        
        # Get nodes to predict link between
        node1 = data.get('node1')
        node2 = data.get('node2')
        
        if not node1 or not node2:
            return jsonify({
                "status": "error",
                "message": "Both node1 and node2 must be provided"
            }), 400
        
        print(f"[GTN Link Prediction] Predicting link between '{node1}' and '{node2}'")
        
        # Load embeddings
        try:
            with open("GTN_embeddings.pkl", "rb") as f:
                saved_data = pickle.load(f)
                embeddings_dict = saved_data["embeddings"]
                node_mapping = saved_data["node_mapping"]
        except FileNotFoundError:
            return jsonify({
                "status": "error",
                "message": "Model not trained. Please train GTN first."
            }), 400
        
        # Find which type each node belongs to
        node1_type = None
        node1_idx = None
        node2_type = None
        node2_idx = None
        
        for node_type, mapping in node_mapping.items():
            if node1 in mapping:
                node1_type = node_type
                node1_idx = mapping[node1]
            if node2 in mapping:
                node2_type = node_type
                node2_idx = mapping[node2]
        
        if node1_type is None or node2_type is None:
            return jsonify({
                "status": "error",
                "message": f"Node(s) not found in trained model"
            }), 400
        
        # Get embeddings
        node1_emb = embeddings_dict[node1_type][node1_idx]
        node2_emb = embeddings_dict[node2_type][node2_idx]
        
        # Compute similarity score (dot product)
        score = float(np.dot(node1_emb, node2_emb))
        
        print(f"[GTN Link Prediction] Score: {score:.6f}")
        print(f"  - {node1} (type={node1_type})")
        print(f"  - {node2} (type={node2_type})")
        
        return jsonify({
            "status": "success",
            "node1": node1,
            "node2": node2,
            "node1_type": node1_type,
            "node2_type": node2_type,
            "score": score
        }), 200
        
    except Exception as e:
        import traceback
        print(f"[GTN Link Prediction] ERROR: {str(e)}")
        print(traceback.format_exc())
        return jsonify({
            "status": "error",
            "message": f"Error during GTN link prediction: {str(e)}"
        }), 500


@app.route('/predict_all_links_GTN', methods=['POST'])
def predict_all_links_GTN():
    """Predict all possible links and return top N for heterogeneous graphs"""
    import pickle
    import numpy as np
    
    try:
        data = request.get_json()
        top_n = data.get('top_n', 10)
        source_type = data.get('source_type', 'drug')
        target_type = data.get('target_type', 'gene')
        
        print(f"[GTN All Links] Predicting top {top_n} {source_type}-{target_type} links")
        
        # Load embeddings
        try:
            with open("GTN_embeddings.pkl", "rb") as f:
                saved_data = pickle.load(f)
                embeddings_dict = saved_data["embeddings"]
                node_mapping = saved_data["node_mapping"]
        except FileNotFoundError:
            return jsonify({
                "status": "error",
                "message": "Model not trained. Please train GTN first."
            }), 400
        
        # Get embeddings for source and target types
        if source_type not in embeddings_dict or target_type not in embeddings_dict:
            return jsonify({
                "status": "error",
                "message": f"Node types '{source_type}' or '{target_type}' not found"
            }), 400
        
        source_emb = embeddings_dict[source_type]
        target_emb = embeddings_dict[target_type]
        source_nodes = list(node_mapping[source_type].keys())
        target_nodes = list(node_mapping[target_type].keys())
        
        print(f"[GTN All Links] {len(source_nodes)} {source_type} nodes x {len(target_nodes)} {target_type} nodes")
        
        # Compute all pairwise scores (vectorized)
        scores_matrix = np.dot(source_emb, target_emb.T)
        
        # Get top N links
        flat_indices = np.argsort(scores_matrix.flatten())[::-1][:top_n]
        top_links = []
        
        for idx in flat_indices:
            source_idx = idx // len(target_nodes)
            target_idx = idx % len(target_nodes)
            score = scores_matrix[source_idx, target_idx]
            
            top_links.append({
                'node1': source_nodes[source_idx],
                'node2': target_nodes[target_idx],
                'node1_type': source_type,
                'node2_type': target_type,
                'score': float(score)
            })
        
        print(f"[GTN All Links] Successfully predicted top {len(top_links)} links")
        
        return jsonify({
            "status": "success",
            "top_links": top_links,
            "total_links": len(source_nodes) * len(target_nodes)
        }), 200
        
    except Exception as e:
        import traceback
        print(f"[GTN All Links] ERROR: {str(e)}")
        print(traceback.format_exc())
        return jsonify({
            "status": "error",
            "message": f"Error during GTN all links prediction: {str(e)}"
        }), 500


if __name__ == '__main__':
    app.run(debug=True, host="0.0.0.0", port=5000)
