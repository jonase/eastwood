#! /bin/bash

# All of the Leiningen project.clj files should either already contain
# a profile called 1.6 that uses Clojure 1.6.0, or I have made a
# modified project.clj file for them that does, and they all use
# Clojure 1.5.1 if you do not specify a profile.
#
# Thus change the value of the shell variable PROFILE below to
# "with-profile +1.6" to use Clojure 1.6.0-master-SNAPSHOT for all of
# these.
#
# All project.clj files also have a 1.7 profile that uses Clojure
# 1.7.0-master-SNAPSHOT.


# set -e    exit immediately if a command exits with a non-0 exit status
# set +e    stop doing that

# set -x    echo expanded commands before executing them
# set +x    stop doing that

#PROFILE=""
PROFILE="+1.6"
#PROFILE="+1.7"

do_eastwood()
{
    local project="$1"
    local ns="$2"

    local LEIN_PROFILE
    if [ "x${PROFILE}" != "x" ]
    then
	LEIN_PROFILE="with-profile ${PROFILE}"
    else
	LEIN_PROFILE=""
    fi

    lein clean
    /bin/rm -f eastwood-out.txt
    set +e   # Do not stop if an eastwood run returns a non-0 exit status.  Keep going with more checking, if any.
    if [ "x${ns}" = "x" ]
    then
	if [ -x ./lint.sh ]
	then
	    set -x
	    LEIN_PROFILE="${LEIN_PROFILE}" ./lint.sh >& eastwood-out.txt
	    set +x
	else
	    set -x
	    lein ${LEIN_PROFILE} eastwood >& eastwood-out.txt
	    set +x
	fi
    elif [ "${project}" = "clojure" -a "${ns}" = "clojure-special" ]
    then
	# Namespaces to exclude when analyzing namespaces in Clojure itself:

	# These namespaces throw exceptions related to their use of
	# test.generative:
	# == Linting clojure.test-clojure.api ==
	# == Linting clojure.test-clojure.compilation ==
        # == Linting clojure.test-clojure.data-structures ==
        # == Linting clojure.test-clojure.edn ==
        # == Linting clojure.test-clojure.generators == (data.generators for this one)
        # == Linting clojure.test-clojure.numbers ==
        # == Linting clojure.test-clojure.reader ==

        # This namespace throws an exception because of something to
        # do with a use of in-ns:

        # == Linting clojure.test-clojure.evaluation ==

        # This namespace throws an exception because it could not find
        # the class
        # clojure.test_clojure.genclass.examples.ExampleClass.
        # Perhaps this could be avoided by analyzing the namespaces in
        # a different order.

        # == Linting clojure.test-clojure.genclass ==
        # == Linting clojure.test-clojure.try-catch == ClassNotFoundException clojure.test.ReflectorTryCatchFixture

        # I am not sure, but perhaps Eastwood hangs trying to analyze
        # this namespace:
        # == Linting clojure.test-helper ==

	# Sometimes analyzing this namespace throws an exception, and
	# then hang, but I think not always:
        # == Linting clojure.test-clojure.protocols ==

	set -x
	lein ${LEIN_PROFILE} eastwood '{:namespaces [ :test-paths ] :exclude-namespaces [ clojure.core clojure.parallel clojure.test-clojure.api clojure.test-clojure.compilation clojure.test-clojure.data-structures clojure.test-clojure.edn clojure.test-clojure.generators clojure.test-clojure.numbers clojure.test-clojure.reader clojure.test-clojure.evaluation clojure.test-clojure.genclass clojure.test-clojure.try-catch clojure.test-helper clojure.test-clojure.protocols ]}' >& eastwood-out.txt
	set +x
    elif [ "${project}" = "clojure" ]
    then
	set -x
	lein ${LEIN_PROFILE} eastwood "{:namespaces [ ${ns} ]}" >& eastwood-out.txt
	set +x
    else
	echo "do_eastwood called with unknown combo project=${project} ns=${ns}"
	exit 1
    fi
    set -e
    cat eastwood-out.txt
}

set -e   # Fail if any of the directories do not exist, so this script can be fixed
cd repos

echo 
echo "Linting crucible projects, which includes most Clojure contrib"
echo "libraries, and several 3rd party libraries."
echo 
for p in */project.clj
do
    lib=`dirname ${p}`
    echo
    echo "=== $lib"
    echo
    cd $lib
    do_eastwood $lib
    cd ..
done

echo
echo "Linting most, but not all, namespaces in Clojure itself."
echo "Skipping these namespaces, which tend to throw exceptions:"
echo "    clojure.core - might not be feasible to analyze this code"
echo "    clojure.parallel - perhaps only fails if you use JDK < 7"
echo "This is done from within algo.generic project directory"
echo
cd algo.generic*
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
    do_eastwood clojure ${core_ns}
done
cd ..

# This mostly seems to work for analyzing Clojure's test namespaces,
# but it 'hangs' at the end without exiting.  I do not know why yet.

#cd clojure
#do_eastwood clojure clojure-special
#cd ..
