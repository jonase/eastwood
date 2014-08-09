#! /bin/bash

set -x
lein ${LEIN_PROFILE} eastwood '{:continue-on-exception true :namespaces [ :source-paths ]}'
