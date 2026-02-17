#!/bin/bash
# All file/OS actions in this script; REMOTE_PATH may be set by the caller to run in that directory
if [ -n "${REMOTE_PATH:-}" ]; then
  cd "$REMOTE_PATH" || { echo "Failed to cd to remote path: $REMOTE_PATH"; exit 1; }
  echo "Working in: $REMOTE_PATH"
fi

# If example.zip was uploaded (e.g. from JAR), unzip into example/ and remove the zip
if [ -f "example.zip" ]; then
  echo "Unzipping example.zip into example/..."
  unzip -o example.zip -d example
  rm -f example.zip
  echo "example.zip extracted and removed."
fi

docker pull nvcr.io/nvidia/nemo-curator:25.09

# Stop and remove all running containers using nvcr.io/nvidia/nemo-curator:25.09 image
echo "Checking for existing containers with nvcr.io/nvidia/nemo-curator:25.09..."
EXISTING_CONTAINERS=$(docker ps --filter "ancestor=nvcr.io/nvidia/nemo-curator:25.09" --format "{{.ID}}")
if [ -n "$EXISTING_CONTAINERS" ]; then
  echo "Found existing containers, stopping and removing them..."
  echo "$EXISTING_CONTAINERS" | while read -r container_id; do
    if [ -n "$container_id" ]; then
      echo "Stopping container: $container_id"
      docker stop "$container_id" 2>/dev/null || true
      echo "Removed container: $container_id"
    fi
  done
else
  echo "No existing containers found with nvcr.io/nvidia/nemo-curator:25.09"
fi

# Ensure REMOTE_PATH is absolute for Docker volume mount
if [ -n "${REMOTE_PATH:-}" ]; then
  REMOTE_PATH=$(cd "$REMOTE_PATH" && pwd)
  VOLUME_MOUNT="-v $REMOTE_PATH:/workspace"
  echo "Mounting $REMOTE_PATH to /workspace in container"
else
  VOLUME_MOUNT=""
  echo "WARNING: REMOTE_PATH not set, container will not have volume mount"
fi

CONTAINER_ID=$(docker run -d --gpus all --rm $VOLUME_MOUNT nvcr.io/nvidia/nemo-curator:25.09 tail -f /dev/null)

if [ -z "$CONTAINER_ID" ]; then
  echo "Failed to create container"
  exit 1
fi

echo "Container created: $CONTAINER_ID"
echo "Verifying mount..."

# Wait a moment for container to start
sleep 2

# Check if /workspace exists and list its contents
if docker exec "$CONTAINER_ID" test -d /workspace; then
  echo "Mount verified: /workspace exists in container"
  echo "Contents of /workspace in container:"
  docker exec "$CONTAINER_ID" ls -la /workspace
  echo ""
  echo "Expected path on host: $REMOTE_PATH"
  echo "Mount point in container: /workspace"
else
  echo "ERROR: /workspace does not exist in container!"
  docker exec "$CONTAINER_ID" ls -la /
fi
