#! /bin/bash

script_fullpath_dir="$1"
clone_fullpath_dir="$2"

echo "Apply core.match patch for ticket MATCH-124 ..."

cd "${clone_fullpath_dir}"
patch -p1 < "${script_fullpath_dir}/fix-for-clj190.patch"
