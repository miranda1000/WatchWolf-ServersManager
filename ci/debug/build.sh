#!/bin/bash

# default variables
preclean=0
test=0

# parse params
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --preclean) preclean=1 ;;
        --test) test=1 ;;
        
        *) echo "[e] Unknown parameter passed: $1" >&2 ; exit 1 ;;
    esac
    shift
done

if [ $test -eq 1 ]; then
    # check for file dependencies
    if [ `ls . | grep -c -P 'watchwolf-server-[\d\.]+\.jar'` -ne 1 ]; then
        echo "[e] Make sure to have the WW-Server .jar in the current directory (and only one instance). The name must match 'watchwolf-server-<version>'"
        exit 1
    fi

    # create dependent folders
    mkdir server-types 2>/dev/null
    mkdir usual-plugins 2>/dev/null
    mkdir tmp 2>/dev/null

    if [ `ls -l server-types 2>&1 | grep -c '^d'` -eq 0 ]; then
        echo "[w] You don't have any server type in the folder. To get the default server types check the following link:"
        echo "https://github.com/watch-wolf/WatchWolf/blob/main/WatchWolfSetup.sh"
    fi
fi

# compile latest ServersManager
echo "[v] Compiling ServersManager..."
script_path=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
base_path=$(dirname $(dirname "$script_path"))
local_maven_repos_path="$HOME/.m2"
if [ $preclean -eq 1 ]; then
    docker run -it --rm -v "$base_path":"/compile" -v "$local_maven_repos_path":/root/.m2 maven:3.8.3-openjdk-17 mvn clean --file '/compile' # clean project & launch "clean" phase (if any)
fi
docker run -it --rm -v "$base_path":"/compile" -v "$local_maven_repos_path":/root/.m2 maven:3.8.3-openjdk-17 mvn compile assembly:single -Dmaven.test.skip=true --file '/compile'

if [ $? -ne 0 ]; then
    echo "[e] Exception while compiling WW-ServersManager"
    exit 1
fi

if [ $test -eq 1 ]; then
    # all dependencies done; run
    # copy WW-Server as a usual plugin
    echo "[v] Moving WW-Server to the 'usual plugins' folder..."
    version=`ls . | grep -o -P '(?<=watchwolf-server-)[\d\.]+(?=\.jar)'`
    cp "watchwolf-server-$version.jar" "usual-plugins/WatchWolf-$version-1.8-LATEST.jar"

    # copy WW-ServersManager
    echo "[v] Preparing WW-ServersManager jar file..."
    version=`ls '../../target' | grep -o -P '(?<=watchwolf-servers-manager-)[\d\.]+(?=\.jar)'`
    cp "../../target/watchwolf-servers-manager-$version.jar" ./ServersManager.jar

    # build the docker
    echo "[v] Building Docker container..."
    docker compose build --no-cache
fi
