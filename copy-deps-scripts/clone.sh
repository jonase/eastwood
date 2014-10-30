#! /bin/bash

set -ex

mkdir -p repos
cd repos

git clone https://github.com/clojure/tools.analyzer.git
cd tools.analyzer
#git checkout tools.analyzer-0.6.2
# One more commit after 0.6.2
git checkout 8ef610c0991ab58bcef4964e6cfad969c79d625c
cd ..

git clone https://github.com/clojure/tools.analyzer.jvm.git
cd tools.analyzer.jvm
#git checkout tools.analyzer.jvm-0.6.3
# A few commits after 0.6.3
git checkout c72d5c9407a5e25e8bd01fb2518521341856c059
cd ..

git clone https://github.com/clojure/core.memoize.git
cd core.memoize
git checkout core.memoize-0.5.6
patch -p1 < ../../core.memoize-fix-reflection-warning.patch
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
#git checkout tools.namespace-0.2.7
# One more commit after 0.2.7
git checkout 122e3d1d4fb01e4f1412d5cc7ec80dce76e8778a
cd ..

git clone https://github.com/clojure/tools.reader.git
cd tools.reader
git checkout tools.reader-0.8.10
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
