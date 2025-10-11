#!/bin/bash

function getAllVersions {
	# 'https://hub.spigotmc.org/versions' contains all the version files
	curl -s https://hub.spigotmc.org/versions/ | grep -o -P '1\.\d+(\.\d+)?(?=\.json)' | sort --reverse --version-sort --field-separator=. | uniq -d
}

# Determines the required Java version for a given Minecraft server version.
# @param $1 Minecraft server version (e.g. "1.20.6", "1.17", "1.8.8")
# @return Java major version number (8, 16, 17, 21)
function get_java_version {
    local server_version="$1"
    local major_minor
    major_minor=$(echo "$server_version" | grep -o -E '^[0-9]+\.[0-9]+')

    case "$major_minor" in
        "1.20" )
            # Check if it's 1.20.5 or newer
            local patch
            patch=$(echo "$server_version" | grep -o -E '^1\.20\.([0-9]+)' | cut -d. -f3)
            if [[ -n "$patch" && "$patch" -ge 5 ]]; then
                return 21
            else
                return 17
            fi
            ;;
        "1.19" | "1.18" )
            return 17
            ;;
        "1.17" )
            return 16
            ;;
        # Anything older than 1.17
        "1.16" | "1.15" | "1.14" | "1.13" | "1.12" | "1.11" | "1.10" | "1.9" | "1.8" | "1.7" )
            return 8
            ;;
        # newest
        * )
            return 21
            ;;
    esac
}


# TODO check if already updated

# @param absolute_copy_path
# @param version (from getAllVersions)
function buildVersion {
	mc_version="$2"
	get_java_version "$mc_version"
	java_version="$?"
	
	pre_cmd=":" # NOP; in Java 8 you need to use 'apt-get', and git is already installed
	if [ $java_version -ne 8 ]; then
		# TODO use an image with git already installed
		pre_cmd="microdnf install git" # in Java 16-17 git is not installed
	fi
	
	cmd="$pre_cmd; mkdir BuildTools; cd BuildTools; curl -z BuildTools.jar -o BuildTools.jar https://hub.spigotmc.org/jenkins/job/BuildTools/lastSuccessfulBuild/artifact/target/BuildTools.jar && java -jar BuildTools.jar --rev $mc_version && cp spigot-$mc_version.jar /Versions/$mc_version.jar"
	sudo docker run -i --rm --detach --name "Spigot_build_$2" -v "$1":/Versions "openjdk:$java_version" /bin/bash -c "$cmd"
}
