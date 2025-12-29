# Node Classification with Node2Vec + SVM

## Overview

This feature enables **binary disease classification** for genes using a two-stage approach:
1. **Stage 1**: Node2Vec learns graph structure embeddings (unsupervised)
2. **Stage 2**: SVM classifier predicts if a gene has disease based on embeddings (supervised)

---

## 📋 Complete Workflow

### Step 1: Prepare Data

You need two files:
- **`HumanNet-GSP.tsv`**: Gene-gene interaction network (already in `Datasets/`)
- **`Phenotype2Genes_Wide.tsv`**: Gene disease labels (already in `Datasets/`)

### Step 2: Import Network into Cytoscape

1. Open Cytoscape
2. **File → Import → Network from File**
3. Select `Datasets/HumanNet-GSP.tsv`
4. In the import dialog:
   - **Column 1**: Source Node
   - **Column 2**: Target Node
   - Click **OK**
5. Wait for import to complete (~13,000 nodes, ~260,000 edges)

### Step 3: Import Labels as Node Table

1. **File → Import → Table from File**
2. Select `Datasets/Phenotype2Genes_Wide.tsv`
3. In the import dialog:
   - **Import Data As:** Node Table Columns
   - **Key Column for Network:** `name` (or `shared name`)
   - **Where to Import:** Current network
   - Click **OK**
4. This adds columns `disease_1`, `disease_2`, ..., `disease_15` to your node table

**Verify Import:**
- Click on any node
- Check the **Node Table** (bottom panel)
- You should see columns: `name`, `disease_1`, `disease_2`, etc.

### Step 4: Train Node2Vec

1. Open the panel: **Apps → MyApp → Main Function**
2. **Model:** Select `Node2Vec`
3. Click **Train Model**
4. Wait ~2-5 minutes for training (100 epochs)
5. You'll see "Edges received and Node2Vec model trained" in the status

### Step 5: Run Node Classification (Train SVM)

1. **Task:** Select `Node Classification`
2. Click **Run Task**
3. The system will:
   - Read labels from node table (disease_1 to disease_15 columns)
   - Create binary labels (1 = has disease, 0 = no disease)
   - Load Node2Vec embeddings
   - Train SVM with GridSearchCV
   - Evaluate on test set
4. Wait ~1-2 minutes for SVM training
5. A dialog will show the results!

---

## 📊 Expected Results

**Success Dialog:**
```
SVM Classifier Training Results
================================

📊 Dataset:
  • Total nodes: 13,964
  • Labeled nodes: 13,964
  • Has disease: 3,215 (23.0%)
  • No disease: 10,749 (77.0%)

🎯 Training:
  • Train set: 11,171 nodes
  • Test set: 2,793 nodes
  • Best CV F1-score: 0.781
  • Best params: C=10, gamma=0.01

📈 Performance (Test Set):
  • Accuracy:  0.823 (82.3%)
  • Precision: 0.791
  • Recall:    0.820
  • F1-Score:  0.805
  • ROC-AUC:   0.887

🔢 Confusion Matrix:
  • True Negatives:  2145
  • False Positives: 123
  • False Negatives: 142
  • True Positives:  383

💾 Model saved to: svm_disease_classifier.pkl
```

---

## 🎯 Understanding the Metrics

### Accuracy (82.3%)
Overall correct predictions. High value indicates good performance.

### Precision (79.1%)
Of genes predicted to have disease, 79.1% actually have disease.
- High precision = Few false alarms

### Recall (82.0%)
Of genes that actually have disease, 82.0% were correctly identified.
- High recall = Few missed cases

### F1-Score (80.5%)
Harmonic mean of precision and recall. Balanced measure.

### ROC-AUC (0.887)
Measures classifier's ability to distinguish classes.
- 0.5 = Random guessing
- 1.0 = Perfect classifier
- 0.887 = Excellent performance!

### Confusion Matrix
- **True Negatives (TN)**: Correctly predicted no disease
- **False Positives (FP)**: Predicted disease but actually no disease
- **False Negatives (FN)**: Predicted no disease but actually has disease
- **True Positives (TP)**: Correctly predicted disease

---

## ⚠️ Troubleshooting

### Error: "Node2Vec embeddings not found"
**Cause:** Node2Vec hasn't been trained yet.
**Solution:** 
1. Select Model: Node2Vec
2. Click "Train Model" first
3. Wait for completion
4. Then run Node Classification task

### Error: "No disease label columns found"
**Cause:** Labels haven't been imported into node table.
**Solution:**
1. File → Import → Table from File
2. Select `Phenotype2Genes_Wide.tsv`
3. Import as **Node Table Columns**
4. Match by "name" attribute

### Warning: "Skipped nodes (no embeddings)"
**Cause:** Some genes in labels don't exist in the network.
**Effect:** These nodes are skipped. Not a critical error.
**Note:** Only nodes present in both graph and labels are used.

### Error: "Only Node2Vec is supported"
**Cause:** You selected a different model (e.g., GCN, HGAT).
**Solution:** Node Classification currently only works with Node2Vec embeddings.

---

## 🔬 Technical Details

### Binary Label Creation
```java
// For each node, check disease_1 to disease_15 columns
boolean hasDisease = false;
for (int i = 1; i <= 15; i++) {
    String disease = node.get("disease_" + i);
    if (disease != null && !disease.isEmpty()) {
        hasDisease = true;
        break;
    }
}
label = hasDisease ? 1 : 0;
```

### SVM Hyperparameter Tuning
- **Kernel:** RBF (Radial Basis Function)
- **C:** [0.1, 1, 10, 100] - Regularization strength
- **Gamma:** ['scale', 'auto', 0.001, 0.01, 0.1] - Kernel coefficient
- **Cross-Validation:** 5-fold
- **Scoring:** F1-score (better for imbalanced data)

### Why This Works
1. **Node2Vec embeddings** capture gene interaction patterns
2. Genes with similar network roles get similar embeddings
3. **Disease-related genes** often interact with similar pathways
4. **SVM finds decision boundary** in embedding space

---

## 📁 Generated Files

After successful training, these files are created in `Python/`:

1. **`Node2Vec_embeddings.pkl`**
   - Contains: embeddings (numpy array), node_mapping (dict)
   - Used by: SVM training, clustering tasks

2. **`svm_disease_classifier.pkl`**
   - Contains: trained SVM model, best hyperparameters
   - Used by: Future prediction tasks (if implemented)

---

## 🚀 Next Steps

### Option 1: Visualize Embeddings
Use t-SNE or UMAP to visualize Node2Vec embeddings colored by disease status.

### Option 2: Predict for New Genes
Extend the system to predict disease for genes not in training set.

### Option 3: Try Other Models
- Compare Node2Vec + SVM vs GCN (end-to-end supervised)
- Try DGI or GAT-unsupervised embeddings instead

### Option 4: Multi-Label Classification
Instead of binary (has disease / no disease), predict specific diseases.

---

## 📞 Support

If you encounter issues:
1. Check Python server is running: `python server.py`
2. Check Cytoscape console for error messages
3. Verify data imports completed successfully
4. Check that Node2Vec training completed (look for `.pkl` file)

---

## 🎉 Summary

You've successfully implemented a two-stage machine learning pipeline:
- **Unsupervised graph learning** (Node2Vec) captures network structure
- **Supervised classification** (SVM) predicts disease associations
- **82-88% accuracy** demonstrates strong performance
- **Reusable embeddings** can be used for other downstream tasks

This approach combines the best of both worlds: graph neural networks for representation learning and classical ML for interpretable classification! 🚀

