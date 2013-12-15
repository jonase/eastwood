#! /bin/bash

# diff all project.clj files in repos against what is in the
# corresponding project-clj-files subdirectory.

cd repos
for dir in *
do
    if [ -d $dir ]
    then
	cd $dir
	set -x
	diff project.clj ../../project-clj-files/$dir/project.clj
	set +x
	cd ..
    fi
done
