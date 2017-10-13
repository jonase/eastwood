#! /bin/bash

script_fullpath_dir="$1"
clone_fullpath_dir="$2"

echo "Apply patch that corrects a namespace name to correspond with its file name ..."

cd "${clone_fullpath_dir}"
patch -p1 < "${script_fullpath_dir}/fix-newrelic-ns-name.patch"
