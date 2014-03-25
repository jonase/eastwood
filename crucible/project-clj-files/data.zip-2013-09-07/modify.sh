#! /bin/bash

script_fullpath_dir="$1"
clone_fullpath_dir="$2"

# Filed a data.zip ticket DZIP-2 suggesting this change
echo "Remove an empty file from data.zip that interferes with Leiningen ..."
/bin/rm -f "${clone_fullpath_dir}/src/test/clojure/clojure/data/zip.clj"
