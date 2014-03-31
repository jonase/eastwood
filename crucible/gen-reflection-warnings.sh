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

#PROFILE=""
PROFILE="+1.6"
#PROFILE="+1.7"

curdir=`pwd`
if [ -x "${curdir}/cmp-reflect.sh" ]
then
    CMPREFLECT=${curdir}/cmp-reflect.sh
else
    echo "No executable script cmp-reflect.sh found in dir ${curdir}.  Aborting"
    exit 1
fi

do_lein_check_and_eastwood_on_source_paths_only()
{
    local LEIN_PROFILE
    if [ "x${PROFILE}" != "x" ]
    then
	LEIN_PROFILE="with-profile ${PROFILE}"
    else
	LEIN_PROFILE=""
    fi

    # 'lein check' only compiles and gives compiler errors and
    # reflection warnings for files in Leiningen :source-paths, which
    # by default is only [ "src" ].  It does not do so for files in
    # :test-paths (by default [ "test" ]).  To avoid needing to create
    # custom versions of all project.clj files just for this
    # reflection warning comparison, restrict our attention to only
    # files in :source-paths.
    lein clean
    /bin/rm -f lein-check-out.txt
    if [ -x ./lein-check.sh ]
    then
	set -x
	LEIN_PROFILE="${LEIN_PROFILE}" ./lein-check.sh >& lein-check-out.txt
	set +x
    else
	set -x
	lein ${LEIN_PROFILE} check >& lein-check-out.txt
	set +x
    fi

    lein clean
    /bin/rm -f lint-source-paths-out.txt
    if [ -x ./lint-source-paths.sh ]
    then
	set -x
	LEIN_PROFILE="${LEIN_PROFILE}" ./lint-source-paths.sh >& lint-source-paths-out.txt
	set +x
    else
	set -x
	lein ${LEIN_PROFILE} eastwood '{:namespaces [:source-paths]}' >& lint-source-paths-out.txt
	set +x
    fi
    set -x
    ${CMPREFLECT} lein-check-out.txt lint-source-paths-out.txt
    set +x
}

cd repos

echo 
echo "Running 'lein check' and Eastwood (on :source-paths only) of all"
echo "Clojure contrib and 3rd party libraries, to look for differences in"
echo "reflection warnings that are produced."
echo 
for p in */project.clj
do
    lib=`dirname ${p}`
    echo
    echo "=== $lib"
    echo
    cd $lib
    do_lein_check_and_eastwood_on_source_paths_only $lib
    cd ..
done
