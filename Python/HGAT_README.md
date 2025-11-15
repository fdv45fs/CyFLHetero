# HGAT (Heterogeneous Graph Attention Network) - User Guide

## 🎯 Overview

**HGAT** is a **deep learning model** for **heterogeneous graphs** (graphs with multiple node types, like Drug-Gene networks). It uses **attention mechanisms** to learn which connections are most important for each node type.

### ✅ Why HGAT for ChG-Miner (Drug-Gene Network)?

| Feature | HGAT | MetaPath2Vec | DGI |
|---------|------|--------------|-----|
| **Graph Type** | ✅ Heterogeneous | ✅ Heterogeneous | ❌ Homogeneous |
| **Learning** | ✅ Deep Learning | ❌ Shallow (Word2Vec) | ✅ Deep Learning |
| **Attention** | ✅ Type-aware | ❌ No | ❌ No |
| **Node Clustering** | ✅ YES | ✅ YES | ✅ YES |
| **Link Prediction** | ✅ YES | ✅ YES | ❌ NO (requires mod) |
| **Unsupervised** | ✅ YES | ✅ YES | ✅ YES |
| **No Features Needed** | ✅ YES (auto-gen) | ✅ YES | ✅ YES |

### 🔥 Key Advantages:
1. **Type-aware Attention**: Learn drug-drug, drug-gene, gene-gene relationships separately
2. **Deep Learning**: More powerful than MetaPath2Vec (shallow embeddings)
3. **No MetaPaths Required**: HGAT learns graph structure automatically
4. **Better Link Prediction**: Attention helps identify important drug-gene interactions
5. **Auto Feature Generation**: Works with ChG-Miner which has no node features

---

## 🏗️ Architecture

```
Input: Drug-Gene Edges (heterogeneous graph)
   ↓
Auto-generate Features (identity matrix or random)
   ↓
HGT Layer 1 (Heterogeneous Graph Transformer)
   ├─ Attention for Drug nodes
   ├─ Attention for Gene nodes
   └─ Cross-type attention (Drug ↔ Gene)
   ↓
HGT Layer 2 (refine embeddings)
   ↓
Output Embeddings (Drug: 64-dim, Gene: 64-dim)
   ↓
Unsupervised Loss (Link Prediction BCE)
   ↓
Applications:
   ├─ Node Clustering (K-Means on embeddings)
   └─ Link Prediction (cosine similarity of embeddings)
```

---

## 🚀 How to Use HGAT

### Step 1: Load Your Network
- Load `ChG-Miner_miner-chem-gene.tsv` in Cytoscape
- **No node features needed!** HGAT will auto-generate them.
- Network should have:
  - **Drug nodes** (e.g., DB00357, DB02721, ...)
  - **Gene nodes** (e.g., P05108, P00325, Q16539, ...)

### Step 2: Train HGAT Model
**Via Main Panel (Recommended):**
1. Open: `Apps > MyApp > Main Function`
2. Select: **Model = "HGAT"**
3. Click: **"Train Model"**
4. Wait ~5-15 minutes (depends on network size)

**Via Menu (Alternative):**
- `Apps > MyApp > Train on HGAT`

### Step 3: Node Clustering
1. In Main Panel, select: **Task = "Node clustering"**
2. Click: **"Run Task"**
3. **Result**: Each node gets a `cluster` attribute (0-9 by default)

### Step 4: Link Prediction (Drug-Gene Interactions)
**Predict Single Link:**
1. Select 2 nodes (1 drug + 1 gene)
2. In Main Panel, select: **Task = "Predict links (select 2 node)"**
3. Click: **"Run Task"**
4. **Result**: Popup shows link score (0-1, higher = stronger predicted interaction)

**Predict All Links (Top N):**
1. In Main Panel, select: **Task = "Predict All Links (Top 10)"**
2. Click: **"Run Task"**
3. **Result**: Dialog shows top 10 drug-gene pairs with highest scores

### Step 5: Visualize
**Clustering Visualization:**
1. Go to Cytoscape `Style` panel
2. Change node **Fill Color** → Map `cluster` attribute to color palette
3. Drugs and genes in the same cluster will have the same color 🎨

**Link Prediction Visualization:**
- Top predicted links can be added to the network as new edges
- Style them differently (e.g., dashed lines) to distinguish from real edges

---

## 🔧 Technical Details

### Model Architecture (from `train_HGAT.py`)
```python
class HGAT:
    - Input projection: Linear(-1 → hidden_channels)
    - HGT Layers: 2 layers × 4 attention heads
    - Output projection: Linear(hidden_channels → out_channels)
```

### Training Parameters
```python
hidden_channels = 128    # Hidden dimension
out_channels = 64        # Output embedding dimension
num_heads = 4            # Attention heads per layer
num_layers = 2           # Number of HGT layers
num_epochs = 200         # Training epochs
learning_rate = 0.001    # Adam optimizer
```

### Automatic Feature Generation
When node features are not provided, HGAT uses:
- **Identity matrix** (if num_nodes ≤ 128)
- **Random normalized vectors** (if num_nodes > 128, dim=128)

This is standard practice for GNNs on feature-less graphs!

### Unsupervised Loss Function
HGAT uses **link prediction loss** (binary cross-entropy):
- **Positive samples**: Existing drug-gene edges
- **Negative samples**: Random non-existing drug-gene pairs
- **Goal**: Embeddings of connected nodes should be similar

### Node Type Detection (Heuristic)
In `SendHeteroDataHGATTask.java`:
- **Drug nodes**: Start with `"DB"` (e.g., DB00357)
- **Gene nodes**: Start with `"P"` or `"Q"` (e.g., P05108, Q16539)
- **Default**: If unknown, treated as drug

---

## 📁 Files Involved

### Python Side
- **`train_HGAT.py`**: HGAT model definition + training
- **`server.py`**: Flask endpoints
  - `/receive_hetero_data_HGAT` - Training
  - `/cluster_nodes_HGAT` - Clustering
  - `/predict_links_HGAT` - Link prediction (single)
  - `/predict_all_links_HGAT` - Predict all links (top N)
- **Output files**:
  - `HGAT_trained_model.pth` - Saved model
  - `HGAT_embeddings.pkl` - Node embeddings

### Java Side
- **`SendHeteroDataHGATTask.java`**: Send ChG data to server
- **`SendHeteroDataHGATTaskFactory.java`**: Factory
- **`NodeEmbeddingsPanel.java`**: UI panel (has "HGAT" in dropdown)
- **`ClusterNodesTask.java`**: Clustering (routes to HGAT endpoint)
- **`PredictLinksTask.java`**: Link prediction (routes to HGAT endpoint)
- **`CyActivator.java`**: Registers HGAT menu items

---

## 🆚 HGAT vs. Other Models

### When to Use HGAT:
- ✅ You have a **heterogeneous graph** (multiple node types)
- ✅ You need **node clustering** across different types
- ✅ You need **link prediction** between types (e.g., drug-gene)
- ✅ You want **deep learning** (more powerful than MetaPath2Vec)
- ✅ You want **attention** (learn important connections)

### When NOT to Use HGAT:
- ❌ You have a **homogeneous graph** (one node type) → Use **DGI** or **GAT**
- ❌ You need **very fast training** → Use **MetaPath2Vec** or **Node2Vec**
- ❌ You have **very small graphs** (<100 nodes) → Shallow methods may be enough

### Comparison Table

| Model | Best For | Speed | Accuracy | Complexity |
|-------|----------|-------|----------|------------|
| **HGAT** | Hetero + Deep | 🔴 Slow | 🟢 High | 🔴 High |
| **MetaPath2Vec** | Hetero + Fast | 🟢 Fast | 🟡 Medium | 🟢 Low |
| **DGI** | Homo + No Features | 🟡 Medium | 🟢 High | 🟡 Medium |
| **GAT** | Homo + Features | 🟡 Medium | 🟢 High | 🟡 Medium |
| **Node2Vec** | Homo + Fast | 🟢 Fast | 🟡 Medium | 🟢 Low |
| **GCN** | Supervised Classification | 🟢 Fast | 🟢 High | 🟡 Medium |

---

## 🐛 Troubleshooting

### Error: "Model not trained"
**Solution**: Train HGAT first via Panel → Model: HGAT → Train Model

### Error: "Connection refused (port 5000)"
**Solution**: Start Python server:
```bash
cd Python
python server.py
```

### Error: Missing PyTorch/PyG dependencies
**Solution**: Install dependencies:
```bash
# For CPU
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
pip install pyg-lib torch-scatter torch-sparse torch-cluster torch-spline-conv -f https://data.pyg.org/whl/torch-2.7.0+cpu.html

# For GPU (CUDA 11.8)
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
pip install pyg-lib torch-scatter torch-sparse torch-cluster torch-spline-conv -f https://data.pyg.org/whl/torch-2.7.0+cu118.html
```

### Training takes too long
**Solutions**:
1. Reduce `num_epochs` in `train_HGAT.py` (200 → 100)
2. Reduce `hidden_channels` (128 → 64)
3. Reduce `num_layers` (2 → 1)
4. Use GPU if available (CUDA)

### Clustering results not meaningful
**Solutions**:
1. Train longer (increase `num_epochs` to 300-500)
2. Try different number of clusters (modify `num_clusters` in `ClusterNodesTask.java`)
3. Increase model capacity (`hidden_channels` 128 → 256)
4. Check if network has enough structure (too sparse = bad clustering)

### Link Prediction scores all similar
**Solutions**:
1. Train longer (model hasn't converged yet)
2. Increase `num_heads` (4 → 8) for more attention diversity
3. Check if network is too dense (all drugs connect to all genes = hard to learn)

---

## 📊 Example Use Case: ChG-Miner

### Network Info:
- **Nodes**: ~15,141 drug-gene interactions
- **Node Types**: 2 (Drug, Gene)
- **Edge Type**: Drug → Gene (interaction)

### Training Time Estimate:
- **CPU**: ~10-15 minutes
- **GPU (CUDA)**: ~3-5 minutes

### Expected Results:
1. **Clustering**:
   - Drugs with similar gene targets → Same cluster
   - Genes targeted by similar drugs → Same cluster
   - Useful for drug repurposing!

2. **Link Prediction**:
   - Discover new potential drug-gene interactions
   - Validate with experimental data
   - Top scored links = most confident predictions

---

## 📚 References

- **Original Paper**: "Heterogeneous Graph Transformer" (Hu et al., WWW 2020)
- **PyTorch Geometric**: https://pytorch-geometric.readthedocs.io/
- **HGTConv Documentation**: https://pytorch-geometric.readthedocs.io/en/latest/modules/nn.html#torch_geometric.nn.conv.HGTConv

---

## ✅ Summary

**HGAT is the BEST choice for ChG-Miner** because:
1. ✅ Heterogeneous graph (Drug + Gene)
2. ✅ Deep learning with attention (more powerful than MetaPath2Vec)
3. ✅ No features needed (auto-generates)
4. ✅ Supports both clustering AND link prediction
5. ✅ Type-aware (learns drug vs. gene separately)

**Perfect for drug repurposing and discovering new drug-gene interactions!** 🎉💊🧬

---

## 🔗 Related Files

- `DGI_README.md` - For homogeneous graphs (e.g., ChCh-Miner)
- `GAT_UNSUPERVISED_README.md` - For homogeneous graphs with features
- `train_HGAT.py` - HGAT implementation
- `server.py` - All endpoints (HGAT, DGI, GAT, MetaPath2Vec, etc.)

