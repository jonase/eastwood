#! /bin/bash

# Clone git repositories containing Clojure code useful for testing
# Eastwood with.

# TBD: Add some commands to copy in project.clj files for those
# contrib libraries that do not have them, or that need modifications
# to their project.clj file to run Eastwood.

# TBD: Add 

C="git clone"

# Create a subdirectory for the pulled repos.  This makes it easier to
# delete them all if you later want to clone the latest versions again
# all from scratch.
mkdir -p repos
cd repos

# The repos that say 'No code yet' were last checked on Dec 6 2013

$C https://github.com/clojure/algo.generic
$C https://github.com/clojure/algo.monads
# Build infrastructure, not Clojure code https://github.com/clojure/build.ci
# Build infrastructure, not Clojure code https://github.com/clojure/build.poms
# Eastwood can lint Clojure core code without the repo, but pulling
# the repo makes it convenient for building the latest master version.
$C https://github.com/clojure/clojure
# No ClojureCLR for Eastwood yet https://github.com/clojure/clojure-clr
# Old nearly abondoned code https://github.com/clojure/clojure-contrib
# Docs, not Clojure code https://github.com/clojure/clojure.github.com
# No ClojureScript for Eastwood yet https://github.com/clojure/clojurescript
# No ClojureCLR for Eastwood yet https://github.com/clojure/clr.data.generators
# No ClojureCLR for Eastwood yet https://github.com/clojure/clr.data.json
# No ClojureCLR for Eastwood yet https://github.com/clojure/clr.test.generative
# No ClojureCLR for Eastwood yet https://github.com/clojure/clr.tools.analyzer
# No ClojureCLR for Eastwood yet https://github.com/clojure/clr.tools.logging
# No ClojureCLR for Eastwood yet https://github.com/clojure/clr.tools.namespace
# No ClojureCLR for Eastwood yet https://github.com/clojure/clr.tools.nrepl
# No ClojureCLR for Eastwood yet https://github.com/clojure/clr.tools.trace
# core.async uses &env in source code
$C https://github.com/clojure/core.async
$C https://github.com/clojure/core.cache
$C https://github.com/clojure/core.contracts
$C https://github.com/clojure/core.incubator
# core.logic uses &env in source code
$C https://github.com/clojure/core.logic
# core.match uses &env in source code
$C https://github.com/clojure/core.match
$C https://github.com/clojure/core.memoize
# core.rrb-vector uses &env in source code
$C https://github.com/clojure/core.rrb-vector
# core.typed uses &env in source code
$C https://github.com/clojure/core.typed
$C https://github.com/clojure/core.unify
$C https://github.com/clojure/data.codec
$C https://github.com/clojure/data.csv
# No code yet https://github.com/clojure/data.enlive
# Filed ticket DFINGER-1 for what might be the problem analyzing
# data.finger-tree.  With x changed to x#, Eastwood works with no
# warnings.
$C https://github.com/clojure/data.finger-tree
$C https://github.com/clojure/data.fressian
$C https://github.com/clojure/data.generators
$C https://github.com/clojure/data.json
$C https://github.com/clojure/data.priority-map
$C https://github.com/clojure/data.xml
$C https://github.com/clojure/data.zip
# No code yet https://github.com/clojure/io.incubator
$C https://github.com/clojure/java.classpath
# Successful Eastwood run requires setting :java-source-paths in project.clj
$C https://github.com/clojure/java.data
# No code yet https://github.com/clojure/java.internal.invoke
$C https://github.com/clojure/java.jdbc
$C https://github.com/clojure/java.jmx
$C https://github.com/clojure/jvm.tools.analyzer
$C https://github.com/clojure/math.combinatorics
$C https://github.com/clojure/math.numeric-tower
# No code yet https://github.com/clojure/net.ring
# Not much code yet https://github.com/clojure/test.benchmark
# No code yet https://github.com/clojure/test.check
$C https://github.com/clojure/test.generative
# tools.analyzer uses &env in source code
$C https://github.com/clojure/tools.analyzer
$C https://github.com/clojure/tools.analyzer.jvm
$C https://github.com/clojure/tools.cli
$C https://github.com/clojure/tools.emitter.jvm
$C https://github.com/clojure/tools.logging
$C https://github.com/clojure/tools.macro
$C https://github.com/clojure/tools.namespace
$C https://github.com/clojure/tools.nrepl
$C https://github.com/clojure/tools.reader
# Eastwood correctly finds redefinitions from uses of deftrace in
# tools.trace.  Filed TTRACE-6 to see if deftrace should be changed to
# avoid this.
$C https://github.com/clojure/tools.trace


# Some other Clojure libraries that are not Clojure contrib libraries

$C https://github.com/cgrand/enlive
$C https://github.com/clojurewerkz/buffy
$C https://github.com/clojurewerkz/cassaforte
$C https://github.com/clojurewerkz/elastisch
$C https://github.com/clojurewerkz/mailer
$C https://github.com/clojurewerkz/meltdown
$C https://github.com/clojurewerkz/money
$C https://github.com/clojurewerkz/romulan
$C https://github.com/clojurewerkz/titanium
$C https://github.com/dakrone/cheshire
$C https://github.com/daveray/seesaw
$C https://github.com/flatland/useful
$C https://github.com/hugoduncan/criterium
$C https://github.com/michaelklishin/neocons
$C https://github.com/michalmarczyk/avl.clj
$C https://github.com/noir-clojure/lib-noir
$C https://github.com/weavejester/compojure
$C https://github.com/weavejester/hiccup


cd ..

echo "Copy additional files into some of the cloned repos ..."

set -x
cd project-clj-files
for dir in *
do
    cd $dir
    cp * ../../repos/$dir/
    cd ..
done
