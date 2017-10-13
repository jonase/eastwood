#! /bin/bash

set -x
lein ${LEIN_PROFILE} eastwood '{:continue-on-exception true :exclude-namespaces [me.raynes.core-test]}'
