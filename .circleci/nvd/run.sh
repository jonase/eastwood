#!/bin/bash
set -Eeuxo pipefail

NVD_DIR=".circleci/nvd"
PROJECT_CLASSPATH="$(LEIN_SILENT=true lein with-profile -user,-dev classpath)"

# Inspired by https://github.com/rm-hull/lein-nvd/tree/b455ec33baa837a4e11031985251ca81d5ceb86f#avoiding-classpath-interference
DEPENDENCY_CHECK_COMMAND="lein with-profile -user,-dev run -m nvd.task.check $PWD/$NVD_DIR/nvd-config.json $PROJECT_CLASSPATH"

cd "$NVD_DIR" || exit 1

# Try a few times in face of flakiness:
eval "$DEPENDENCY_CHECK_COMMAND" || eval "$DEPENDENCY_CHECK_COMMAND" || eval "$DEPENDENCY_CHECK_COMMAND"
