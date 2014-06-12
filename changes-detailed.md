# Detailed changes in Eastwood behavior

Intended for Eastwood developers to track down when they have time and
interest.

## Changes from version 0.1.3 to 0.1.4

The new way planned to check for (is ...) expressions, so that it only
lints them if they are clojure.test/is, by using the :raw-forms and
:eastwood/partly-resolved-forms values in the AST, causes some lint
warnings to no longer be printed.  Here are some of them:

* The last deftest called state-seq-monad in algo.monad's file
  test_monads.clj.  This appears to be because :raw-forms includes
  macro expansions up to an invocation of
  clojure.tools.macro/with-symbol-macros, but after that the (is)
  macro is expanded along with everything else, or something like
  that.  Probably t.a.j/analyze+eval's method of doing macroexpand-1
  doesn't handle symbol macros.

* core.logic-2014-02-21 namespace clojure.core.logic.nominal.tests had
  a :suspicious-test warning before about nil inside a deftest,
  probably due to a (comment ...) inside of a deftest.  I didn't
  explicitly try to make this no longer appear, but it does not.  That
  is good, if it is for the right reason.

* namespace clojure.java.test-jmx has a new warning about a non-string
  as second arg at line 142.  The second arg is a symbol whose value
  happens to be a string, I can tell, but I can see that this is like
  other cases where it is not easy for Eastwood to determine that.  It
  probably did not occur in the output before because the 'is' macro
  invocation is inside of a doseq.

* Namespace ogre.transform.traversal-test used to have 2
  :suspicious-test warnings that no longer appear.  This is probably
  due to an exception being thrown before analysis completes.  This
  did not prevent the warnings before because they were done purely at
  the source code form level, not on ASTs.

* Many new :unused-ret-vals warnings in namespace potemkin.  I don't
  know why yet.

* Namespace reply.reader.jlin.JlineInputReader no longer has spurious
  :unused-ret-vals warning due to use of :gen-class in ns form.  Good!

* Namespace clojure.tools.test-macro no longer has a spurious
  :suspicious-test warning due to (comment) inside deftest.  Good!

* Namespace clojure.tools.reader.impl.ExceptionInfo no longer has
  spurious :unused-ret-vals warning due to use of :gen-class in ns
  form.  Good!

* Namespace flatland.useful.deftype no longer throws a "Method code
  too large!" exception.  Good.  But now it throws an exception
  "Attempting to call unbound fn: #'flatland.useful.deftype/defmap".
  Not sure why it would do that.

* Namespace flatland.useful.utils-test no longer shows two
  :suspicious-test warnings that it did before.  It appears that it
  *should*.  Investigate why they are no longer there.

* Exception thrown during analyze+eval of namespace clojure.reflect.
  This has not occurred before.  Figure out why.


## Changes from version 0.1.2 to 0.1.3

GOOD CHANGE: The formerly-normal 2 reflection warnings from
clojure/data/priority_map.clj and clojure/core/memoize.clj are now
gone, because these libraries have been copied into Eastwood, and
updated to no longer generate those reflection warnings.  Note that
for projects that use these versions of core.memoize and/or
data.priority-map, they now have their own reflection warnings for
these namespaces, whereas before these were 'masked' by the ones from
Eastwood.

GOOD CHANGE: clj-ns-browser-2013-03-04 formerly caused an exception to
be thrown early during linting, due to that project using a version of
tools.namespace with a different API than the version used by
Eastwood.  Eastwood copying tools.namespace and renaming its namespace
helps in 0.1.3 to do normal linting of this project.

Similarly for these projects:
+ core.cache-2014-01-31
+ core.memoize-2013-08-13

GOOD CHANGE: Linting data.priority-map-2014-03-20 had an
:unlimited-use warning that used to have :line nil and :column nil,
but now it has numbers there instead of nil, and they are correct.
This is probably because of the same change, and thus now Eastwood's
reader is actually reading the source code from the files, instead of
skipping reading them because the namespaces were already there?
Something like that, I would guess.

Similarly in these projects:
+ tools.reader-2014-03-05


COULD BE BETTER: core.constracts-2013-07-24 has a :redefd-vars
warning, now with file names and column numbers, that gives wrong a
line number in a file that does not have that many lines in it.
Probably some macro expansion issue that Eastwood doesn't handle well.

WEIRD: core.logic-2014-02-21 throws IllegalStateException in both
versions, but in 0.1.3 the "Shown again with metadata for debugging"
has no :line :column etc. metadata, whereas it did in 0.1.2.  I don't
know yet why this change occurs.

Similarly for these projects:
+ ogre-(tbd-date-here)
+ potemkin - difference here is *no* output at all for with-metadata version
+ useful-2013-11-19 - difference here is part of output expression
  does not appear at all, which might be similar to potemkin change


BUG?: java.jdbc-2014-03-07 used to have these warnings in the output,
but no longer.  I do not know why, but suspect it is due to some kind
of change in tools.analyzer(.jvm).

    Error: Eastwood found no instance method named getTables for class java.sql.DatabaseMetaData taking 4 args with types (java.lang.Object, java.lang.Object, java.lang.Object, [Ljava.lang.String;).  This may occur because Eastwood does not yet do type matching in the same way that Clojure does.
    Error: Eastwood found no instance method named getTables for class java.sql.DatabaseMetaData taking 4 args with types (java.lang.Object, java.lang.Object, java.lang.Object, [Ljava.lang.String;).  This may occur because Eastwood does not yet do type matching in the same way that Clojure does.

BUG?: seeaw-2014-02-21 used to have this warning in the output, but no
longer.  Seems similar to the java.jdbc change.

    Error: Eastwood found no instance method named addStyle for class javax.swing.JTextPane taking 2 args with types (java.lang.String, java.lang.Object).  This may occur because Eastwood does not yet do type matching in the same way that Clojure does.


BUG?: tools.analyzer.jvm-2014-03-11 formerly had fairly clean output
from Eastwood, but now I see this exception.  I do not know yet why
this new behavior occurs.

Additional info: tools.analyzer.jvm-2014-03-11/project.clj depends
upon version 0.1.0-SNAPSHOT of tools.analyzer.  Thus it depends upon
what version of tools.analyzer happens to be in my local Maven repo at
the time.  By doing 'lein install' on the version of tools.analyzer in
the crucible repo first, this problem about not finding rseqv
disappears.

    == Linting clojure.tools.analyzer.passes.jvm.clear-locals ==
    Exception in thread "main" java.lang.IllegalAccessError: rseqv does not exist, compiling:(/private/var/folders/c1/gpfcdwt14075pr80tsshkws40000gn/T/form-init5513266052110466476.clj:1:142)
    Caused by: java.lang.IllegalAccessError: rseqv does not exist
	    ... 11 more
    Error encountered performing task 'eastwood' with profile(s): 'default,1.6'
    Subprocess failed

BUG?: Perhaps similar is a new exception in useful-2013-11-19 shown
below:

Additional info: This error does not occur if you lint only the
namespace flatland.useful.deftype-test.  The function alist is defined
in namespace flatland.useful.deftype, and Eastwood throws an exception
while linting that namespace, probably before successfully defining fn
alist, thus causing the problem below later on.

    == Linting flatland.useful.deftype-test ==
    {:linter :unlimited-use,
     :msg
     "Unlimited use of (clojure.test flatland.useful.deftype) in flatland.useful.deftype-test",
     :line 1,
     :column 5}
    
    Exception thrown during phase :analyze of linting namespace flatland.useful.deftype-test
    Got exception with extra ex-data:
        msg='Could not resolve var: alist'
        (keys dat)=(:file :column :line :var)
    ExceptionInfo Could not resolve var: alist

BUG?: Project useful-2013-11-19 now also throws "RuntimeException
Method code too large!" that it did not throw before while linting
namespace flatland.useful.deftype.  I do not know the reason, but
would guess it might be because of line/col metadata being inside of a
backquote expression and getting expanded out into something even
larger than it was in the older version.

BUG IN TOOLS.READER?: Project useful-2013-11-19 used to have bad
line/col numbers for some warnings, and they have been made better.
Perhaps this is due to bug fixes in tools.reader, or Eastwood's new
use of the IndexingReader to get the file names in metadata.


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

core.cache-2014-01-31 had change in exceptions thrown.

With Eastwood 0.1.1 the first exception was this:

    == Linting clojure.core.cache ==
    Exception thrown during phase :analyze of linting namespace clojure.core.cache
    Got exception with extra ex-data:
        msg='Could not resolve var: cache'
        (keys dat)=(:file :column :line :var)
    ExceptionInfo Could not resolve var: cache
	    clojure.core/ex-info (core.clj:4403)
	    clojure.tools.analyzer.passes.jvm.validate/eval2644/fn--2646 (validate.clj:32)
	    clojure.lang.MultiFn.invoke (MultiFn.java:227)
	    clojure.tools.analyzer.passes.jvm.validate/validate (validate.clj:237)
	    eastwood.analyze-ns/run-passes/analyze--3856/fn--3857 (analyze_ns.clj:186)

With Eastwood 0.1.2 the first exception is this:

    == Linting clojure.core.cache ==
    Exception thrown during phase :analyze of linting namespace clojure.core.cache
    IllegalArgumentException No implementation of method: :has? of protocol: #'clojure.core.cache/CacheProtocol found for class: clojure.core.memoize.PluggableMemoization
	    clojure.core/-cache-protocol-fn (core_deftype.clj:544)
	    clojure.core.cache/eval5394/fn--5395/G--5331--5398 (form-init1529996205346131197.clj:1)
	    clojure.core.cache/through (form-init1529996205346131197.clj:53)
	    clojure.core.memoize/through* (memoize.clj:52)
	    clojure.lang.Atom.swap (Atom.java:65)
	    clojure.core/swap! (core.clj:2234)

I do not know the reason for the change yet, but core.cache typically
has strange behavior during linting, especially if the version is
different than the one on which Eastwood itself depends.

Extra line numbers in :redefd-vars warnings from Eastwood 0.1.2:

    == Linting clojure.core.constraints-tests ==
    {:linter :redefd-vars,
     :msg "Var ->Foo def'd 2 times at lines: 154 125",
     :line 125,
     :column 6}
    
    {:linter :redefd-vars,
     :msg "Var map->Foo def'd 2 times at lines: 154 160",
     :line 160,
     :column 8}

Eastwood 0.1.1 did not have the 154 numbers in the output.  Those line
numbers are not terribly helpful in either of these cases, since they
are from macro definitions in a different file from the one that
caused the warnings.  This was due to one of the last commits to
tools.analyzer before the 0.1.0-beta10 release, or perhaps to
tools.analyzer.jvm.

Some small differences in the output of exceptions thrown while
processing core.logic.  I am pretty sure that this is due to Eastwood
0.1.1 having a special case for analyzing ns forms as a whole, whereas
0.1.2 treats them like any other form, i.e. macroexpand them and if it
expands to a do form, analyze each subform independently before the
next subform.  Similar changes in exception messages occur for other
crucible projects, too.

core.memoize-2013-08-13 throws an exception with Eastwood 0.1.1 in
namespace clojure.core.memoize that Eastwood 0.1.2 does not.  Not sure
exactly which change caused this, but there are plenty of other
differences in the output, and core.memoize being an Eastwood
dependency is often troublesome.

Several reflection warnings produced by Eastwood 0.1.1 no longer occur
in Eastwood 0.1.2:

    == Linting clojure.core.rrb-vector ==
    Reflection warning, clojure/core/rrb_vector.clj:100:17 - call to method cons on java.lang.Object can't be resolved (no such method).
    Reflection warning, clojure/core/rrb_vector.clj:150:17 - call to method cons on java.lang.Object can't be resolved (no such method).

These were due to Nicola fixing issues with tools.analyzer(.jvm),
probably one of these:

* [TANAL-75](http://dev.clojure.org/jira/browse/TANAL-75)
* [TANAL-78](http://dev.clojure.org/jira/browse/TANAL-78)

Many warnings have improved line:column numbers, strangely enough.
This was due to a tools.analyzer(.jvm) change.

kria-2014-03-19 threw many exceptions with Eastwood 0.1.1, and no
longer throws any with Eastwood 0.1.2.

potemkin-2014-03-20 analyzed without exceptions with Eastwood 0.1.1,
but throws many exceptions with Eastwood 0.1.2.  This is due to some
changes Nicola made to tools.analyzer(.jvm) shortly before Mar 29
2014, knowing this breakage would occur.  He submitted a pull request
to potemkin that would enable it to analyze successfully again:

    https://github.com/ztellman/potemkin/pull/20

ogre-2014-03-11 throws exceptions after this change, too, due to its
use of the potemkin library.

An exception thrown by Eastwood 0.1.1 analyzing
tools.analyzer-2014-03-22 namespace clojure.tools.analyzer.query-test
no longer occurs.

tools.reader-2014-03-05 had these warnings with Eastwood 0.1.1 that
are now gone with 0.1.2:

    == Linting clojure.tools.reader.impl.utils ==
    WARNING: ex-info already refers to: #'clojure.core/ex-info in namespace: clojure.tools.reader.impl.utils, being replaced by: #'clojure.tools.reader.impl.utils/ex-info
    WARNING: ex-data already refers to: #'clojure.core/ex-data in namespace: clojure.tools.reader.impl.utils, being replaced by: #'clojure.tools.reader.impl.utils/ex-data
    {:linter :redefd-vars,
     :msg "Var ex-info? def'd 2 times at lines: 41 44",
     :line 44,
     :column 11} 

Similarly for several other namespaces in tools.reader-2014-03-05.  An
exception thrown while analyzing namespace clojure.tools.reader also
no longer occurs:

    Exception thrown during phase :analyze of linting namespace clojure.tools.reader
    Got exception with extra ex-data:
        msg='Can only recur from tail position'
        (keys dat)=(:file :line :column :exprs :form)
        (:form dat)=
    (^{:line 728, :column 33, :end-line 728, :end-column 38} recur)
    ExceptionInfo Can only recur from tail position

When analyzing the namespace below in Clojure core using Eastwood
0.1.1, there were no reflection warnings except 'the usual ones' from
data.priority-map and core.memoize.  With Eastwood 0.1.2 there are the
same reflection warnings you see when you enable warnings and compile
Clojure itself.

    clojure.instant
    clojure.main
    clojure.repl
    clojure.stacktrace

I am nearly certain that this is because of the change Nicola made to
Eastwood where it no longer disables eval of emitted forms if the
namespace was already loaded.  These namespaces were already loaded,
and with Eastwood 0.1.1 that caused their emitted forms not to be
eval'd during analysis, but with Eastwood 0.1.2 they are eval'd
anyway.  Thus the reflection warnings.


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
