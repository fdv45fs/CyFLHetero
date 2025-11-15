from flask import Flask, request, jsonify
from train_Node2Vec import train_model_Node2Vec
from predict_Node2Vec import predict_node_label_Node2Vec
from train_GCN import train_model_GCN
from predict_GCN import predict_node_label_GCN
from train_GAT_unsupervised import train_model_GAT_unsupervised  # Unsupervised for clustering
from train_DGI import train_model_DGI  # Deep Graph Infomax - Unsupervised
from train_HGAT import train_model_HGAT, get_embeddings as get_embeddings_HGAT  # Heterogeneous GAT

app = Flask(__name__)

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
            num_epochs=5  # Changed from 200 to 5 for quick testing
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
            "total_possible_links": len(source_nodes) * len(target_nodes)
        }), 200
        
    except Exception as e:
        import traceback
        print(f"[HGAT All Links] ERROR: {str(e)}")
        print(traceback.format_exc())
        return jsonify({
            "status": "error",
            "message": f"Error during HGAT all links prediction: {str(e)}"
        }), 500


if __name__ == '__main__':
    app.run(debug=True, host="0.0.0.0", port=5000)
