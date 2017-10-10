#! /bin/bash

if [ "x${LEIN_PROFILE}" != "x" ]
then
    q="${LEIN_PROFILE},+test"
else
    q="with-profile +test"
fi

set -x
lein ${q} eastwood '{:continue-on-exception true :exclude-namespaces [ taoensso.timbre.appenders.3rd-party.android-logcat taoensso.timbre.appenders.3rd-party.congomongo taoensso.timbre.appenders.3rd-party.newrelic taoensso.timbre.appenders.3rd-party.server-socket taoensso.timbre.appenders.3rd-party.zmq ]}'
