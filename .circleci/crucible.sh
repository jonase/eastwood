#!/usr/bin/env bash
set -Euxo pipefail

lein with-profile -user,-dev,+eastwood-plugin install || exit 1

cd .circleci || exit 1

git submodule update --init --recursive

# Exercise core.async because it's been historically a difficult-to-analyse one
cd ./core.async || exit 1

if lein with-profile +test update-in :plugins conj "[jonase/eastwood \"RELEASE\"]" -- eastwood | tee output; then
  echo "Should have failed! Emitted output:"
  cat output
  exit 1
fi

if grep --silent "Reflection warning" output; then
  echo "Reflection warnings should have been detected as an Eastwood linter and not as mere stdout!"
  exit 1
fi

grep --silent "== Warnings: 29. Exceptions thrown: 0" output || exit 1

# Exercise tools.reader, see https://github.com/jonase/eastwood/issues/413
cd ../tools.reader || exit 1

if lein with-profile +test update-in :plugins conj "[jonase/eastwood \"RELEASE\"]" -- eastwood | tee output; then
  echo "Should have failed! Emitted output:"
  cat output
  exit 1
fi

if grep --silent "Reflection warning" output; then
  echo "Reflection warnings should have been detected as an Eastwood linter and not as mere stdout!"
  exit 1
fi

grep --silent "== Warnings: 14. Exceptions thrown: 0" output || exit 1

# Exercise clojurescript as it's large and interesting
cd ../clojurescript || exit 1

if lein with-profile +test update-in :plugins conj "[jonase/eastwood \"RELEASE\"]" -- eastwood | tee output; then
  echo "Should have failed! Emitted output:"
  cat output
  exit 1
fi

if grep --silent "Reflection warning" output; then
  echo "Reflection warnings should have been detected as an Eastwood linter and not as mere stdout!"
  exit 1
fi

grep --silent "== Warnings: 340. Exceptions thrown: 0" output || exit 1

# Exercise malli because Eastwood used to choke on that project due to lack of explicit topo order for ns analysis
cd ../malli || exit 1

cp ../deps_project.clj project.clj

if lein with-profile +test update-in :plugins conj "[jonase/eastwood \"RELEASE\"]" -- eastwood | tee output; then
  echo "Should have failed! Emitted output:"
  cat output
  exit 1
fi

if grep --silent "Reflection warning" output; then
  echo "Reflection warnings should have been detected as an Eastwood linter and not as mere stdout!"
  exit 1
fi

grep --silent "== Warnings: 111. Exceptions thrown: 0" output || exit 1

# Exercise crux simply because it's a large project with plenty of Java hints, interop etc
cd ../crux || exit 1
./lein-sub install
./lein-sub with-profile -user,+test update-in :dependencies conj "[jonase/eastwood \"RELEASE\"]" -- run -m eastwood.lint "{:source-paths [\"src\"], :test-paths [\"test\"], :forced-exit-code 0}" | tee output

# Assert that no exceptions are thrown, and that useful insights are found:
ex_marker="Exceptions thrown:"

if grep --silent "$ex_marker [1-9]" output; then
  echo "Should not have thrown an exception!"
  exit 1
fi

if grep --silent "Reflection warning" output; then
  echo "Reflection warnings should have been detected as an Eastwood linter and not as mere stdout!"
  exit 1
fi

grep --silent "Warnings: 3. $ex_marker 0" output || exit 1
grep --silent "Warnings: 4. $ex_marker 0" output || exit 1
grep --silent "Warnings: 10. $ex_marker 0" output || exit 1
grep --silent "Warnings: 16. $ex_marker 0" output || exit 1
grep --silent "Warnings: 36. $ex_marker 0" output || exit 1
grep --silent "Warnings: 45. $ex_marker 0" output || exit 1

zero_warns=$(grep -c "Warnings: 0. $ex_marker 0" output)
zero_warns=${zero_warns// /}
if [ "$zero_warns" != "11" ]; then
  exit 1
fi

exit 0
