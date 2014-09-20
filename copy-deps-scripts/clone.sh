#! /bin/bash

set -ex

mkdir -p repos
cd repos

git clone https://github.com/clojure/tools.analyzer.git
cd tools.analyzer
git checkout tools.analyzer-0.6.0

cd ..

git clone https://github.com/clojure/tools.analyzer.jvm.git
cd tools.analyzer.jvm
# This is a commit shortly after tools.analyzer.jvm-0.6.0 that
# contains a fix that will hopefully avoid some NullPointerException
# that were occurring with proxy expressions.
git checkout cef273d52876b4e76cf9f4c47c0d0c66aa2aabd2
cd ..

git clone https://github.com/clojure/core.memoize.git
cd core.memoize
git checkout core.memoize-0.5.6
cd ..

git clone https://github.com/clojure/core.cache.git
cd core.cache
git checkout core.cache-0.6.3
cd ..

git clone https://github.com/clojure/data.priority-map.git
cd data.priority-map
git checkout data.priority-map-0.0.5
cd ..

git clone https://github.com/clojure/tools.namespace.git
cd tools.namespace
git checkout tools.namespace-0.2.6
cd ..

git clone https://github.com/clojure/tools.reader.git
cd tools.reader
git checkout tools.reader-0.8.4
cd ..

git clone https://github.com/sattvik/leinjacker.git
cd leinjacker
git checkout v0.4.1
cd ..

git clone https://github.com/clojure/core.contracts
cd core.contracts
git checkout core.contracts-0.0.1
cd ..

git clone https://github.com/clojure/core.unify
cd core.unify
git checkout core.unify-0.5.3
cd ..

for j in *
do
    cd $j
    echo "=== $j"
    git status .
    cd ..
done
