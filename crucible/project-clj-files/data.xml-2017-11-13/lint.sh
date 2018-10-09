#! /bin/bash

# Leave out the following namespace because tools.analyzer.jvm may be
# analyzing it incorrectly, in such a way that it throws an exception.
# See this ticket: https://dev.clojure.org/jira/browse/TANAL-126

# clojure.data.xml

# Leave out the following namespace while linting, since it seems more
# related to the ClojureScript part of the library, even though it is
# in a .clj file, and because it uses a JDK library
# javax.xml.bind.DatatypeConverter that no longer exists in JDK11.

# clojure.data.xml.cljs-repls

set -x
lein ${LEIN_PROFILE} eastwood '{:exclude-namespaces [clojure.data.xml clojure.data.xml.cljs-repls]}'
