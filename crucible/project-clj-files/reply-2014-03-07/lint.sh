#! /bin/bash

set -x
lein ${LEIN_PROFILE} eastwood '{:namespaces [ :source-paths ]}'
