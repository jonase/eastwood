# Detailed changes in Eastwood behavior

Intended for Eastwood developers to track down when they have time and
interest.

## Changes from version 0.1.1 to 0.1.2

This appears to be a good change, which doesn't need fixing, but I
wanted to record it here since I believe I understand the reason for
the change.

With Eastwood 0.1.1, the crucible linting of namespace
clojure.stacktrace (and a few others in Clojure core) did not show any
reflection warnings.  With Eastwood 0.1.2, it does.

The warnings from Eastwood 0.1.2 for these Clojure core namespaces are
the same (or perhaps with a few minor differences) from those you see
if you build Clojure using ant after enabling reflection warnings in
build.xml.

I believe the reason they did not appear in 0.1.1 output, but they do
appear in 0.1.2 output, is because of the change that was made to fix
issue [#60](https://github.com/jonase/eastwood/issues/60).

clojure.stacktrace was already loaded at the beginning of linting in
both cases.  With Eastwood 0.1.1 this caused the eval of
analyzed-then-emitted-from-AST forms to be suppressed.  With that
change, the emitted forms are eval'd anyway, even though the namespace
has already been loaded previously.


## Changes from version 0.1.0 to 0.1.1

Good change, which doesn't need fixing (except perhaps tracking down
the few remaining differences in reflection warnings between Eastwood
and `lein check`): Many reflection warnings issued by Eastwood 0.1.0
that were incorrect are no longer issued in 0.1.1.  For example:

    # With Eastwood 0.1.0

    $ lein with-profile +1.6 eastwood '{:namespaces [ clojure.test ]}'
    == Eastwood 0.1.0 Clojure 1.6.0-master-SNAPSHOT JVM 1.7.0_51
    == Linting clojure.test ==
    Reflection warning, clojure/test.clj:1:251 - call to method nth can't be resolved (target class is unknown).
    Reflection warning, clojure/test.clj:1:251 - call to method nth can't be resolved (target class is unknown).

    # With Eastwood 0.1.1

    $ lein with-profile +1.6 eastwood '{:namespaces [ clojure.test ]}'
    == Eastwood 0.1.1-SNAPSHOT Clojure 1.6.0-master-SNAPSHOT JVM 1.7.0_51
    == Linting clojure.test ==


In addition, many reflection warnings that are still issued by
Eastwood 0.1.1 have more useful line:column numbers than with version
0.1.0: Example:

    # With Eastwood 0.1.0

    $ lein with-profile +1.6 eastwood '{:namespaces [ clojure.test.junit ]}'
    == Eastwood 0.1.0 Clojure 1.6.0-master-SNAPSHOT JVM 1.7.0_51
    == Linting clojure.test.junit ==
    Reflection warning, clojure/test/junit.clj:1:251 - call to method lastIndexOf can't be resolved (target class is unknown).
    Reflection warning, clojure/test/junit.clj:1:251 - call to method substring can't be resolved (target class is unknown).
    Reflection warning, clojure/test/junit.clj:1:251 - call to method substring can't be resolved (target class is unknown).

    [ ... other output deleted ... ]

    # With Eastwood 0.1.1

    $ lein with-profile +1.6 eastwood '{:namespaces [ clojure.test.junit ]}'
    == Eastwood 0.1.1-SNAPSHOT Clojure 1.6.0-master-SNAPSHOT JVM 1.7.0_51
    == Linting clojure.test.junit ==
    Reflection warning, clojure/test/junit.clj:84:11 - call to method lastIndexOf can't be resolved (target class is unknown).
    Reflection warning, clojure/test/junit.clj:87:8 - call to method substring can't be resolved (target class is unknown).
    Reflection warning, clojure/test/junit.clj:87:30 - call to method substring can't be resolved (target class is unknown).

    [ ... other output deleted ... ]


Linting contrib libraries core.memoize and core.cache often leads to
errors, because now tools.analyer.jvm uses them in its implementation
for performance enhancements.  Linting tools.analyzer or
tools.analyzer.jvm can still often lead to strange errors as before,
since they are used in Eastwood's implementation.

core.logic defines record? inconsistently with the new
clojure.core/record? in Clojure 1.6, leading to errors during linting.

Line:column locations that used to be reported with Eastwood 0.1.0 but
not by Eastwood 0.1.1.  There are many other :unlimited-use warnings
issued by Eastwood 0.1.1 that do have line:column number as with
0.1.0, but for some reason this one does not.  I am guessing it may
have something to do with the explicit metadata on the symbol
clojure.data.priority-map that is in this ns form that is not in most
ns forms.

    # With Eastwood 0.1.0
    $ lein with-profile +1.6 eastwood
    == Eastwood 0.1.0 Clojure 1.6.0-master-SNAPSHOT JVM 1.7.0_51
    == Linting clojure.data.priority-map ==
    {:linter :unlimited-use,
     :msg "Unlimited use of (clojure.test) in clojure.data.priority-map",
     :line 181,
     :column 5}

    # With Eastwood 0.1.1
    $ lein with-profile +1.6 eastwood
    == Eastwood 0.1.1-SNAPSHOT Clojure 1.6.0-master-SNAPSHOT JVM 1.7.0_51
    == Linting clojure.data.priority-map ==
    {:linter :unlimited-use,
     :msg "Unlimited use of (clojure.test) in clojure.data.priority-map",
     :line nil,
     :column nil}

New warning while linting tools.macro.  This warning is due to a
(comment ...) expression inside of a deftest.  I do not know why it
gave no warning with Eastwood 0.1.0, but unfortunately this is
difficult to avoid with the way that Eastwood analyzes comment forms
(which always evaluate to nil in Clojure).

    == Linting clojure.tools.test-macro ==
    [ ... many other warnings that have not changed deleted here ... ]
    {:linter :suspicious-test,
     :msg
     "Found constant form  with class nil inside deftest.  Did you intend to compare its value to something else inside of an 'is' expresssion?",
     :line nil,
     :column nil}

New warning while linting tools.reader, which is used by Eastwood so
perhaps this is just unavoidable strangeness:

    == Linting clojure.tools.reader.impl.utils ==
    WARNING: ex-info already refers to: #'clojure.core/ex-info in namespace: clojure.tools.reader.impl.utils, being replaced by: #'clojure.tools.reader.impl.utils/ex-info
    WARNING: ex-data already refers to: #'clojure.core/ex-data in namespace: clojure.tools.reader.impl.utils, being replaced by: #'clojure.tools.reader.impl.utils/ex-data
    {:linter :redefd-vars,
     :msg "Var ex-info? def'd 2 times at lines: 41 44",
     :line 44,
     :column 11}

Also new exception while linting tools.reader, again perhaps
unavoidable strangeness:

    == Linting clojure.tools.reader ==
    [ ... some lines deleted here ... ]
    Exception thrown during phase :analyze of linting namespace clojure.tools.reader
    Got exception with extra ex-data:
        msg='Can only recur from tail position'
        (keys dat)=(:file :line :column :exprs :form)
        (:form dat)=
    (^{:line 728, :column 33, :end-line 728, :end-column 38} recur)
    ExceptionInfo Can only recur from tail position
	    clojure.core/ex-info (core.clj:4403)
	    clojure.tools.analyzer/eval1045/fn--1048 (analyzer.clj:525)
	    clojure.lang.MultiFn.invoke (MultiFn.java:231)
