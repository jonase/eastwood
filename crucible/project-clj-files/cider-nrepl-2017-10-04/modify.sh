#! /bin/bash

script_fullpath_dir="$1"
clone_fullpath_dir="$2"

echo "Apply patch to use clojure.spec.alpha instead of clojure.spec ..."

cd "${clone_fullpath_dir}"
patch -p1 < "${script_fullpath_dir}/replace-clojure.spec-with-alpha.patch"
