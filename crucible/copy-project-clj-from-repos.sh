#! /bin/bash

# Only intended for use if you have updated one or more project.clj
# files in the repos subdirectory, and want to make all of the
# project.clj files in the corresponding project-clj-files
# subdirectory match.

set -x
cd repos
for dir in *
do
    cd $dir
    mkdir -p ../../project-clj-files/$dir
    cp project.clj ../../project-clj-files/$dir/
    cd ..
done
