#!/usr/bin/env bash
set -Eeuxo pipefail

mkdir -p ~/.lein

echo '{:eastwood-ci-clojure-1-10 {:dependencies [[org.clojure/clojure "1.10.3"]]} :eastwood-ci-clojure-1-11 {:dependencies [[org.clojure/clojure "1.11.0-alpha3"], [org.clojure/spec.alpha "0.3.214"]]}}' > ~/.lein/profiles.clj
