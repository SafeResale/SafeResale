# SafeResale ML environment — torch (CUDA 12.4) + ultralytics + roboflow
# Teammates pull this image and never need to install Python/torch/CUDA themselves.
# GPU needs the NVIDIA Container Toolkit on the host (Docker Desktop: enable WSL2 GPU).
#
# Build (from repo root):
#   docker build -f ml/Dockerfile -t saferesale/ml:latest .
# Run (examples in docs/10-setup-guide.md §8.2):
#   docker compose -f ml/docker-compose.yml run --rm ml python scripts/smoke_test.py

FROM python:3.12-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    TMPDIR=/tmp \
    ML_HOME=/ml

# opencv/ultralytics runtime libs
# NOTE: libgl1-mesa-glx is not available on Debian bookworm (python:3.12-slim); libgl1 is the modern equivalent.
RUN apt-get update && apt-get install -y --no-install-recommends \
        libgl1 libglib2.0-0 libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /ml

# CUDA build of torch first, then the rest (see ml/requirements.txt)
COPY ml/requirements.txt requirements.txt
RUN pip install --upgrade pip \
    && pip install torch==2.6.0 torchvision==0.21.0 \
       --index-url https://download.pytorch.org/whl/cu124 \
    && pip install -r requirements.txt

# Scripts + configs for every model (data/weights/runs stay out of the image — see volumes)
COPY ml/ /ml/
WORKDIR /ml/m1_defect_detection

# Default: run whatever is passed (train / infer / smoke). Interactive shell otherwise.
ENTRYPOINT ["python"]
CMD ["-c", "import torch; print('torch', torch.__version__, '| cuda:', torch.cuda.is_available())"]
