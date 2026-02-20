
#DGX OS

#1. Checked the system: nvidia-smi → driver 580.126.09, CUDA 13.0.
#2. Reinstalled PyTorch with CUDA: Uninstalled torch, torchvision, torchaudio, then installed the CUDA 12.4 builds from PyTorch’s index (compatible with your driver):
#   - pip uninstall -y torch torchvision torchaudio
#   - pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu124
#3. Checked again: torch.__version__ is 2.5.1, CUDA compiled: 12.4, and torch.cuda.is_available(): True.
# So the GPU is now detected by PyTorch in your ai env.

#What you should do
#- Use the ai conda environment and run your code as before; it should see the GPU.
#- If you need PyTorch 2.9 with CUDA (instead of 2.5), check pytorch.org for a CUDA 12.4 (or 12.6) install command for your OS and Python version; avoid any install that doesn’t specify a +cu* variant or a CUDA index URL, or you may get the CPU build again.
#- To avoid going back to CPU-only in the future:
#  - Prefer installing with an explicit index, e.g.
#pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu124
#  - Or use the exact command from the PyTorch “Get Started” page for your platform and CUDA version.
#No further troubleshooting is required for “why CUDA was False” — it was due to the CPU-only build; that has been replaced with a CUDA build and verified.

#Driver 580, CUDA 13.0. Installing PyTorch with CUDA support (using CUDA 12.4 wheels; compatible with your driver):
source ~/conda/etc/profile.d/conda.sh 2>/dev/null || true; conda activate ai 2>/dev/null; pip uninstall -y torch torchvision torchaudio 2>/dev/null; pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu130
#Verifying CUDA is detected:
conda run -n ai python -c "import torch; print('Version:', torch.__version__); print('CUDA compiled:', torch.version.cuda); print('cuda.is_available():', torch.cuda.is_available()); print('Device count:', torch.cuda.get_device_count() if torch.cuda.is_available() else 0); print('Device name:', torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'N/A')"


#DGX some container

#1. Host vs container
#The pip install ... --index-url https://download.pytorch.org/whl/cu124 you ran on the host (in your ai env) only affects the host. The NeMo Curator container has its own Python and its own PyTorch, so the GPU is “not detected” there for a different reason.
#2. Image ships CPU-only PyTorch
#   Inside the container we see:
#   - nvidia-smi works → GPU (NVIDIA GB10) and driver are visible.
#   - PyTorch is 2.8.0+cpu (CUDA: None) → the image’s PyTorch is CPU-only, so torch.cuda.is_available() is False regardless of the GPU.
#3. Reinstall must be forced
#   A plain pip install torch ... --index-url cu124 in the container does not replace the preinstalled CPU build; you have to uninstall the existing torch/torchvision/torchaudio and then install from the CUDA index.
#   After uninstall + install from the cu124 index we get:
# - version: 2.5.1, CUDA: 12.4, cuda.is_available(): True, device_name: NVIDIA GB10.
#There is a warning: the standard PyTorch wheel does not list sm_121 (GB10). So the GPU is “detected” and cuda.is_available() is True, but not all ops may use the GPU or be supported; for full GB10 support you’d need a PyTorch build that includes sm_121 (e.g. a newer or NVIDIA-built wheel when available).

# 1) Bootstrap pip if needed (no pip in this image)
curl -sS https://bootstrap.pypa.io/get-pip.py -o /tmp/get-pip.py
python3 /tmp/get-pip.py -q

# 2) Remove CPU-only torch and install CUDA build
python3 -m pip uninstall -y torch torchvision torchaudio
python3 -m pip install --no-cache-dir torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu124

# 3) Verify
python3 -c "import torch; print('cuda.is_available():', torch.cuda.is_available()); print('device:', torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'N/A')"