#! /bin/bash

set -x

# This command is needed to generate clj files from cljx files
lein cljx once

lein ${LEIN_PROFILE} eastwood '{:continue-on-exception true}'
