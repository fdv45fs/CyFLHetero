# 🧠 Graph Attention Network (GAT) - Node Clustering

## 📌 Tổng quan

GAT (Graph Attention Network) là một kiến trúc GNN hiện đại sử dụng **multi-head attention mechanism** để học embeddings chất lượng cao cho **node clustering**.

### 🎯 Use Case: Node Clustering (Unsupervised)

GAT trong project này được dùng để:
- Train supervised learning với labels → Học embeddings tốt
- Sau đó dùng embeddings cho **unsupervised clustering** với KMeans
- Tìm communities/clusters trong network

### 🆚 So sánh với các model khác

| Model | Use Case | Clustering Method |
|-------|----------|-------------------|
| **MetaPath2Vec** | Hetero network | ✅ KMeans on embeddings |
| **Node2Vec** | Homo network | ✅ KMeans on embeddings |
| **GCN** | Homo network | ❌ Not implemented |
| **GAT** | Homo network | ✅ KMeans on embeddings ⭐ |

---

## 🏗️ Kiến trúc GAT

```
Input Features (1433)
    ↓
[GAT Layer 1] → 8 heads × 8 hidden = 64 output
    ↓ ELU + Dropout
[GAT Layer 2] → 8 heads × 8 hidden = 64 output
    ↓ ELU + Dropout
[GAT Layer 3] → 1 head × 7 classes = 7 output
    ↓ Softmax
Predicted Classes (7)
```

### Hyperparameters mặc định:

- **Hidden dimension**: 8
- **Number of heads**: 8
- **Dropout rate**: 0.6
- **Learning rate**: 0.005
- **Weight decay**: 5e-4
- **Epochs**: 200

---

## 🚀 Cách sử dụng trong Cytoscape

### **Option 1: Sử dụng Menu (Training)**

#### **Train GAT Model**
```
Apps → MyApp → Train on GAT
```
- Load network có node features (Label, Split, Features)
- Server sẽ train GAT model và lưu:
  - `GAT_trained_model.pth` (model weights)
  - `GAT_embeddings.pkl` (node embeddings - used for clustering)
  - `features.pkl` (node features)

---

### **Option 2: Sử dụng Panel (Recommended) ⭐**

#### **Bước 1: Open Panel**
```
Apps → MyApp → Main Function
```

#### **Bước 2: Select Model**
- Dropdown "Model": Chọn **GAT**
- Network Type: **Homogeneous Network**
- Dimension: 128 (hoặc tùy chọn)

#### **Bước 3: Train Model**
- Click **Train Model** button
- Đợi training hoàn thành (200 epochs)
- Server sẽ hiển thị:
  ```
  Epoch 190 | Train Loss: 0.0234 | Test Loss: 0.3421 | Test Acc: 0.8234
  ...
  Training Complete!
  Best Test Accuracy: 0.8456
  ✅ Model saved at GAT_trained_model.pth
  ✅ Embeddings saved at GAT_embeddings.pkl
  ```

#### **Bước 4: Node Clustering**
- Chọn task: **Node clustering**
- Click **Run Task**
- Server sẽ:
  - Load GAT embeddings từ `GAT_embeddings.pkl`
  - Run KMeans clustering (default: 10 clusters)
  - Return cluster assignments
- Kết quả: Mỗi node được gán attribute `cluster` (0-9)

---

## 📊 Dataset Requirements

### Node attributes cần có:

```java
// Example node table trong Cytoscape:
name     | Features                        | Label        | Split
---------|----------------------------------|--------------|-------
node1    | 0.1 0.2 0.3 ... (1433 features) | Case_Based   | Train
node2    | 0.4 0.5 0.6 ... (1433 features) | Genetic_Algorithms | Test
node3    | 0.7 0.8 0.9 ... (1433 features) | Neural_Networks | Train
...
```

### Định dạng:
- **Features**: Space-separated numeric values (e.g., "0.1 0.2 0.3 ...")
- **Label**: Class label string
- **Split**: "Train" hoặc "Test"

---

## 🔧 Files được tạo sau khi train

```
Python/
├── GAT_trained_model.pth      # Trained GAT model weights
├── GAT_embeddings.pkl          # Node embeddings + metadata
│   ├── embeddings: np.array   # Node embeddings (n_nodes × output_dim)
│   ├── node_mapping: dict     # node_name → node_index
│   ├── edge_index: np.array   # Edge indices
│   └── label_mapping: dict    # label_string → label_index
└── features.pkl                # Original node features (shared with GCN)
```

---

## 📡 API Endpoints (server.py)

### 1. Train GAT
```http
POST http://localhost:5000/receive_edge_indices_and_features_GAT
Content-Type: application/json

{
  "edge_index": [
    {"source": "node1", "target": "node2"},
    {"source": "node2", "target": "node3"}
  ],
  "node_features": [
    {
      "name": "node1",
      "features": {
        "Features": "0.1 0.2 0.3 ...",
        "Label": "Case_Based",
        "Split": "Train"
      }
    }
  ]
}
```

**Response:**
```json
{
  "status": "success",
  "message": "Edges and node features received, GAT model trained"
}
```

---

### 2. Cluster Nodes (KMeans on GAT embeddings)
```http
POST http://localhost:5000/cluster_nodes_GAT
Content-Type: application/json

{
  "num_clusters": 10
}
```

**Response:**
```json
{
  "status": "success",
  "node_to_cluster": {
    "node1": 0,
    "node2": 3,
    "node3": 0,
    "node4": 7,
    ...
  }
}
```

---

## 🐛 Troubleshooting

### Error: "Required file not found"
```
Error: Required file not found: GAT_embeddings.pkl. Please train GAT model first.
```
**Solution:** Train GAT model trước khi predict:
```
Apps → MyApp → Train on GAT
```

---

### Error: "Node 'xxx' not found in node mapping"
```
Node 'node123' not found in node mapping
```
**Solution:** 
- Node đó không tồn tại trong training data
- Check lại tên node trong network

---

### Error: Model không converge
```
Training Complete!
Best Test Accuracy: 0.3456  # Too low!
```
**Solution:**
- Tăng số epochs trong `train_GAT.py`: `num_epochs=500`
- Giảm learning rate: `learning_rate=0.001`
- Giảm dropout: `dropout=0.4`

---

## 🎯 Use Cases

### 1. **Node Clustering** (Primary Use Case) ⭐
- Train GAT với supervised learning để học embeddings tốt
- Dùng embeddings cho unsupervised clustering với KMeans
- Example: Community detection, topic grouping

**Workflow:**
```
Train GAT (supervised) → Get embeddings → KMeans → Cluster assignments
```

### 2. **Comparison with other clustering methods**
```
MetaPath2Vec → Hetero network → Embeddings → Clusters
Node2Vec → Homo network → Embeddings → Clusters
GAT → Homo network + Features → Embeddings → Clusters ⭐ Better quality!
```

### 3. **Attention Visualization** (future)
- Visualize attention weights giữa các nodes
- Hiểu node nào quan trọng nhất cho clustering

---

## 📚 References

- **Paper**: [Graph Attention Networks (GAT)](https://arxiv.org/abs/1710.10903) - Veličković et al., ICLR 2018
- **PyTorch Geometric**: [GATConv Documentation](https://pytorch-geometric.readthedocs.io/en/latest/modules/nn.html#torch_geometric.nn.conv.GATConv)
- **Dataset**: Cora citation network (1433 features, 7 classes)

---

## 🚀 Next Steps

1. **Experiment with clustering parameters**:
   - Try different number of clusters: `num_clusters=5` or `num_clusters=20`
   - Compare clustering quality across models (Node2Vec vs GAT)
   - Analyze cluster coherence

2. **Compare clustering methods**:
   ```
   Node2Vec clustering  vs  GAT clustering
   ↓                        ↓
   Which produces better communities?
   Which captures node features better?
   ```

3. **Visualize results**:
   - Color nodes by cluster ID in Cytoscape
   - Analyze cluster structure
   - Extract attention weights (future feature)

---

## 🔍 Why GAT for Clustering?

GAT learns **better embeddings** than Node2Vec because:
- ✅ Uses node features (not just structure)
- ✅ Attention mechanism captures importance
- ✅ Supervised training guides learning
- ✅ Multi-head attention = richer representations

→ Better embeddings = Better clustering! 🎯

---

**Happy Graph Clustering! 🎉**

