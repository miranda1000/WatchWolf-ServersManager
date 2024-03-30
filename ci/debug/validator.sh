#!/bin/bash

error=0

script_path=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
base_path=$(dirname $(dirname "$script_path"))
unit_tests_path="$base_path/src/test/java"
integration_tests_path="$base_path/src/integration-test/java"

# are unit tests following naming standard?
warning_files=`find "$unit_tests_path" -type f ! -name '*Should.java'` # every java file that don't end with "Should"
if [ ! -z "$warning_files" ]; then
    echo "[w] Some files are not following the unit test naming convention. Any unit test that don't end with 'Should' won't run."
    echo "[v] Files that don't follow the convention:"
    echo "$warning_files"
    error=1
fi

# are unit tests following other naming standard?
warning_files=`find "$unit_tests_path" -type f -name 'IT*.java'` # every java file that start with "IT"
if [ ! -z "$warning_files" ]; then
    echo "[w] Some files are not following the unit test naming convention. Any unit test that start with 'IT' won't run."
    echo "[v] Files that don't follow the convention:"
    echo "$warning_files"
    error=1
fi

# are integration tests following naming standard?
warning_files=`find "$integration_tests_path" -type f ! -name 'IT*.java'` # every java file that don't start with "IT"
if [ ! -z "$warning_files" ]; then
    echo "[w] Some files are not following the system test naming convention. Any system test that don't start with 'IT' won't run."
    echo "[v] Files that don't follow the convention:"
    echo "$warning_files"
    error=1
fi

# does all integration tests have a timeout?
candidates=`find "$integration_tests_path" -type f -name 'IT*.java'`
for candidate in $candidates; do
    # TODO what if commented?
    # TODO what if non-test class?
    # TODO what if multiple classes in same file?
    if [ `grep -Pz -c '@Timeout.*\n.*public class' "$candidate"` -eq 0 ]; then
        echo "[w] Class defined on $candidate don't have timeout set."
        error=1
    fi
done

if [ $error -ne 0 ]; then
    exit $error
fi
echo "[i] All done"