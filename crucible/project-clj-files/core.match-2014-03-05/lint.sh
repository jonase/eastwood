#! /bin/bash

set -x
# The :constant-test linter is *very* noisy for the tests of core.match
lein ${LEIN_PROFILE} eastwood '{:continue-on-exception true :exclude-linters [:constant-test]}'
