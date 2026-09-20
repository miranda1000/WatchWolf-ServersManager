#!/bin/sh
set -eu

script_path=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

# Install download and parsing tools in a disposable container.
docker run --rm -i --env HOME=/tmp \
    --env BUILD_UID="$(id -u)" --env BUILD_GID="$(id -g)" \
    --volume "$script_path:/prepare" \
    --workdir /prepare --entrypoint /bin/bash \
    ubuntu:24.04 -c '
        set -e
        apt-get update
        apt-get install -y --no-install-recommends ca-certificates coreutils grep jq wget util-linux
        # Write artifacts as the invoking user after installing tools as root.
        exec setpriv --reuid "$BUILD_UID" --regid "$BUILD_GID" --clear-groups \
            /bin/bash -s -- "$@"
    ' bash "$@" <<'PREPARE'
#!/bin/bash
set -e

# Runs inside the temporary container, with ci/release as the working directory.

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
mkdir -p tmp

if [ `ls -l server-types 2>&1 | grep -c '^d'` -eq 0 ]; then
    echo "[w] You don't have any server type in the folder. To get the default server types check the following link:"
    echo "https://github.com/watch-wolf/WatchWolf/blob/main/WatchWolfSetup.sh"
fi

# WatchWolf Server as usual-plugins: check the latest release on every build.
watchwolf_server_versions_base_path="https://watchwolf.dev/versions"
if ! web_contents=$(wget --no-check-certificate --max-redirect=2 --timeout=30 --tries=2 -q -O - "$watchwolf_server_versions_base_path"); then
    echo "[e] Could not fetch WatchWolf-Server versions." >&2
    exit 1
fi
watchwolf_server_files=$(printf '%s\n' "$web_contents" | grep -o -P 'WatchWolf-[\d.]+-[\d.]+-LATEST\.jar' | sort -u)
higher_version=$(printf '%s\n' "$watchwolf_server_files" | grep -o -P '(?<=WatchWolf-)[\d.]+(?=-)' | sort --reverse --version-sort | head -1)
if [ -z "$higher_version" ]; then
    echo "[e] No WatchWolf-Server jars found in the versions listing." >&2
    exit 1
fi

echo "[v] Highest WW-Server version: $higher_version"
higher_version_file=$(printf '%s\n' "$watchwolf_server_files" | grep -F "WatchWolf-$higher_version-")
destination="usual-plugins/$higher_version_file"
if [ -s "$destination" ]; then
    echo "[v] Already downloaded: $higher_version_file"
else
    echo "[v] Downloading $higher_version_file..."
    # Publish only complete downloads, so an interrupted build cannot leave a broken jar.
    download_tmp=$(mktemp "usual-plugins/.watchwolf-download.XXXXXX") || exit 1
    trap 'rm -f "$download_tmp"' EXIT
    if ! wget --no-check-certificate --max-redirect=2 --timeout=30 --tries=2 \
            "$watchwolf_server_versions_base_path/$higher_version_file" -O "$download_tmp" \
            || [ ! -s "$download_tmp" ]; then
        echo "[e] Could not download $higher_version_file." >&2
        exit 1
    fi
    mv "$download_tmp" "$destination" || exit 1
    trap - EXIT
fi

# download the latest ServersManager
release_metadata=$(wget --timeout=30 --tries=2 -q -O - 'https://api.github.com/repos/miranda1000/WatchWolf-ServersManager/releases/latest')
latest_program_url=$(printf '%s\n' "$release_metadata" | jq -er '[.assets[] | select(.name | endswith(".jar")) | .browser_download_url] | if length == 1 then .[0] else error("Expected one ServersManager jar") end')
download_tmp=$(mktemp .servers-manager-download.XXXXXX)
trap 'rm -f "$download_tmp"' EXIT
wget --timeout=30 --tries=2 -O "$download_tmp" "$latest_program_url"
test -s "$download_tmp"
mv "$download_tmp" ServersManager.jar
trap - EXIT

PREPARE

echo "[v] Building Docker container..."
docker build --tag servers-manager --no-cache "$script_path"
