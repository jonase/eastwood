#! /bin/bash

set -x
lein ${LEIN_PROFILE} eastwood '{:continue-on-exception true :exclude-namespaces [cider.nrepl.middleware.util.java.parser]}'
