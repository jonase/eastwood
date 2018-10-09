#! /bin/bash

script_fullpath_dir="$1"
clone_fullpath_dir="$2"

echo "Apply patch to core.rrb-vector to bring namespace up to date with collection-check dependency in project.clj ..."

cd "${clone_fullpath_dir}"
patch -p1 < "${script_fullpath_dir}/fix-for-later-collection-check-dep.patch"
