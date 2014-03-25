Dec 24 2013:

    lein with-profile dev,test check

required to get lein check to pass without missing dependencies.

    lein with-profile dev,test eastwood

throws an exception for one namespace, I think because of funky type
hints that tools.analyzer does not handle.  I have tried to boil down
a shorter test case in file testcases/f09.clj.  Filed ticket
http://dev.clojure.org/jira/browse/TANAL-36 for it, and Nicola gave
some suggestions on what to do about it.
