#!/bin/bash

function getAllPaperVersions {
	# 'https://api.papermc.io/v2/projects/paper/' contains a JSON with all the MC versions
	curl -s https://api.papermc.io/v2/projects/paper/ | jq -c '.versions | .[]' | grep -o -P '(?<=")1\.\d+(\.\d+)?(?=")' | sort --reverse --version-sort --field-separator=.
}

# @param absolute_copy_path
# @param version (from getAllVersions)
function buildPaperVersion {
	mc_version="$2"
	
	# 'https://api.papermc.io/v2/projects/paper/versions/<mc_version>/builds/' contains all the builds from that version
	# It returns a JSON with the last element being the most recent. We'll want the build number and the file name to get the download link.
	base_path="https://api.papermc.io/v2/projects/paper/versions/$mc_version/builds/"
	download_path=`curl -s "$base_path" | jq --raw-output '.builds[-1] | .build,.downloads.application.name' | awk -v base="$base_path" '{build=$0; getline; print base build "/downloads/" $0;}'`
	
	# Paper is already built
	wget -O "$1/$mc_version.jar" "$download_path"
}
