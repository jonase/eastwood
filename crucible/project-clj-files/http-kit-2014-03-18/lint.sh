#! /bin/bash

set -x
lein ${LEIN_PROFILE} eastwood '{:continue-on-exception true :exclude-namespaces [ org.httpkit.client-test org.httpkit.server-test org.httpkit.ws-test ]}'
