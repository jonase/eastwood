#! /bin/bash

# Leave out the following namespaces while linting, since it seems to
# use some require/load-time compilation techniques that cause
# Eastwood to throw an exception:

# cider-nrepl.plugin

set -x
lein ${LEIN_PROFILE} eastwood '{:exclude-namespaces [cider-nrepl.plugin]}'
