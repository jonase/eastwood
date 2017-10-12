#! /bin/bash

set -x
lein ${LEIN_PROFILE} eastwood '{:continue-on-exception true :exclude-namespaces [ org.httpkit.client-proxy-test org.httpkit.server-test org.httpkit.ws-test ]}'
