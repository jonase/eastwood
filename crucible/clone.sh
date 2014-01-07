#! /bin/bash

# Clone git repositories containing Clojure code useful for testing
# Eastwood with.

C="git clone"

# Create a subdirectory for the pulled repos.  This makes it easier to
# delete them all if you later want to clone the latest versions again
# all from scratch.
mkdir -p repos
cd repos

# The repos that say 'No code yet' were last checked on Dec 6 2013

$C https://github.com/clojure/algo.generic
$C https://github.com/clojure/algo.graph
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
$C https://github.com/clojure/data.avl
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
# TBD whether to add liberator to crucible
$C https://github.com/clojure-liberator/liberator.git
$C https://github.com/clojurewerkz/archimedes.git
$C https://github.com/clojurewerkz/buffy
$C https://github.com/clojurewerkz/cassaforte
$C https://github.com/clojurewerkz/elastisch
$C https://github.com/clojurewerkz/mailer
$C https://github.com/clojurewerkz/meltdown
$C https://github.com/clojurewerkz/money
$C https://github.com/clojurewerkz/ogre.git
$C https://github.com/clojurewerkz/scrypt.git
$C https://github.com/clojurewerkz/serialism.git

# Dec 20 2013: romulan considered deprecated project by ClojureWerkz
#$C https://github.com/clojurewerkz/romulan

# Dec 20 2013: Latest spyglass passes 'lein check', but fails to make
# connection attempts even when only doing eastwood analysis on some
# its test namespaces.
#$C https://github.com/clojurewerkz/spyglass.git

$C https://github.com/clojurewerkz/support.git
$C https://github.com/clojurewerkz/titanium
$C https://github.com/dakrone/cheshire
$C https://github.com/daveray/seesaw
$C https://github.com/davidsantiago/stencil.git
$C https://github.com/flatland/useful
$C https://github.com/franks42/clj-ns-browser.git
# TBD whether to add http-kit to crucible
$C https://github.com/http-kit/http-kit.git
$C https://github.com/hugoduncan/criterium

# TBD: Decide whether to add Midje to lint.sh
# Many exceptions are thrown because of symbols with names like ...foo...
# Also takes a long time to lint, I think mostly because it runs all
# of the voluminous tests while linting.
$C https://github.com/marick/Midje.git

$C https://github.com/michaelklishin/chash.git

# Dec 20 2013: Latest neocons passes 'lein check', but 'lein eastwood'
# throws exceptions because it fails to make network connections.
# Save it for later.
#$C https://github.com/michaelklishin/neocons

$C https://github.com/michaelklishin/pantomime.git
$C https://github.com/michaelklishin/quartzite.git
$C https://github.com/michaelklishin/urly.git
$C https://github.com/michaelklishin/vclock.git

# Dec 20 2013: welle seems to hang during 'lein eastwood'.  It seems
# to be because some of the tests are trying to open connections, and
# those connection attempts hang.
#$C https://github.com/michaelklishin/welle.git

$C https://github.com/noir-clojure/lib-noir
$C https://github.com/pjstadig/utf8.git

# Dec 24 2013: 'lein with-profile dev,test check' required to get lein
# check to pass without missing dependencies.  'lein with-profile
# dev,test eastwood' throws an exception for one namespace, I think
# because of funky type hints that tools.analyzer does not handle.  I
# have tried to boil down a shorter test case in file
# testcases/f09.clj.  Filed ticket
# http://dev.clojure.org/jira/browse/TANAL-36 for it, and Nicola gave
# some suggestions on what to do about it.
$C https://github.com/ptaoussanis/carmine.git

# Dec 24 2013: timbre latest version fails 'lein check' because of
# lack of android.util.Log class.  It can run tests with 'lein
# with-profile +test test', and 'lein with-profile +test eastwood'
# works.  I customed project.clj to include dependencies on projects
# needed by the different timbre appenders.  TBD: Add to lint.sh after
# I add a way for projects to have customized 'lein eastwood' command
# line options, or I change project.clj to not need the different
# profile.
$C https://github.com/ptaoussanis/timbre.git

# Dec 20 2013: tower requires 'lein with-profile test eastwood' to get
# needed test dependencies on expectations library from its
# project.clj.  It also seems to run the unit tests while analyzing
# the test source files.  TBD whether that is normal for expectations
# library.
$C https://github.com/ptaoussanis/tower.git
$C https://github.com/Raynes/fs.git

# Dec 24 2013: Lots of exceptions about *runner* not being bound while
# linting, I think limited to the test namespaces.  These are likely
# due to the use of speclj for writing tests.  I do not know how to
# avoid those errors when linting.
$C https://github.com/trptcolin/reply.git

$C https://github.com/weavejester/compojure
$C https://github.com/weavejester/hiccup
$C https://github.com/weavejester/medley.git

# automat uses simple-check, which has a cyclic dependency from
# namespace simple-check.clojure-test back to simple-check.core.
# Filed ticket http://dev.clojure.org/jira/browse/TANAL-37 to track
# the issue.
$C https://github.com/ztellman/automat.git

$C https://github.com/ztellman/collection-check.git
$C https://github.com/ztellman/potemkin.git


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
cd ..

echo "Remove an empty file from data.zip that interferes with Leiningen ..."
/bin/rm -f repos/data.zip/src/test/clojure/clojure/data/zip.clj

echo "1-line change to hiccup file that eliminates many analyzer exceptions ..."
/bin/cp -f project-clj-files/hiccup/hiccup.clj repos/hiccup/src/hiccup/util.clj
