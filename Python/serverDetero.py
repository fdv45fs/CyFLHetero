from flask import Flask, request, jsonify
import torch
from torch_geometric.data import HeteroData
from torch_geometric.nn import MetaPath2Vec
from sklearn.cluster import KMeans
from typing import List, Tuple, Dict
import logging

app = Flask(__name__)

logging.basicConfig(level=logging.DEBUG,
                    format='%(asctime)s - %(levelname)s - %(message)s')

model = None
data = None
device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
node_map = None  # Lưu node_map ở đây

def create_hetero_data(edge_index: List[Dict]) -> HeteroData:
    global node_map  # Sử dụng biến global
    data = HeteroData()
    node_map = {'drug': {}, 'gene': {}}  # Khởi tạo node_map
    edge_index_dict = {
        ('drug', 'to', 'gene'): [[], []], # Hoàn lại tên quan hệ
        ('gene', 'to', 'drug'): [[], []]
    }

    for edge in edge_index:
        source_name, target_name = edge['source'], edge['target']

        # Xác định loại node dựa trên tiền tố "DB"
        source_type = 'drug' if source_name.startswith('DB') else 'gene'
        target_type = 'gene' if source_type == 'drug' else 'drug' # Giả định đồ thị là bipartite

        # Đảm bảo tên nút đúng loại
        if source_type == 'drug' and not source_name.startswith('DB'):
             logging.warning(f"Node {source_name} was expected to be a drug (start with 'DB') but isn't.")
             continue
        if target_type == 'gene' and target_name.startswith('DB'):
             logging.warning(f"Node {target_name} was expected to be a gene (not start with 'DB') but does.")
             continue

        # Thêm node vào map nếu chưa có
        if source_name not in node_map[source_type]:
            node_map[source_type][source_name] = len(node_map[source_type])
        if target_name not in node_map[target_type]:
            node_map[target_type][target_name] = len(node_map[target_type])

        # Lấy index của node
        source_index = node_map[source_type][source_name]
        target_index = node_map[target_type][target_name]

        # Thêm cạnh vào edge_index_dict (sử dụng tên 'to')
        edge_tuple_forward = (source_type, 'to', target_type)
        edge_tuple_backward = (target_type, 'to', source_type)

        edge_index_dict[edge_tuple_forward][0].append(source_index)
        edge_index_dict[edge_tuple_forward][1].append(target_index)
        edge_index_dict[edge_tuple_backward][0].append(target_index)
        edge_index_dict[edge_tuple_backward][1].append(source_index)

    for edge_type, ei in edge_index_dict.items():
        data[edge_type].edge_index = torch.tensor(ei, dtype=torch.long)

    # Khởi tạo đặc trưng node (ví dụ: identity matrix) nếu có node
    if node_map['drug']:
       data['drug'].x = torch.eye(len(node_map['drug']), dtype=torch.float)
    if node_map['gene']:
       data['gene'].x = torch.eye(len(node_map['gene']), dtype=torch.float)

    # Xóa các node type không có node nào
    for node_type in list(data.node_types):
        if node_type not in node_map or not node_map[node_type]:
            del data[node_type]

    return data

def train_metapath2vec(data: HeteroData, metapath: List[Tuple[str, str, str]], embedding_dim: int = 128,
                       walk_length: int = 50, context_size: int = 7, walks_per_node: int = 5,
                       num_negative_samples: int = 5, epochs: int = 2):
    global model
    model = MetaPath2Vec(data.edge_index_dict, embedding_dim=embedding_dim,
                         metapath=metapath, walk_length=walk_length, context_size=context_size,
                         walks_per_node=walks_per_node, num_negative_samples=num_negative_samples).to(device)
    loader = model.loader(batch_size=128, shuffle=True, num_workers=0)
    optimizer = torch.optim.Adam(model.parameters(), lr=0.01)
    model.train()
    for epoch in range(1, epochs + 1):
        total_loss = 0
        for pos_rw, neg_rw in loader:
            optimizer.zero_grad()
            loss = model.loss(pos_rw.to(device), neg_rw.to(device))
            loss.backward()
            optimizer.step()
            total_loss += loss.item()
        logging.info(f'Epoch: {epoch:02d}, Loss: {total_loss / len(loader):.4f}')
    return model

def get_node_embeddings(model, data, node_map_param): # Thêm node_map_param
    """Lấy embedding cho tất cả các nút và map về tên node gốc."""
    embeddings_by_original_name = {}
    with torch.no_grad(): # Đảm bảo không tính gradient khi lấy embeddings
        for node_type in data.node_types:
            # Lấy tất cả embeddings cho một node_type
            type_embeddings = model(node_type) 
            # Lấy map từ index -> tên gốc cho node_type hiện tại
            # Cần đảo ngược node_map: {name: index} -> {index: name}
            index_to_name_map = {idx: name for name, idx in node_map_param[node_type].items()}
            
            for i, emb in enumerate(type_embeddings):
                original_name = index_to_name_map.get(i)
                if original_name:
                    embeddings_by_original_name[original_name] = emb.cpu().tolist() # Chuyển tensor sang list và về CPU
                else:
                    logging.warning(f"Could not find original name for node type {node_type} and index {i}")
    return embeddings_by_original_name

def perform_clustering(embeddings, num_clusters=10): # Hàm clustering
    """Thực hiện phân cụm trên embeddings."""
    # embeddings: {node_type: Tensor}
    # Gộp embedding của tất cả node type lại
    all_embeddings = torch.cat([embeddings[node_type] for node_type in embeddings], dim=0)
    kmeans = KMeans(n_clusters=num_clusters, random_state=0, n_init = 'auto')
    cluster_labels = kmeans.fit_predict(all_embeddings.cpu().detach().numpy())  # Chuyển về CPU trước khi fit
    return cluster_labels

@app.route('/receive_hetero_data', methods=['POST'])
def receive_hetero_data():
    global data, model, node_map 
    try:
        req_data = request.get_json()
        logging.debug(f"Received request data: {req_data}")
        edge_index = req_data.get('edge_index', [])
        metapath = req_data.get('metapath', [])

        if not edge_index or not metapath:
            return jsonify({"status": "error", "message": "Missing data"}), 400

        metapath_tuples = [(metapath[i], metapath[i+1], metapath[i+2]) for i in range(0, len(metapath) - 2, 2)]
        
        # create_hetero_data sẽ cập nhật global node_map
        data = create_hetero_data(edge_index) 
        
        if not data.node_types or not any(data[node_type].num_nodes > 0 for node_type in data.node_types):
            logging.error("No nodes found in the created HeteroData object. Cannot train model.")
            return jsonify({"status": "error", "message": "No nodes found in network data to train on."}), 400

        # Kiểm tra xem metapath có hợp lệ với các node_types trong data không
        valid_metapath = True
        current_node_type = metapath_tuples[0][0] # Loại node bắt đầu của metapath
        if current_node_type not in data.node_types:
            valid_metapath = False
        else:
            for i in range(len(metapath_tuples)):
                source_type, _, target_type = metapath_tuples[i]
                if source_type not in data.node_types or target_type not in data.node_types:
                    valid_metapath = False
                    break
                # Kiểm tra xem có edge_type tương ứng không
                if (source_type, metapath_tuples[i][1], target_type) not in data.edge_index_dict:
                    valid_metapath = False
                    break
        
        if not valid_metapath:
            logging.error(f"Metapath {metapath_tuples} is not valid for the current graph structure and node types: {data.node_types} and edge types: {data.edge_types}")
            return jsonify({"status": "error", "message": f"Metapath {metapath_tuples} is not valid for the current graph structure."}), 400

        model = train_metapath2vec(data, metapath_tuples) 
        
        # Lấy embeddings sau khi huấn luyện
        # Hàm get_node_embeddings giờ sẽ sử dụng node_map global đã được cập nhật
        node_embeddings_response = get_node_embeddings(model, data, node_map) 

        logging.info(f"Successfully trained model and got {len(node_embeddings_response)} embeddings.")
        return jsonify({"status": "success", "embeddings": node_embeddings_response}), 200

    except Exception as e:
        logging.exception("Error during request processing in /receive_hetero_data:")
        return jsonify({"status": "error", "message": str(e)}), 500 # Changed to 500 for server errors

@app.route('/cluster_nodes', methods=['POST'])
def cluster_nodes():
    global model, data, node_map  # Sử dụng node_map toàn cục
    if model is None or data is None or node_map is None:
        return jsonify({"status": "error", "message": "Model not trained"}), 400

    try:
        req_data = request.get_json()
        num_clusters = req_data.get('num_clusters', 10)

        embeddings = get_node_embeddings(model, data, node_map)
        cluster_labels = perform_clustering(embeddings, num_clusters)

        # Tạo mapping node_name -> cluster_id (sử dụng node_map)
        node_to_cluster = {}
        label_index = 0
        for node_type in ['drug', 'gene']:
            for node_name in node_map[node_type]:
                node_to_cluster[node_name] = cluster_labels[label_index].item()
                label_index += 1

        return jsonify({"status": "success", "node_to_cluster": node_to_cluster}), 200

    except Exception as e:
        logging.exception("Error during clustering:")
        return jsonify({"status": "error", "message": str(e)}), 500
@app.route('/predict_links', methods=['POST'])
def predict_links():
    global model, data, node_map
    if model is None or data is None:
        return jsonify({"status": "error", "message": "Model not trained"}), 400

    try:
        req_data = request.get_json()
        node1_name = req_data.get('node1_name')
        node2_name = req_data.get('node2_name')

        if not node1_name or not node2_name:
             return jsonify({"status": "error", "message": "Missing node names"}), 400

        node1_embedding, node1_type = get_node_embedding(model, node1_name, node_map)
        node2_embedding, node2_type = get_node_embedding(model, node2_name, node_map)

        if node1_embedding is None or node2_embedding is None:
          return jsonify({"status": "error", "message": "Node not found"}), 404

        # Dự đoán liên kết bằng dot product (có thể thay bằng cách khác)
        score = torch.dot(torch.tensor(node1_embedding), torch.tensor(node2_embedding)).item()
        return jsonify({"status": "success", "score": score}), 200


    except Exception as e:
        logging.exception("Error during link prediction:")
        return jsonify({"status":"error", "message":str(e)}), 500

def get_node_embedding(model, node_name: str, node_map):
    # Xác định loại node dựa trên tiền tố
    node_type = 'drug' if node_name.startswith('DB') else 'gene'

    if node_type not in node_map or node_name not in node_map[node_type]:
        logging.warning(f"Node '{node_name}' (type: {node_type}) not found in node_map.")
        return None, None  # Return None for both embedding and type

    node_index = node_map[node_type][node_name]
    try:
        with torch.no_grad():
            # Lấy embedding cho một node cụ thể thuộc một loại cụ thể
            embedding = model(node_type, torch.tensor([node_index], device=device)).cpu().numpy()
        return embedding[0], node_type
    except Exception as e:
        # Bắt lỗi nếu model không thể lấy embedding cho node_type này (ví dụ: node_type không có trong metapath)
        logging.error(f"Error getting embedding for node '{node_name}' (type: {node_type}, index: {node_index}): {e}")
        return None, None

if __name__ == '__main__':
    app.run(debug=True, host="0.0.0.0", port=5001)
