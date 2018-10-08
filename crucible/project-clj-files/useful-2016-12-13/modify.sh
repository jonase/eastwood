#! /bin/bash

script_fullpath_dir="$1"
clone_fullpath_dir="$2"

echo "Apply patch to useful to avoid error with Clojure 1.9+ ..."

cd "${clone_fullpath_dir}"
patch -p1 < "${script_fullpath_dir}/fix-for-clojure-1.9.patch"
