#! /bin/bash

# Only intended for use if you have updated one or more project.clj
# files in the project-clj-files subdirectory, and want to make all of
# the project.clj files in the corresponding repos subdirectory match.

set -x
cd repos
for dir in *
do
    cd $dir
    cp ../../project-clj-files/$dir/project.clj project.clj
    cd ..
done
