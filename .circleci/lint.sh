#!/usr/bin/env bash
set -Eeuxo pipefail

classpath="$(lein with-profile -user,+test classpath)"
# populate a clj-kondo cache per https://github.com/clj-kondo/clj-kondo/tree/4f1252748b128da6ea23033f14b2bec8662dc5fd#project-setup :
lein with-profile -user,+test,+clj-kondo run -m clj-kondo.main --lint "$classpath" --dependencies --parallel --copy-configs
lein with-profile -user,+test,+clj-kondo run -m clj-kondo.main --lint src test lein-eastwood var-info-test

lein with-profile -user,-dev,+antq,+clj-kondo trampoline antq
lein with-profile -user,-dev,+antq,+check-var-info trampoline antq

NVD_SCRIPT=".circleci/nvd/run.sh"
shellcheck $NVD_SCRIPT
$NVD_SCRIPT
