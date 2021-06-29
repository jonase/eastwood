#!/usr/bin/env bash
set -Euxo pipefail

lein with-profile -user,-dev,+eastwood-plugin install || exit 1

cd .circleci || exit 1

git submodule update --init --recursive

cd ./core.async || exit 1

if lein with-profile +test update-in :plugins conj "[jonase/eastwood \"RELEASE\"]" -- eastwood | tee output; then
  echo "Should have failed! Emitted output:"
  cat output
  exit 1
fi

grep --silent "== Warnings: 30 (not including reflection warnings)  Exceptions thrown: 0" output  || exit 1

cd ../crux || exit 1
./lein-sub install
./lein-sub with-profile -user,+test update-in :dependencies conj "[jonase/eastwood \"RELEASE\"]" -- run -m eastwood.lint "{:source-paths [\"src\"], :test-paths [\"test\"], :forced-exit-code 0}" | tee output

# Assert that no exceptions are thrown, and that useful insights are found:
ex_marker=" Exceptions thrown:"

if grep --silent "$ex_marker [1-9]" output; then
  echo "Should not have thrown an exception!"
  exit 1
fi

grep --silent "Warnings: 1 (not including reflection warnings) $ex_marker 0" output  || exit 1
grep --silent "Warnings: 2 (not including reflection warnings) $ex_marker 0" output  || exit 1
grep --silent "Warnings: 3 (not including reflection warnings) $ex_marker 0" output  || exit 1
grep --silent "Warnings: 4 (not including reflection warnings) $ex_marker 0" output  || exit 1
grep --silent "Warnings: 42 (not including reflection warnings) $ex_marker 0" output  || exit 1

sixteen_warns=$(grep -c "Warnings: 16 (not including reflection warnings) $ex_marker 0" output)
sixteen_warns=${sixteen_warns// /}
if [ "$sixteen_warns" != "2" ]; then
  exit 1
fi

zero_warns=$(grep -c "Warnings: 0 (not including reflection warnings) $ex_marker 0" output)
zero_warns=${zero_warns// /}
if [ "$zero_warns" != "11" ]; then
  exit 1
fi

exit 0
