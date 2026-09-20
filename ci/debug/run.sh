#!/bin/sh
set -eu

# some utilities
script_path=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

# parse params
force_recreate=0
while [ "$#" -gt 0 ]; do
    case "$1" in
        --force-recreate) force_recreate=1 ;;
        *) echo "[e] Unknown parameter passed: $1" >&2 ; exit 1 ;;
    esac
    shift
done

# build if needed
if [ "$force_recreate" -eq 1 ] || ! docker image inspect servers-manager >/dev/null 2>&1; then
    echo "[v] Building Docker image..."
    docker build --tag servers-manager "$script_path"
fi

# Address-discovery tools run in a disposable container, not on the host.
# Host networking makes hostname -I see the manager host's interfaces.
echo "[v] Detecting IP addresses in a temporary Docker container..."
addresses=$(docker run --rm -i --network host \
    --env MACHINE_IP="${MACHINE_IP:-}" --env PUBLIC_IP="${PUBLIC_IP:-}" \
    --entrypoint /bin/bash ubuntu:24.04 -s <<'DETECT_IPS'
set -euo pipefail
if [ -z "$MACHINE_IP" ] || [ -z "$PUBLIC_IP" ]; then
    apt-get update >&2
    apt-get install -y --no-install-recommends ca-certificates curl hostname mawk >&2
fi
if [ -z "$MACHINE_IP" ]; then
    MACHINE_IP=$(hostname -I | awk '{print $1}')
fi
if [ -z "$PUBLIC_IP" ]; then
    PUBLIC_IP=$(curl --fail --silent --show-error --connect-timeout 10 --max-time 30 https://ifconfig.me/ip)
fi
if [ -z "$MACHINE_IP" ] || [ -z "$PUBLIC_IP" ]; then
    echo "[e] Could not determine MACHINE_IP and PUBLIC_IP." >&2
    exit 1
fi
printf '%s %s\n' "$MACHINE_IP" "$PUBLIC_IP"
DETECT_IPS
)
IFS=' ' read -r MACHINE_IP PUBLIC_IP <<ADDRESSES
$addresses
ADDRESSES

# run
echo "[v] Running..."
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
