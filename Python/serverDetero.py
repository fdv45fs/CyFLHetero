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

def get_node_embeddings(model, data): # Lấy tất cả embedding 1 lần
    """Lấy embedding cho tất cả các nút."""
    z_dict = {}
    for node_type in data.node_types:
      z_dict[node_type] = model(node_type)
    return z_dict

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
    global data, model, node_map  # Khai báo model ở đây
    try:
        req_data = request.get_json()
        logging.debug(f"Received request data: {req_data}")
        edge_index = req_data.get('edge_index', [])
        metapath = req_data.get('metapath', [])

        if not edge_index or not metapath:
            return jsonify({"status": "error", "message": "Missing data"}), 400

        metapath_tuples = [(metapath[i], metapath[i+1], metapath[i+2]) for i in range(0, len(metapath) - 2, 2)]
        data = create_hetero_data(edge_index)
        model = train_metapath2vec(data, metapath_tuples)  # Gán giá trị cho model
        return jsonify({"status": "success"}), 200

    except Exception as e:
        logging.exception("Error during request processing:")
        return jsonify({"status": "error", "message": str(e)}), 400

@app.route('/cluster_nodes', methods=['POST'])
def cluster_nodes():
    global model, data, node_map  # Sử dụng node_map toàn cục
    if model is None or data is None or node_map is None:
        return jsonify({"status": "error", "message": "Model not trained"}), 400

    try:
        req_data = request.get_json()
        num_clusters = req_data.get('num_clusters', 10)

        embeddings = get_node_embeddings(model, data)
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
