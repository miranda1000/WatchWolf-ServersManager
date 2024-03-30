#!/bin/bash

function getAllVersions {
	# 'https://hub.spigotmc.org/versions' contains all the version files
	curl -s https://hub.spigotmc.org/versions/ | grep -o -P '1\.\d+(\.\d+)?(?=\.json)' | sort --reverse --version-sort --field-separator=. | uniq -d
}

# @param server_version
function get_java_version {
	case `echo "$1" | grep -o -P '^\d+\.\d+'` in # get the first 2 numbers
		"1.20" | "1.19" | "1.18" )
			return 17
			;;
		"1.17" )
			return 16
			;;
		* ) # previous to 1.17
			return 8
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
