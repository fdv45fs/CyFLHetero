# GTN (Graph Transformer Network) for Heterogeneous Graphs

## 🎯 Overview

**GTN (Graph Transformer Network)** is an advanced deep learning model designed for **heterogeneous graphs** with multiple node types and edge types. Unlike traditional GNNs, GTN automatically **learns meta-paths** through multi-channel graph transformations, making it ideal for complex networks like Drug-Gene interactions.

## 🔥 Key Features

- ✅ **Automatic Meta-Path Learning**: No need to manually define meta-paths
- ✅ **Multi-Channel Transformation**: Learns multiple graph views simultaneously
- ✅ **Type-Aware Processing**: Handles heterogeneous node and edge types
- ✅ **Unsupervised Learning**: No labels required for training
- ✅ **GPU Auto-Detection**: Automatically uses CUDA if available
- ✅ **Node Clustering**: K-means clustering on learned embeddings
- ✅ **Link Prediction**: Predicts interaction scores between nodes

## 📊 Model Architecture

```
Input: Heterogeneous Graph (Drug nodes, Gene nodes, Drug-Gene edges)
   ↓
Graph Transformation Layers (Learn meta-paths automatically)
   ↓
GAT Layers (Apply attention on transformed graphs)
   ↓
Node Embeddings (64-dim)
   ↓
Tasks: Clustering, Link Prediction
```

### Architecture Details

1. **GT Layers**: 
   - Learn soft selection of edge types
   - Create multi-channel graph transformations
   - Automatically discover important meta-paths

2. **Feature Transformation**:
   - Xavier-initialized random features (64-dim)
   - Linear projection to hidden dimension (128-dim)

3. **GAT Layers**:
   - 2-layer Graph Attention Network
   - Multi-head attention (4 heads)
   - Dropout: 0.6 for regularization

4. **Decoder**:
   - Reconstructs edges for unsupervised training
   - Binary cross-entropy loss with negative sampling

## 🚀 Usage (via Cytoscape)

### Step 1: Load Network
```
File → Import → Network from File
Select: ChG-Miner_miner-chem-gene.tsv
```

### Step 2: Train GTN Model
```
Apps → MyApp → Main Function
1. Select "GTN" from Model dropdown
2. Click "Train Model"
3. Wait for training to complete (~1-2 minutes)
```

### Step 3: Node Clustering
```
In the same panel:
1. Select Task: "Node clustering"
2. Click "Run Task"
3. Nodes will be colored by cluster
```

### Step 4: Link Prediction

**Option A: Predict Single Link**
```
1. Select 2 nodes (Ctrl+Click)
2. Task: "Predict Link Score (2 nodes)"
3. Click "Run Task"
→ Shows interaction score
```

**Option B: Predict Top N Links**
```
1. Task: "Predict All Links (Top 10)"
2. Click "Run Task"
→ Shows top 10 drug-gene interactions
```

## 🔬 Model Parameters

### Training Hyperparameters
```python
feature_dim = 64        # Input feature dimension
hidden_dim = 128        # Hidden layer dimension
out_dim = 64           # Output embedding dimension
num_epochs = 5         # Quick training (can increase to 100+)
learning_rate = 0.005  # Adam optimizer
weight_decay = 5e-4    # L2 regularization
dropout = 0.6          # Dropout rate
num_heads = 4          # Attention heads
num_channels = 2       # Meta-path channels
num_layers = 2         # GT layer depth
```

### Graph Statistics (ChG-Miner)
```
Nodes: ~15,000 (drugs + genes)
Edges: ~15,141 drug-gene interactions
Node Types: 2 (drug, gene)
Edge Types: 2 (drug→gene, gene→drug)
```

## 📈 Training Process

```
Epoch 1/5  | Loss: 2.1543 | Pos: 0.6921 | Neg: 1.4622
Epoch 10/5 | Loss: 1.3244 | Pos: 0.3891 | Neg: 0.9353
...
Epoch 5/5  | Loss: 0.8932 | Pos: 0.2134 | Neg: 0.6798

✅ Training completed!
📁 Embeddings saved to GTN_embeddings.pkl
```

## 🔍 Output Files

### GTN_embeddings.pkl
Pickle file containing:
```python
{
    "embeddings": {
        "drug": np.array([...]),  # Drug embeddings [N_drugs, 64]
        "gene": np.array([...])   # Gene embeddings [N_genes, 64]
    },
    "node_mapping": {
        "drug": {"DB00001": 0, "DB00002": 1, ...},
        "gene": {"P12345": 0, "Q67890": 1, ...}
    },
    "model_type": "GTN"
}
```

## 🎨 Node Type Detection (Heuristic)

GTN automatically detects node types based on name prefixes:

| Prefix | Node Type | Example |
|--------|-----------|---------|
| `DB*`  | Drug      | DB00001, DB01234 |
| `P*`   | Gene      | P12345, P08254 |
| `Q*`   | Gene      | Q9Y6K9, Q15848 |
| Other  | Drug (default) | - |

## 💡 GTN vs HGAT

| Feature | **GTN** | **HGAT** |
|---------|---------|----------|
| **Meta-Path Learning** | ✅ Automatic | ❌ Type-aware attention only |
| **Architecture** | GT + GAT | HGT (Heterogeneous Transformer) |
| **Complexity** | Higher | Moderate |
| **Training Speed** | Slower | Faster |
| **Use Case** | Complex meta-path discovery | Direct type-aware modeling |
| **Best For** | Unknown relationships | Known heterogeneous structures |

## 📊 Use Cases

### 1. **Drug-Gene Interaction Prediction**
```
Predict which drugs interact with specific genes
→ Drug repurposing opportunities
```

### 2. **Network Module Discovery**
```
Find clusters of related drugs or genes
→ Functional modules in biological networks
```

### 3. **Biomarker Discovery**
```
Identify gene clusters associated with drug effects
→ Precision medicine applications
```

## 🐛 Troubleshooting

### Issue 1: "Model not trained" error
**Solution**: 
- Make sure you clicked "Train Model" first
- Wait for training to complete before clustering/prediction

### Issue 2: Slow training
**Solution**:
- Check if GPU is detected: Look for "Using device: cuda" in logs
- If CPU only, training will be slower (5-10x)
- Reduce `num_epochs` in `server.py` (line 1006) for faster testing

### Issue 3: No node types detected
**Solution**:
- Check that node names follow the convention (DB*/P*/Q*)
- Modify `detectNodeType()` in `SendHeteroDataGTNTask.java` if needed

### Issue 4: Out of memory
**Solution**:
- Reduce `hidden_dim` from 128 to 64
- Reduce `batch_size` if using batching
- Use CPU instead of GPU (automatic fallback)

## 🔧 Advanced Configuration

### Modify Training Parameters
Edit `Python/server.py` line 1000-1008:

```python
embeddings, node_map = train_model_GTN(
    edges=edges,
    node_types=node_types,
    feature_dim=64,        # ← Change here
    hidden_dim=128,        # ← Change here
    out_dim=64,            # ← Change here
    num_epochs=100,        # ← Change here (default: 5)
    lr=0.005              # ← Change here
)
```

### Customize Node Type Detection
Edit `Cytoscape/.../SendHeteroDataGTNTask.java` line 142-155:

```java
private String detectNodeType(String nodeName) {
    // Add your custom logic here
    if (nodeName.startsWith("CUSTOM_PREFIX")) {
        return "custom_type";
    }
    // ... existing logic
}
```

## 📚 References

1. **Graph Transformer Networks** (NeurIPS 2019)
   - Paper: https://arxiv.org/abs/1911.06455
   - Automatic meta-path learning for heterogeneous graphs

2. **PyTorch Geometric**
   - Documentation: https://pytorch-geometric.readthedocs.io/
   - GATConv, Linear layers

## 🎓 Example Workflow

```
1. Load ChG network (15,141 edges)
   ↓
2. Select GTN model → Train (1-2 minutes)
   ↓
3. Cluster nodes → 3 clusters
   ↓
4. Predict links between drug DB00001 and gene P12345
   → Score: 0.8234 (high interaction probability)
   ↓
5. Predict top 10 drug-gene links
   → Discover novel drug-gene associations
```

## 🚨 Important Notes

1. **First time training**: Takes 1-2 minutes on CPU, <30 seconds on GPU
2. **Embeddings persist**: Saved to disk, no need to retrain for clustering/prediction
3. **Retrain when**: Network structure changes or you want different hyperparameters
4. **GPU recommended**: 10-20x faster training with CUDA-enabled GPU

## ✅ Success Indicators

Console output should show:
```
[GTN] Using device: cuda
[GTN] GPU detected: NVIDIA GeForce RTX 3080
[GTN] Detected node types: {'drug', 'gene'}
[GTN] drug: 1234 nodes
[GTN] gene: 5678 nodes
[GTN] Training for 5 epochs...
  Epoch 5/5 | Loss: 0.8932
[GTN] Training completed!
[GTN] Embeddings saved to GTN_embeddings.pkl
```

---

## 📧 Support

For issues or questions:
- Check server logs: Python console output
- Check Cytoscape console: Java output
- Verify files exist: `GTN_embeddings.pkl` in Python directory

**Happy Graph Learning with GTN! 🎉**

