# SafeResale ML-M2 environment — TensorFlow 2.21 (GPU) + Keras 3 for condition classification.
# Based on the official TF GPU image (ships CUDA 12.3 + cuDNN — no manual CUDA setup).
# GPU needs the NVIDIA Container Toolkit on the host (Docker Desktop: WSL2 GPU + nvidia runtime).
#
# Build (from repo root):
#   docker build -f ml/Dockerfile.m2 -t saferesale/ml-m2:latest .
# Run (examples in ml/m2_condition_classification/README.md):
#   docker compose -f ml/docker-compose.yml run --rm m2           # TF/GPU check
#   docker compose -f ml/docker-compose.yml run --rm m2-smoke     # 1-epoch smoke test
#   docker compose -f ml/docker-compose.yml run --rm m2-train     # baseline + improved

FROM tensorflow/tensorflow:2.21.0-gpu

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    M2_WORK_ROOT=/ml/work

WORKDIR /ml

# yaml (train/prepare configs) + matplotlib (confusion-matrix PNG in evaluate.py)
RUN pip install --upgrade pip && pip install pyyaml matplotlib

# Scripts + configs for every model (data/weights/runs stay out of the image — see volumes)
COPY ml/ /ml/
WORKDIR /ml/m2_condition_classification

# Default: verify TF + GPU inside the container.
ENTRYPOINT ["python"]
CMD ["-c", "import tensorflow as tf; gpus = tf.config.list_physical_devices('GPU'); print('tensorflow', tf.__version__, '| gpu:', len(gpus) > 0, '|', [g.name for g in gpus])"]
