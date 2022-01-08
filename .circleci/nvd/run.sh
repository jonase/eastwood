#!/bin/bash
set -Eeuxo pipefail

NVD_DIR="$PWD/.circleci/nvd"
# calculate a production classpath within the main project:
CLASSPATH="$(LEIN_SILENT=true lein with-profile -user,-dev classpath)"

DEPENDENCY_CHECK_COMMAND="clojure -M:nvd $NVD_DIR/nvd-config.json $CLASSPATH"

# cd into a different project defining its own deps.edn:
cd "$NVD_DIR" || exit 1

# Try a few times in face of flakiness:
eval "$DEPENDENCY_CHECK_COMMAND" || eval "$DEPENDENCY_CHECK_COMMAND" || eval "$DEPENDENCY_CHECK_COMMAND"
