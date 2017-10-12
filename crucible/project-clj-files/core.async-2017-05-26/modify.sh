#! /bin/bash

script_fullpath_dir="$1"
clone_fullpath_dir="$2"

echo "Apply core.async patch for ticket ASYNC-54 ..."

cd "${clone_fullpath_dir}"
patch -p1 < "${script_fullpath_dir}/0001-Fix-MAX-QUEUE-SIZE-type-hint-def-evaluates-the-metad.patch"
