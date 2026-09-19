#!/bin/bash

# some utilities
script_path=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# parse params
force_recreate=0
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --force-recreate) force_recreate=1 ;;
        *) echo "[e] Unknown parameter passed: $1" >&2 ; exit 1 ;;
    esac
    shift
done

# build if needed
if [ $force_recreate -eq 1 ] || ! docker image inspect servers-manager >/dev/null 2>&1; then
    echo "[v] Building Docker image..."
    docker build --tag servers-manager .
fi

# run
echo "[v] Running..."
export MACHINE_IP=$(hostname -I | awk '{print $1}')
export PUBLIC_IP=$(curl ifconfig.me)
export PARENT_PWD="$script_path"
export SERVER_PATH_SHIFT="."

docker run -d --rm --name ServersManager \
    --network host \
    -p 8000:8000 \
    -v "$script_path/server-types:/servers/server-types" \
    -v "$script_path/usual-plugins:/servers/usual-plugins" \
    -v "$script_path/logs:/servers/logs" \
    -v "$script_path/tmp:/servers/tmp" \
    -v /var/run/docker.sock:/var/run/docker.sock \
    --env MACHINE_IP="$MACHINE_IP" \
    --env PUBLIC_IP="$PUBLIC_IP" \
    --env PARENT_PWD="$PARENT_PWD" \
    --env SERVER_PATH_SHIFT="$SERVER_PATH_SHIFT" \
    servers-manager
