#! /bin/bash

if [ "x${LEIN_PROFILE}" != "x" ]
then
    q="${LEIN_PROFILE},+test"
else
    q="with-profile +test"
fi

set -x
lein ${q} eastwood '{:exclude-namespaces [ taoensso.timbre.appenders.android ]}'
