from flask import Flask, request, jsonify
from train_Node2Vec import train_model_Node2Vec
from predict_Node2Vec import predict_node_label_Node2Vec
from train_GCN import train_model_GCN
from predict_GCN import predict_node_label_GCN
from train_GAT_unsupervised import train_model_GAT_unsupervised  # Unsupervised for clustering
from train_DGI import train_model_DGI  # Deep Graph Infomax - Unsupervised

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

if __name__ == '__main__':
    app.run(debug=True, host="0.0.0.0", port=5000)
