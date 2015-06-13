#! /bin/bash

set -ex

mkdir -p repos
cd repos

git clone https://github.com/clojure/tools.analyzer.git
cd tools.analyzer
# 0.6.6 is latest as of Jun 13 2015.  Only one very minor change since
# previous version Eastwood used, one commit before release 0.6.5.
git checkout tools.analyzer-0.6.6
cd ..

git clone https://github.com/clojure/tools.analyzer.jvm.git
cd tools.analyzer.jvm
# 0.6.7 is latest as of Jun 13 2015.  It has at least the beginning of
# support for reader conditionals in .cljc files.  Not sure I want
# that in Eastwood just yet.  Back off to 0.6.6 if it causes any
# problems.
git checkout tools.analyzer.jvm-0.6.7
cd ..

git clone https://github.com/clojure/core.memoize.git
cd core.memoize
# 0.5.7 is latest release as of Jun 13 2015.  Previous version used
# was just before 0.5.7 release, with no source code changes.
git checkout core.memoize-0.5.7
cd ..

git clone https://github.com/clojure/core.cache.git
cd core.cache
# Latest as of Jun 13 2015 has only doc changes from core.cache-0.6.4
git checkout core.cache-0.6.4
cd ..

git clone https://github.com/clojure/data.priority-map.git
cd data.priority-map
# 0.0.7 is latest as of Jun 13 2015.  Only very minor changes since 0.0.5
git checkout data.priority-map-0.0.7
cd ..

git clone https://github.com/clojure/tools.namespace.git
cd tools.namespace
# Latest as of Jun 13 2015 is 0.2.10, but it has .cljc changes for
# Clojure 1.7.0 that I do not wish to include in Eastwood until
# Eastwood is completely ready for it.
git checkout tools.namespace-0.2.9
patch -p1 < ../../tools.namespace-enhance-return-non-clj-files.patch
cd ..

git clone https://github.com/clojure/tools.reader.git
cd tools.reader
# Latest non-alpha release as of Jun 13 2015 is 0.9.2.  0.9.0 added
# reader conditional support, but I am hoping that will not affect
# Eastwood until and unless I explicity enable it.  If this does cause
# a problem, try backing off to 0.8.16, the last 0.8.x release.
git checkout tools.reader-0.9.2
cd ..

git clone https://github.com/sattvik/leinjacker.git
cd leinjacker
# v0.4.2 is latest as of Jun 13 2015, but I don't want to figure out
# any potential breakages from upgrading at this time.
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
