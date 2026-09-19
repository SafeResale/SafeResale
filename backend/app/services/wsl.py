"""WSL bridge for the heavy ML kernels (M1/M2/M6/M3).

The backend process runs on Windows (FastAPI + MongoDB) and cannot import
cv2/torch, whereas the SafeResale models were trained inside WSL where the
deps + weights live (~/safresale-ml/.venv, ~/safresale-ml/m1/runs/...).
Compute is delegated to the WSL Python via the small shims in ml/bridge/:
each reads JSON from stdin and prints JSON on stdout.
"""
from __future__ import annotations

import json
import os
import re
import subprocess
from pathlib import Path

WSL_DISTRO = os.environ.get("WSL_DISTRO", "Ubuntu")
WSL_ML_PY = os.environ.get("WSL_ML_PY", "/home/irahu/safresale-ml/.venv/bin/python")
REPO_ROOT = Path(__file__).resolve().parents[3]  # SafeResale/
BRIDGE_DIR = REPO_ROOT / "ml" / "bridge"


def to_mnt(path: str | Path) -> str:
    """Convert a Windows path (C:\\...) to the WSL mount (/mnt/c/...)."""
    p = str(path).replace("\\", "/")
    if re.match(r"^[A-Za-z]:/", p):
        p = "/mnt/" + p[0].lower() + p[2:]
    return p


def run_bridge(script: str, payload: dict, timeout: int = 300) -> dict | list:
    """Run one bridge shim in WSL and return its parsed JSON output.

    Raises RuntimeError if the subprocess fails or returns non-JSON.
    """
    script_mnt = to_mnt(BRIDGE_DIR / script)
    cmd = [WSL_DISTRO and "wsl" or "wsl", "-d", WSL_DISTRO, "--", WSL_ML_PY, script_mnt]
    body = {**payload, "change_cwd": to_mnt(REPO_ROOT)}
    try:
        proc = subprocess.run(
            cmd,
            input=json.dumps(body).encode("utf-8"),
            capture_output=True,
            timeout=timeout,
        )
    except Exception as exc:
        raise RuntimeError(f"wsl {script} could not run: {exc}") from exc
    stdout = proc.stdout.decode("utf-8", errors="replace")
    stderr = proc.stderr.decode("utf-8", errors="replace")
    if proc.returncode != 0:
        raise RuntimeError(f"wsl {script} failed rc={proc.returncode}: {stderr[-2000:]}")
    try:
        return json.loads(stdout)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"wsl {script} returned invalid JSON: {stdout[:400]}") from exc