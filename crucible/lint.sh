#! /bin/bash

# All of the Leiningen project.clj files should either already contain
# a profile called 1.6 that uses Clojure 1.6.0-master-SNAPSHOT, or I
# have made a modified project.clj file for them that does, and made
# things so that they all use Clojure 1.5.1 if you do not specify a
# profile.
#
# Thus change the commands below from:
#     lein eastwood ...
# to:
#     lein with-profile 1.6 eastwood ...
# to use Clojure 1.6.0-master-SNAPSHOT for all of these.


# set -e    exit immediately if a command exits with a non-0 exit status
# set +e    stop doing that

# set -x    echo expanded commands before executing them
# set +x    stop doing that

do_eastwood()
{
    local ns="$1"

    set +e   # Do not stop if an eastwood run returns a non-0 exit status.  Keep going with more checking, if any.
    if [ "x${ns}" == "x" ]
    then
	lein eastwood "{:exclude-linters [:unused-fn-args :unused-namespaces]}"
    else
	lein eastwood "{:namespaces [ ${core_ns} ] :exclude-linters [:unused-fn-args :unused-namespaces]}"
    fi
    set -e
}

set -e   # Fail if any of the directories do not exist, so this script can be fixed
cd repos

echo 
echo "Linting 3rd party Clojure libraries"
echo 
for lib in \
    avl.clj \
    cheshire \
    criterium \
    elastisch \
    enlive \
    hiccup \
    lib-noir \
    mailer \
    meltdown \
    money \
    buffy \
    cassaforte \
    seesaw \
    titanium \
    useful
do
    echo
    echo $lib
    echo
    cd $lib
    do_eastwood
    cd ..
done

echo
echo "Linting most, but not all, namespaces in Clojure itself."
echo "Skipping these namespaces, which tend to throw exceptions:"
echo "    clojure.core - might not be feasible to analyze this code"
echo "    clojure.parallel - perhaps only fails if you use JDK < 7"
echo
cd algo.generic
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
    do_eastwood ${core_ns}
done
cd ..

echo 
echo "Linting Clojure contrib libraries"
echo 
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
    echo
    echo $lib
    echo
    cd $lib
    do_eastwood
    cd ..
done
