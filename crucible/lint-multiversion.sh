#! /bin/bash

ORIG_PATH="${PATH}"

lsb_release >& /dev/null
if [ $? != 0 ]
then
    uname >& /dev/null
    if [ $? != 0 ]
    then
        1>&2 echo "Neither the command 'lsb_release' nor 'uname' were found in your command path."
        exit 1
    else
        OS_VERSION="`uname -s`-`uname -r`"
    fi
else
    OS_VERSION="`lsb_release -si`-`lsb_release -sr`"
fi

for j in 1 2 3 4 5 6 7 8
do
    case ${j} in
    1)
	EASTWOOD="0.3.3"
	LEIN_PROFILE_CLJ_VERSION="1.9"
	JDK="oracle-1.8.0_192"
	;;
    2)
	EASTWOOD="0.3.3"
	LEIN_PROFILE_CLJ_VERSION="1.10"
	JDK="oracle-1.8.0_192"
	;;
    3)
	EASTWOOD="0.2.6"
	LEIN_PROFILE_CLJ_VERSION="1.9"
	JDK="oracle-1.8.0_192"
	;;
    4)
	EASTWOOD="0.2.6"
	LEIN_PROFILE_CLJ_VERSION="1.10"
	JDK="oracle-1.8.0_192"
	;;
    5)
	EASTWOOD="0.3.3"
	LEIN_PROFILE_CLJ_VERSION="1.9"
	JDK="openjdk-11.0.1"
	;;
    6)
	EASTWOOD="0.3.3"
	LEIN_PROFILE_CLJ_VERSION="1.10"
	JDK="openjdk-11.0.1"
	;;
    7)
	EASTWOOD="0.2.6"
	LEIN_PROFILE_CLJ_VERSION="1.9"
	JDK="openjdk-11.0.1"
	;;
    8)
	EASTWOOD="0.2.6"
	LEIN_PROFILE_CLJ_VERSION="1.10"
	JDK="openjdk-11.0.1"
	;;
    esac

    case ${LEIN_PROFILE_CLJ_VERSION} in
    1.9)
	CLJ_VERSION="1.9.0"
	;;
    1.10)
	CLJ_VERSION="1.10.0-beta5"
	;;
    *)
	1>&2 echo "Unrecognized value for LEIN_PROFILE_CLJ_VERSION=${LEIN_PROFILE_CLJ_VERSION}   Aborting."
	exit 1
	;;
    esac
    
    case ${JDK} in
    oracle-1.8.0_192)
	export JAVA_HOME=${HOME}/jdks/jdk1.8.0_192
	;;
    openjdk-11.0.1)
	export JAVA_HOME=${HOME}/jdks/jdk-11.0.1
	;;
    *)
	1>&2 echo "Unrecognized value for JDK=${JDK}   Aborting."
	exit 1
	;;
    esac
    if [ -d "${JAVA_HOME}" -a -x "${JAVA_HOME}/bin/java" ]
    then
	1>&2 echo "Found directory ${JAVA_HOME} and executable ${JAVA_HOME}/bin/java"
    else
	1>&2 echo "No such directory ${JAVA_HOME} or no executable ${JAVA_HOME}/bin/java    Aborting."
	exit 1
    fi
    export PATH="${JAVA_HOME}/bin:${ORIG_PATH}"
    
    echo out-${EASTWOOD}-clj-${CLJ_VERSION}-jdk-${JDK}-${OS_VERSION}.txt
    ./lint.sh ${EASTWOOD} ${LEIN_PROFILE_CLJ_VERSION} >& out-${EASTWOOD}-clj-${CLJ_VERSION}-jdk-${JDK}-${OS_VERSION}.txt
done

