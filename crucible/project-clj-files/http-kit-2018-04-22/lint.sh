#! /bin/bash

# Add 'with-profile +test' to bring in some Leiningen dependencies
# needed for linting some of the test namespaces.

# Leave out the following namespaces while linting, since with Clojure
# 1.9.0 and later they require a version of a dependency
# http.async.client whose source code gives a syntax error with
# Clojure 1.9's tighter macro syntax checking.

# org.httpkit.ws-test
# org.httpkit.server-test

set -x
lein with-profile +test ${LEIN_PROFILE} eastwood '{:continue-on-exception true :exclude-namespaces [org.httpkit.ws-test org.httpkit.server-test]}'
