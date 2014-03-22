#! /bin/bash

# Do some command in all cloned repo directories, e.g.

# ./do-all-libs.sh git status .
# ./do-all-libs.sh git pull

cd repos
for repo in *
do
    echo
    echo "=== $repo"
    cd $repo
    $*
    cd ..
done
