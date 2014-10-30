#! /bin/bash

# Using Eastwood on itself causes a few exceptions if you do it on all
# namespaces, I think due to double-analysis and/or eval of namespaces
# with defprotocol, deftype, or something of that sort.  For now, just
# exclude the few namespaces that cause trouble.

# Add :debug #{:ns} to see the namespaces included in the list to be
# linted.

# Trying to lint namespace eastwood.test.linters-test causes a "Method
# code too large!" RuntimeException to be thrown.  There are a few
# others in other projects, too.  Should see if there is a way to
# prevent this.  I think the root cause is macros that get very large
# because of lots of tools.reader metadata on subexpressions of
# backquoted expressions.

lein eastwood '{:exclude-namespaces [eastwood.copieddeps.dep4.clojure.core.cache eastwood.copieddeps.dep6.leinjacker.utils eastwood.copieddeps.dep6.leinjacker.eval eastwood.copieddeps.dep6.leinjacker.eval-in-project leiningen.eastwood eastwood.test.linters-test]}'
