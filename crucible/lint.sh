#! /bin/bash

# These namespaces throw exceptions when I try to lint them:
# clojure.core
# clojure.parallel (perhaps only because I was using JDK6 at the time)

set -x
cd repos

cd algo.generic
set +x
set -e
for core_ns in \
    clojure.core.protocols \
    clojure.core.reducers \
    clojure.data \
    clojure.edn \
    clojure.inspector \
    clojure.instant \
    clojure.java.browse \
    clojure.java.browse-ui \
    clojure.java.io \
    clojure.java.javadoc \
    clojure.java.shell \
    clojure.main \
    clojure.pprint \
    clojure.reflect \
    clojure.repl \
    clojure.set \
    clojure.stacktrace \
    clojure.string \
    clojure.template \
    clojure.test.junit \
    clojure.test.tap \
    clojure.test \
    clojure.uuid \
    clojure.walk \
    clojure.xml \
    clojure.zip
do
    lein eastwood "{:namespaces [ ${core_ns} ]}"
done
cd ..

for lib in \
    algo.generic \
    algo.monads \
    core.async \
    core.cache \
    core.contracts \
    core.incubator \
    core.logic \
    core.match \
    core.memoize \
    core.rrb-vector \
    core.typed \
    core.unify \
    data.codec \
    data.csv \
    data.finger-tree \
    data.fressian \
    data.generators \
    data.json \
    data.priority-map \
    data.xml \
    data.zip \
    java.classpath \
    java.data \
    java.jdbc \
    java.jmx \
    jvm.tools.analyzer \
    math.combinatorics \
    math.numeric-tower \
    test.generative \
    tools.analyzer \
    tools.analyzer.jvm \
    tools.cli \
    tools.emitter.jvm \
    tools.logging \
    tools.macro \
    tools.namespace \
    tools.nrepl \
    tools.reader \
    tools.trace
do
    echo $lib
    cd $lib
    set +e
    if [ -x ./lint.sh ]
    then
        ./lint.sh
    else
	lein eastwood
    fi
    set -e
    cd ..
done
