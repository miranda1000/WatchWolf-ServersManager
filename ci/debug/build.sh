#!/bin/sh
set -eu

script_path=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
base_path=$(CDPATH= cd -- "$script_path/../.." && pwd)

# Only Docker and standard shell utilities are needed on the host.
mkdir -p "$HOME/.m2"
docker run --rm -i --user "$(id -u):$(id -g)" --env HOME=/tmp \
    --volume "$base_path:/compile" \
    --volume "$HOME/.m2:/maven-cache" \
    --workdir /compile/ci/debug --entrypoint /bin/bash \
    maven:3.9-eclipse-temurin-17 -s -- "$@" <<'PREPARE'
#!/bin/bash
set -euo pipefail

# Runs inside the temporary container, with ci/debug as the working directory.
preclean=0
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --preclean) preclean=1 ;;
        *) echo "[e] Unknown parameter passed: $1" >&2; exit 1 ;;
    esac
    shift
done

shopt -s nullglob
server_jars=(watchwolf-server-*.jar)
if [[ ${#server_jars[@]} -ne 1 || ! ${server_jars[0]} =~ ^watchwolf-server-([0-9.]+)\.jar$ ]]; then
    echo "[e] Put exactly one watchwolf-server-<version>.jar in ci/debug." >&2
    exit 1
fi
server_version=${BASH_REMATCH[1]}

mkdir -p server-types usual-plugins tmp
server_types=(server-types/*/)
if [[ ${#server_types[@]} -eq 0 ]]; then
    echo "[w] You don't have any server type in the folder. To get the default server types check:"
    echo "https://github.com/watch-wolf/WatchWolf/blob/main/WatchWolfSetup.sh"
fi

echo "[v] Compiling ServersManager..."
if [[ $preclean -eq 1 ]]; then
    # Install the local Core jar before Maven resolves compile dependencies.
    mvn --batch-mode --file /compile -Dmaven.repo.local=/maven-cache/repository clean
fi
mvn --batch-mode --file /compile -Dmaven.repo.local=/maven-cache/repository \
    compile assembly:single -Dmaven.test.skip=true

echo "[v] Moving WW-Server to the 'usual plugins' folder..."
cp "${server_jars[0]}" "usual-plugins/WatchWolf-$server_version-1.8-LATEST.jar"

echo "[v] Preparing WW-ServersManager jar file..."
manager_jars=(/compile/target/watchwolf-servers-manager-*.jar)
if [[ ${#manager_jars[@]} -ne 1 ]]; then
    echo "[e] Expected exactly one ServersManager jar in target; try --preclean." >&2
    exit 1
fi
cp "${manager_jars[0]}" ServersManager.jar
PREPARE

echo "[v] Building Docker container..."
docker build --tag servers-manager --no-cache "$script_path"
