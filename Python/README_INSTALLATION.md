# 📦 Installation Guide

## 📁 Files Overview

- **`SETUP.md`** - Chi tiết hướng dẫn cài đặt step-by-step ⭐
- **`requirements_gpu.txt`** - Cho hệ thống có GPU NVIDIA (CUDA 11.8) 🎮
- **`requirements_cpu.txt`** - Cho hệ thống không có GPU 💻
- **`requirements.txt`** - File cơ bản (không đầy đủ)

---

## 🚀 Quick Start

### Bạn có GPU NVIDIA CUDA 11.8?

**✅ CÓ** → Xem hướng dẫn trong `requirements_gpu.txt`  
**❌ KHÔNG** → Xem hướng dẫn trong `requirements_cpu.txt`

Hoặc đọc `SETUP.md` để có hướng dẫn đầy đủ!

---

## ⚡ One-liner (Cho GPU CUDA 11.8)

```bash
cd D:\CyFLHetero\Python
pip install Flask scikit-learn && pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118 && pip install torch_geometric && pip install torch-cluster -f https://data.pyg.org/whl/torch-2.7.0+cu118.html
```

---

## ⚡ One-liner (Cho CPU)

```bash
cd D:\CyFLHetero\Python
pip install Flask scikit-learn && pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu && pip install torch_geometric && pip install torch-cluster -f https://data.pyg.org/whl/torch-2.7.0+cpu.html
```

---

## ✅ Verify Installation

```bash
python -c "import torch; print('PyTorch:', torch.__version__)"
python -c "import torch_cluster; print('torch-cluster: OK')"
python -c "from torch_geometric.nn import Node2Vec; print('Node2Vec: READY!')"
```

**Expected output:**
```
PyTorch: 2.7.0+cu118
torch-cluster: OK
Node2Vec: READY!
```

---

## 🎯 Then Run Server

```bash
python server.py
```

Or for heterogeneous networks:

```bash
python serverDetero.py
```

Done! 🎉

