#!/usr/bin/env bash
set -Euxo pipefail

lein with-profile -user,-dev,+eastwood-plugin install || exit 1

# Red project

cd .circleci/example-red-project || exit 1

rm -rf .eastwood*
if lein with-profile -user eastwood > output; then
  echo "Should have failed! Emitted output:"
  cat output
  exit 1
fi

grep --silent "== Warnings: 1. Exceptions thrown: 0" output  || exit 1

rm -rf .eastwood*
if lein with-profile -user run -m eastwood.lint > output; then
  echo "Should have failed! Emitted output:"
  cat output
  exit 1
fi

grep --silent "== Warnings: 1. Exceptions thrown: 0" output  || exit 1

rm -rf .eastwood*
if clojure -M:eastwood > output; then
  echo "Should have failed! Emitted output:"
  cat output
  exit 1
fi

grep --silent "== Warnings: 1. Exceptions thrown: 0" output  || exit 1

# Green project

cd ../example-green-project || exit 1

rm -rf .eastwood*
if ! lein with-profile -user eastwood > output; then
  echo "Should have passed!"
  exit 1
fi

grep --silent "== Warnings: 0. Exceptions thrown: 0" output  || exit 1

rm -rf .eastwood*
if ! lein with-profile -user run -m eastwood.lint > output; then
  echo "Should have passed!"
  exit 1
fi

grep --silent "== Warnings: 0. Exceptions thrown: 0" output  || exit 1

rm -rf .eastwood*
if ! clojure -M:eastwood > output; then
  echo "Should have passed!"
  exit 1
fi

grep --silent "== Warnings: 0. Exceptions thrown: 0" output  || exit 1

# Refresh project

cd ../example-refresh-project || exit 1

rm -rf .eastwood*
if ! lein with-profile -user eastwood > output; then
  echo "Should have passed!"
  exit 1
fi

grep --silent "== Warnings: 0. Exceptions thrown: 0" output  || exit 1

rm -rf .eastwood*
if ! clojure -M:eastwood > output; then
  echo "Should have passed!"
  exit 1
fi

grep --silent "== Warnings: 0. Exceptions thrown: 0" output  || exit 1

exit 0
