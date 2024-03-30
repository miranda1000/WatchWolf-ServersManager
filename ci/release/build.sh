#!/bin/bash

# check for dependencies
if [ ! -d "server-types" ]; then
    echo "[e] 'server-types' folder missing. Did you run the install?"
    exit 1
fi
if [ ! -d "usual-plugins" ]; then
    echo "[e] 'usual-plugins' folder missing. Did you run the install?"
    exit 1
fi

# create dependent folders
mkdir tmp 2>/dev/null

if [ `ls -l server-types 2>&1 | grep -c '^d'` -eq 0 ]; then
    echo "[w] You don't have any server type in the folder. To get the default server types check the following link:"
    echo "https://github.com/watch-wolf/WatchWolf/blob/main/WatchWolfSetup.sh"
fi

# download the latest ServersManager
# TODO

# build the docker
echo "[v] Building Docker container..."
docker compose build --no-cache