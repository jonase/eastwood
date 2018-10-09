automat uses simple-check, which has a cyclic dependency from
namespace simple-check.clojure-test back to simple-check.core.  Filed
ticket https://dev.clojure.org/jira/browse/TANAL-37 to track the issue.
It seems to work now after fixing this Eastwood issue:
https://github.com/jonase/eastwood/issues/44
