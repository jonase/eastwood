#! /bin/bash

# Leave namespace flatland.useful.datatypes-test out of linting run,
# since attempting to lint it leads to the following known Eastwood
# limitation:

# Eastwood cannot analyze code that uses the values of &env in a macro expansion.
# See https://github.com/jonase/eastwood#explicit-use-of-clojure-environment-env


# Leave these two namespaces out of linting run, because they use
# sun.misc Java classes that were part of JDK8, but not in JDK10 or
# JDK11 (not sure if they are in JDK9 or not).  This reduces Eastwood
# test coverage only slightly, and enables not seeing those exceptions
# when testing Eastwood on this project.

# flatland.useful.compress
# flatland.useful.compress-test

set -x
lein ${LEIN_PROFILE} eastwood '{:continue-on-exception true :exclude-namespaces [flatland.useful.datatypes-test flatland.useful.compress flatland.useful.compress-test]}'
