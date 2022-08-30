#!/usr/bin/env bash
set -Eeuxo pipefail
cd "${BASH_SOURCE%/*}"

rm -rf repos
mkdir -p repos
cd repos

git clone https://github.com/clojure/tools.analyzer.git
cd tools.analyzer
git checkout v1.1.0
cd ..

git clone https://github.com/clojure/tools.analyzer.jvm.git
cd tools.analyzer.jvm
git checkout v1.2.1
cd ..

git clone https://github.com/clojure/core.memoize.git
cd core.memoize
git checkout v1.0.257
cd ..

git clone https://github.com/clojure/core.cache.git
cd core.cache
git checkout v1.0.225
cd ..

git clone https://github.com/clojure/data.priority-map.git
cd data.priority-map
git checkout v1.1.0
cd ..

git clone https://github.com/clojure/tools.namespace.git
cd tools.namespace
git checkout v1.3.0
cd ..

git clone https://github.com/clojure/java.classpath.git
cd java.classpath
git checkout java.classpath-1.0.0
cd ..

git clone https://github.com/clojure/tools.reader.git
cd tools.reader
git checkout v1.3.6
cd ..

for j in *
do
  cd $j
  echo "=== $j"
  git status .
  cd ..
done
