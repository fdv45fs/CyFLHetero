# DGI (Deep Graph Infomax) - Unsupervised Node Clustering

## 🎯 Overview

**DGI (Deep Graph Infomax)** is an unsupervised graph neural network model that learns node embeddings by maximizing **mutual information** between node-level and graph-level representations. 

### ✅ Key Advantages for Your Use Case:
- **NO NODE FEATURES REQUIRED** - Perfect for `ChCh-Miner_durgbank-chem-chem.tsv` which only has edges!
- **Automatic Feature Generation** - Uses graph structure (degrees) to create initial features
- **Pure Unsupervised Learning** - No labels or annotations needed
- **GCN-based Encoder** - Uses efficient Graph Convolutional Networks
- **InfoMax Principle** - Learns by distinguishing real vs. corrupted graph representations

---

## 📊 What DGI Does

### Training (Unsupervised)
1. **Input**: Edge list only (node features auto-generated)
2. **Process**:
   - Creates positive samples (real graph)
   - Creates negative samples (corrupted graph with permuted features)
   - Trains encoder to distinguish real from corrupted
   - Maximizes mutual information between local and global representations
3. **Output**: Node embeddings saved to `DGI_embeddings.pkl`

### Clustering (Task)
1. **Input**: Trained DGI embeddings
2. **Process**: K-Means clustering on learned embeddings
3. **Output**: Cluster assignments as node attributes in Cytoscape

---

## 🚀 How to Use DGI

### Step 1: Load Your Network
- Load `ChCh-Miner_durgbank-chem-chem.tsv` in Cytoscape
- **No need for node features!** DGI will auto-generate them.

### Step 2: Train DGI Model
**Via Main Panel:**
1. Open: `Apps > MyApp > Main Function`
2. Select: **Model = "DGI"**
3. Click: **"Train Model"**

**Via Menu (Alternative):**
- `Apps > MyApp > Train on DGI`

### Step 3: Cluster Nodes
1. In Main Panel, select: **Task = "Node clustering"**
2. Click: **"Run Task"**
3. **Result**: Each node will have a `cluster` attribute with cluster ID (0-9 by default)

### Step 4: Visualize Clusters
1. In Cytoscape, go to: `Style` panel
2. Change node color: **Map `cluster` attribute to color**
3. Enjoy your clustered network! 🎨

---

## 🔧 Technical Details

### Model Architecture
```
Input: Edge Index + Auto-generated Features
   ↓
GCN Encoder (2 layers with PReLU)
   ↓
Node Embeddings (512-dim by default)
   ↓
Discriminator (distinguishes real vs. fake)
   ↓
Loss: Binary Cross-Entropy (InfoMax objective)
```

### Automatic Feature Generation
When no features are provided, DGI uses:
- **One-hot degree encoding** OR
- **Identity matrix** (each node gets a unique feature vector)

This is a standard practice in unsupervised GNNs!

### Training Parameters (in `train_DGI.py`)
```python
num_epochs = 300        # More epochs = better embeddings
hidden_dim = 512        # Embedding dimension
learning_rate = 0.001   # Adam optimizer
```

### Clustering Parameters
- **Default clusters**: 10
- **Algorithm**: K-Means
- **Distance metric**: Euclidean distance in embedding space

---

## 📁 Files Involved

### Python Server (`server.py`)
- **Endpoint**: `/receive_edge_indices_DGI` (training)
- **Endpoint**: `/cluster_nodes_DGI` (clustering)
- **Port**: `5000`

### Python Training Script
- **File**: `train_DGI.py`
- **Model**: `Encoder` (GCN-based), `Discriminator`, `DGI` wrapper
- **Output**: `DGI_embeddings.pkl`

### Java Tasks
- **Training**: `SendEdgeIndicesDGITask.java`
- **Clustering**: `ClusterNodesTask.java` (routes to DGI endpoint)

---

## 🆚 DGI vs. Other Models

| Feature | DGI | GAT | Node2Vec | GCN |
|---------|-----|-----|----------|-----|
| **Needs Features?** | ❌ NO | ✅ YES | ❌ NO | ✅ YES |
| **Supervised?** | ❌ NO | ❌ NO (our version) | ❌ NO | ✅ YES |
| **For Clustering?** | ✅ YES | ✅ YES | ✅ YES | ❌ NO |
| **Works on Homo Graphs?** | ✅ YES | ✅ YES | ✅ YES | ✅ YES |
| **Speed** | 🔵 Medium | 🔵 Medium | 🟢 Fast | 🟢 Fast |
| **Best For** | Feature-less graphs | Feature-rich graphs | Simple graphs | Classification |

### When to Use DGI:
- ✅ You have **only edges** (no node features)
- ✅ You need **unsupervised clustering**
- ✅ You want **deep learning** (more powerful than Node2Vec)
- ✅ You have a **homogeneous graph** (one node type)

### When NOT to Use DGI:
- ❌ You have rich node features → Use **GAT** instead
- ❌ You need fast training → Use **Node2Vec** instead
- ❌ You need supervised classification → Use **GCN** instead
- ❌ You have a heterogeneous graph → Use **MetaPath2Vec** instead

---

## 🐛 Troubleshooting

### Error: "Model not trained"
**Solution**: Train DGI first via Panel → Model: DGI → Train Model

### Error: "Connection refused (port 5000)"
**Solution**: Start Python server:
```bash
cd Python
python server.py
```

### Error: Missing PyTorch/PyG dependencies
**Solution**: Install dependencies (see `requirements_cpu.txt` or `requirements_gpu.txt`)

### Training takes too long
**Solution**: Reduce `num_epochs` in `train_DGI.py` (e.g., from 300 to 100)

### Clustering results not good
**Solution**: 
1. Try training longer (increase `num_epochs`)
2. Try different `hidden_dim` (256, 512, 1024)
3. Try different number of clusters (change `num_clusters` in `ClusterNodesTask.java`)

---

## 📚 References

- **Original Paper**: "Deep Graph Infomax" (Veličković et al., ICLR 2019)
- **PyTorch Geometric**: https://pytorch-geometric.readthedocs.io/
- **Tutorial**: https://pytorch-geometric-temporal.readthedocs.io/en/latest/notes/introduction.html

---

## ✅ Summary

DGI is **perfect for your ChCh-Miner chemical-chemical network** because:
1. ✅ No need for node features (only edges)
2. ✅ Fully unsupervised (no labels needed)
3. ✅ Powerful deep learning (better than shallow methods)
4. ✅ Easy to use (just 2 clicks in the panel!)

Enjoy clustering! 🎉

