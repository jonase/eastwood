#! /bin/bash

set -ex

mkdir -p repos
cd repos

git clone https://github.com/clojure/tools.analyzer.git
cd tools.analyzer
#git checkout tools.analyzer-0.6.3
# Several commits after 0.6.3, up through Nov 15 2014
git checkout fd763e6a06319679f933f7fb9bf29910a004b262
cd ..

git clone https://github.com/clojure/tools.analyzer.jvm.git
cd tools.analyzer.jvm
git checkout tools.analyzer.jvm-0.6.5
cd ..

git clone https://github.com/clojure/core.memoize.git
cd core.memoize
#git checkout core.memoize-0.5.6
# Many commits after 0.5.6, up through Nov 16 2014
git checkout c0a91d09a666490c4b2702f4f05f5430b5fb9e3d
cd ..

git clone https://github.com/clojure/core.cache.git
cd core.cache
# Latest as of Nov 17 2014 has only doc changes from core.cache-0.6.4
git checkout core.cache-0.6.4
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
patch -p1 < ../../tools.namespace-enhance-return-non-clj-files.patch
cd ..

git clone https://github.com/clojure/tools.reader.git
cd tools.reader
git checkout tools.reader-0.8.12
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
