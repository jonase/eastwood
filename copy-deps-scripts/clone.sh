#! /bin/bash

set -ex

mkdir -p repos
cd repos

git clone https://github.com/clojure/tools.analyzer.git
cd tools.analyzer
# This is latest version as of Oct 9 2017.
git checkout tools.analyzer-0.6.9
cd ..

git clone https://github.com/clojure/tools.analyzer.jvm.git
cd tools.analyzer.jvm
# This is latest version as of Oct 9 2017.
git checkout tools.analyzer.jvm-0.7.1
cd ..

git clone https://github.com/clojure/core.memoize.git
cd core.memoize
# This is latest version as of Oct 9 2017.
git checkout core.memoize-0.5.9
cd ..

git clone https://github.com/clojure/core.cache.git
cd core.cache
# This is latest version as of Oct 9 2017.
git checkout core.cache-0.6.5
cd ..

git clone https://github.com/clojure/data.priority-map.git
cd data.priority-map
# This is latest version as of Oct 9 2017.
git checkout data.priority-map-0.0.7
cd ..

git clone https://github.com/clojure/tools.namespace.git
cd tools.namespace
# 0.3.0-alpha3 is latest as of Oct 9 2017.  It has .cljc files, which
# I will try to simply rename to .clj files and hand-resolve the
# reader conditionals they contain for the :clj platform, removing the
# ClojureScript-specific expressions completely.  I am hoping this
# change will be all that is necessary in order for the resulting code
# to work with Clojure 1.5.1 and up, rather than requiring Clojure
# 1.7.0 as reader conditionals and .cljc files do.
git checkout tools.namespace-0.3.0-alpha3
git mv src/main/clojure/clojure/tools/namespace/dependency.cljc src/main/clojure/clojure/tools/namespace/dependency.clj
git mv src/main/clojure/clojure/tools/namespace/parse.cljc src/main/clojure/clojure/tools/namespace/parse.clj
git mv src/main/clojure/clojure/tools/namespace/track.cljc src/main/clojure/clojure/tools/namespace/track.clj
patch -p1 < ../../tools.namespace-0.3.0-alpha3-enhance-return-non-clj-files.patch
cd ..

git clone https://github.com/clojure/java.classpath.git
cd java.classpath
# This is latest version as of Oct 9 2017.
git checkout java.classpath-0.2.3
cd ..

git clone https://github.com/clojure/tools.reader.git
cd tools.reader
# This is latest version as of Oct 9 2017.
git checkout tools.reader-1.1.0
cd ..

git clone https://github.com/sattvik/leinjacker.git
cd leinjacker

# This is an unreleased version of leinjacker that is the latest as of
# Aug 15 2017.  It adds support for Leiningen managed dependencies.
# See Eastwood issue #230 for more details.
git checkout 3f4b0d73170dcd984608c703d80ee4d52dcdac73
cd ..

git clone https://github.com/clojure/core.contracts
cd core.contracts
# There are later versions of core.contracts than 0.0.1 as of Jan 15
# 2016, but this is the version that leinjacker v0.4.1 uses, and
# nothing else in Eastwood uses it, so I will not bother to try using
# the latest.
git checkout core.contracts-0.0.1
cd ..

git clone https://github.com/clojure/core.unify
cd core.unify
# There are later versions of core.unify than 0.5.3 as of Jan 15 2016,
# but this is the version that leinjacker v0.4.1 uses, and nothing
# else in Eastwood uses it, so I will not bother to try using the
# latest.
git checkout core.unify-0.5.3
cd ..

for j in *
do
    cd $j
    echo "=== $j"
    git status .
    cd ..
done
