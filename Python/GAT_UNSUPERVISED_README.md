# 🔥 GAT Unsupervised - Node Clustering (Học không giám sát)

## 📌 Quan trọng!

**GAT trong project này dùng cho HỌC KHÔNG GIÁM SÁT (Unsupervised Learning) cho node clustering!**

### ❌ KHÔNG cần:
- ❌ `Label` attribute (không cần nhãn)
- ❌ `Split` attribute (không cần train/test split)
- ❌ `Features` attribute (tự động generate nếu không có!)

### ✅ CHỈ cần:
- ✅ Edge connections (cấu trúc đồ thị)
- ✅ [Optional] `Features` attribute (nếu không có sẽ auto-generate với Xavier init)

---

## 🎯 Workflow

```
1. Load Graph (Homo or Hetero)
   ↓
2. Auto-generate Features (if not provided - Xavier init 64-dim)
   ↓
3. GAT learns embeddings (reconstruction loss + attention)
   ↓
4. KMeans clustering on embeddings
   ↓
5. Cluster assignments → Color nodes
```

## 📊 Use Cases

✅ **Homogeneous Graphs** (e.g., ChCh-Miner_durgbank-chem-chem.tsv)
- Drug-drug interaction networks
- Auto-generates features (Xavier initialization)
- Clusters similar drugs

✅ **Heterogeneous Graphs** (if you provide Features)
- Networks with node features
- Uses your custom features
- More accurate clustering

---

## 📊 Dataset Requirements

### Node attributes cần có:

```
name      | Features
----------|------------------------------------------
node1     | 0.1 0.2 0.3 0.4 ... (space-separated)
node2     | 0.5 0.6 0.7 0.8 ...
node3     | 0.9 1.0 1.1 1.2 ...
```

**Chỉ cần 2 columns:**
1. `name` - Tên node
2. `Features` - Vector đặc trưng (space-separated numbers)

**KHÔNG cần `Label` hay `Split`!**

---

## 🚀 Cách sử dụng

### **Bước 1: Prepare network**
Load network trong Cytoscape với:
- Nodes có attribute `Features` (vector số)
- Edges (connections)

### **Bước 2: Open Panel**
```
Apps → MyApp → Main Function
```

### **Bước 3: Train GAT (Unsupervised)**
```
Model: Chọn "GAT"
  ↓
Click "Train Model"
  ↓
Server training:
  - 50 epochs (faster than supervised)
  - Reconstruction loss
  - No labels needed!
  ↓
✅ GAT_embeddings.pkl saved
```

### **Bước 4: Clustering**
```
Task: Chọn "Node clustering"
  ↓
Click "Run Task"
  ↓
KMeans clusters nodes (default: 10 clusters)
  ↓
✅ Each node gets "cluster" attribute (0-9)
```

---

## 🔧 Technical Details

### GAT Architecture (Unsupervised)
```
Input: Node Features (input_dim)
    ↓
GAT Layer 1: Multi-head attention (heads=4)
    ↓
GAT Layer 2: Multi-head attention (heads=4)
    ↓
GAT Layer 3: Output embeddings (output_dim=64)
    ↓
Reconstruction Loss: MSE between feature similarities
```

### Training Parameters
```python
num_epochs = 50        # Fewer than supervised
hidden_dim = 16        # Smaller than supervised
heads = 4              # Fewer heads
output_dim = 64        # Embedding dimension
dropout = 0.3          # Lower dropout
learning_rate = 0.01   # Higher learning rate
```

### Loss Function
```python
# Reconstruction loss (self-supervised)
embeddings = GAT(features, edges)
reconstruction = embeddings @ embeddings.T  # Similarity matrix
target = features @ features.T              # Target similarity
loss = MSE(reconstruction, target)
```

---

## 🆚 So sánh với các model khác

| Model | Learning Type | Need Labels? | Clustering |
|-------|---------------|--------------|------------|
| **MetaPath2Vec** | Unsupervised | ❌ No | ✅ Yes |
| **Node2Vec** | Unsupervised | ❌ No | ✅ Yes |
| **GCN** | Supervised | ✅ Yes | ❌ No |
| **GAT (ours)** | **Unsupervised** | ❌ **No** | ✅ **Yes** ⭐ |

**GAT advantages:**
- ✅ No labels needed (unsupervised)
- ✅ Uses node features (better than Node2Vec)
- ✅ Attention mechanism (learns importance)
- ✅ Fast training (50 epochs vs 200)

---

## 📁 Files Created

```
Python/
├── train_GAT_unsupervised.py    ⭐ NEW - Unsupervised training
├── train_GAT.py                 (Old - supervised, not used)
├── server.py                    ✅ Updated - uses unsupervised
└── GAT_embeddings.pkl           (Generated after training)
    ├── embeddings               # Node embeddings for clustering
    ├── node_mapping             # node_name → index
    └── edge_index               # Graph structure
```

---

## 🐛 Troubleshooting

### Error: "Missing 'Features' attribute"
```
Node 'xxx' has no 'Features' attribute
```
**Solution:** 
- Make sure ALL nodes have `Features` column
- Features should be space-separated numbers
- Example: `"0.1 0.2 0.3 0.4 ..."`

---

### Error: "No edges provided"
```
ERROR: No edges provided
```
**Solution:**
- Network must have edges (connections)
- Cannot cluster isolated nodes

---

### Warning: Network has no features
```
Warning: Node has no 'Features' attribute, using random features
```
**Solution:**
- If nodes truly have no features, GAT will use random initialization
- Clustering quality will be lower without real features

---

## ✅ Example Workflow

**1. Load Cora dataset (or any network with features)**
```
File → Import → Network from File
  → Select Cora nodes/edges files
```

**2. Check if `Features` column exists**
```
View → Show Node Table
  → Should see "Features" column with values like "0.1 0.2 ..."
```

**3. Train GAT**
```
Apps → MyApp → Main Function
  → Model: GAT
  → Train Model
  
Console shows:
[GAT Unsupervised] Number of edges: 5429
[GAT Unsupervised] Number of nodes: 2708
Starting UNSUPERVISED GAT training for node clustering
Epoch   0 | Reconstruction Loss: 0.123456
...
Epoch  49 | Reconstruction Loss: 0.012345
✅ GAT training completed! Ready for clustering.
```

**4. Cluster nodes**
```
Task: Node clustering
  → Run Task
  
Result: Nodes colored by cluster
```

---

## 🎉 Key Points

1. **NO LABELS NEEDED** ✅
   - Pure unsupervised learning
   - Only needs node features

2. **Faster training** ⚡
   - 50 epochs (vs 200 for supervised)
   - Smaller model (fewer parameters)

3. **Better than Node2Vec** 🚀
   - Uses actual node features
   - Attention mechanism
   - Reconstruction-based learning

4. **Ready for clustering** 🎯
   - Embeddings optimized for similarity
   - KMeans works great on these embeddings

---

**Ready to cluster! 🔥**

