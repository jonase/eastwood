#! /bin/bash

set -x
# The :constant-test linter is *very* noisy for the Carmine project
lein ${LEIN_PROFILE} eastwood '{:continue-on-exception true :exclude-linters [:constant-test] :namespaces [ :source-paths ]}'
