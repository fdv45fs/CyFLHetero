# 🚀 Quick Setup Guide

## Chọn phiên bản phù hợp

### 📌 Bạn có GPU NVIDIA? 

**CÓ GPU + CUDA 11.8** → Dùng `requirements_gpu.txt`  
**KHÔNG có GPU** → Dùng `requirements_cpu.txt`

---

## 🖥️ Cài đặt cho GPU (CUDA 11.8)

### Bước 1: Cài base packages
```bash
cd D:\CyFLHetero\Python
pip install Flask scikit-learn
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
pip install torch_geometric
```

### Bước 2: Cài torch-cluster (REQUIRED cho Node2Vec)
```bash
pip install torch-cluster -f https://data.pyg.org/whl/torch-2.7.0+cu118.html
```

### Bước 3: Verify
```bash
python -c "import torch; print(f'PyTorch: {torch.__version__}')"
python -c "import torch; print(f'CUDA: {torch.cuda.is_available()}')"
python -c "import torch_cluster; print('✅ torch-cluster OK')"
python -c "from torch_geometric.nn import Node2Vec; print('✅ Node2Vec OK')"
```

---

## 💻 Cài đặt cho CPU

### Bước 1: Cài base packages
```bash
cd D:\CyFLHetero\Python
pip install Flask scikit-learn
pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu
pip install torch_geometric
```

### Bước 2: Cài torch-cluster (REQUIRED cho Node2Vec)
```bash
pip install torch-cluster -f https://data.pyg.org/whl/torch-2.7.0+cpu.html
```

### Bước 3: Verify
```bash
python -c "import torch; print(f'PyTorch: {torch.__version__}')"
python -c "import torch_cluster; print('✅ torch-cluster OK')"
python -c "from torch_geometric.nn import Node2Vec; print('✅ Node2Vec OK')"
```

---

## 🚀 Start Server

```bash
python server.py
```

Hoặc

```bash
python serverDetero.py
```

Xong! 🎉

---

## Troubleshooting

**Lỗi: "Could not find a version..."**
→ Thử: `pip install torch-cluster -f https://data.pyg.org/whl/`

**Lỗi: "No matching distribution..."**
→ Check Python version: `python --version` (cần 3.8-3.11)

**Lỗi: Compiler errors**
→ Dùng pre-built wheels: `pip install torch-cluster -f https://data.pyg.org/whl/`

