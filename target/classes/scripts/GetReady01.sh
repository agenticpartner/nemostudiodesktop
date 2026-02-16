#!/bin/bash
# All file/OS actions in this script; REMOTE_PATH may be set by the caller to run in that directory
if [ -n "${REMOTE_PATH:-}" ]; then
  cd "$REMOTE_PATH" || { echo "Failed to cd to remote path: $REMOTE_PATH"; exit 1; }
  echo "Working in: $REMOTE_PATH"
fi

docker pull nvcr.io/nvidia/nemo-curator:25.09
docker run --gpus all -it --rm nvcr.io/nvidia/nemo-curator:25.09

# Create data folders if they do not exist
if [ ! -d "data/sample" ]; then
  mkdir -p data/sample
  echo "Created data/sample"
else
  echo "data/sample already exists"
fi

if [ ! -d "data/curated" ]; then
  mkdir -p data/curated
  echo "Created data/curated"
else
  echo "data/curated already exists"
fi