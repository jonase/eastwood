#! /bin/bash

# All of the Leiningen project.clj files should either already contain
# profiles called 1.6 1.7 1.8 1.9 1.10 that uses the corresponding
# version of Clojure, or I have made a modified project.clj file for
# them that does, and they all use Clojure 1.5.1 if you do not specify
# a profile.

# set -e    exit immediately if a command exits with a non-0 exit status
# set +e    stop doing that

# set -x    echo expanded commands before executing them
# set +x    stop doing that

which lein >& /dev/null
EXIT_STATUS=$?
if [ $EXIT_STATUS != 0 ]
then
    1>&2 echo "Command 'lein' not found:"
    exit $EXIT_STATUS
fi
if [ -d "$HOME/.lein" ]
then
    1>&2 echo "Found directory $HOME/.lein as expected."
else
    1>&2 echo "No directory $HOME/.lein found.  Aborting."
    exit 1
fi


if [ $# -ne 2 ]
then
    1>&2 echo "usage: `basename $0` <eastwood_version> <clj_version_as_lein_profile>"
    1>&2 echo ""
    1>&2 echo "Example <clj_version_as_lein_profile>: 1.6 1.7 1.8 1.9 1.10"
    exit 1
fi

EASTWOOD_VERSION="$1"
PROFILE="+$2"


RESTORE_PROFILES_CLJ=0
if [ -e "$HOME/.lein/profiles.clj" ]
then
    PROFILES_CLJ_BACKUP_FNAME=`mktemp -p $HOME/.lein -t profiles.clj-backup-XXXXXX`
    EXIT_STATUS=$?
    if [ $EXIT_STATUS != 0 ]
    then
	1>&2 echo "The following command failed with exit status $EXIT_STATUS"
	1>&2 echo "mktemp -p $HOME/.lein -t profiles.clj-backup-XXXXXX"
	exit $EXIT_STATUS
    fi
    1>&2 echo "$HOME/.lein/profiles.clj exists.  Moving it to temporary"
    1>&2 echo "file $PROFILES_CLJ_BACKUP_FNAME while running this script."
    /bin/mv -f "$HOME/.lein/profiles.clj" "$PROFILES_CLJ_BACKUP_FNAME"
    RESTORE_PROFILES_CLJ=1
fi

echo "{:user {:plugins [[jonase/eastwood \"${EASTWOOD_VERSION}\"]]}}" > "$HOME/.lein/profiles.clj"


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

    #echo "do_eastwood: project=$1 ns=$2 LEIN_PROFILE=${LEIN_PROFILE}"
    #return 0
    #echo "After return 0.  How did this happen?"

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
	    lein ${LEIN_PROFILE} eastwood '{:continue-on-exception true}' >& eastwood-out.txt
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
	lein ${LEIN_PROFILE} eastwood '{:continue-on-exception true :namespaces [ :test-paths ] :exclude-namespaces [ clojure.core clojure.parallel clojure.test-clojure.api clojure.test-clojure.compilation clojure.test-clojure.data-structures clojure.test-clojure.edn clojure.test-clojure.generators clojure.test-clojure.numbers clojure.test-clojure.reader clojure.test-clojure.evaluation clojure.test-clojure.genclass clojure.test-clojure.try-catch clojure.test-helper clojure.test-clojure.protocols ]}' >& eastwood-out.txt
	set +x
    elif [ "${project}" = "clojure" ]
    then
	set -x
	lein ${LEIN_PROFILE} eastwood "{:continue-on-exception true :namespaces [ ${ns} ]}" >& eastwood-out.txt
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

date

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

date

# Remove temporary profiles.clj file
/bin/rm -f "$HOME/.lein/profiles.clj"

# Restore the original, if there was one
if [ $RESTORE_PROFILES_CLJ == 1 ]
then
    /bin/mv -f "$PROFILES_CLJ_BACKUP_FNAME" "$HOME/.lein/profiles.clj"
    1>&2 echo "Restored original $HOME/.lein/profiles.clj file"
    1>&2 echo "to its original location."
fi
