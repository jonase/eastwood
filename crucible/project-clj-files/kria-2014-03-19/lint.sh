#! /bin/bash

set -x

# This command is needed to compile some Java source files.  Without
# it, most other Leiningen commands on the project will fail,
# including Eastwood.
lein with-profile base javac

lein ${LEIN_PROFILE} eastwood
