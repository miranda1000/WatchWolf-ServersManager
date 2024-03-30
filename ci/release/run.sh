#!/bin/bash

# some utilities
script_path=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

# run
echo "[v] Running..."
export MACHINE_IP=$(hostname -I | awk '{print $1}') && export PUBLIC_IP=$(curl ifconfig.me) && export PARENT_PWD="$script_path" && export SERVER_PATH_SHIFT="." && docker compose up --no-build --detach
# TODO run `docker compose rm --force` once it finishes