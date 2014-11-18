# Change log for Eastwood

## Changes planned for near future

* Implement a way to enable/disable linters on individual Clojure
  expressions.  Issue
  [#21](https://github.com/jonase/eastwood/issues/21).


## Changes from version 0.1.5 to 0.2.0

Version 0.2.0 is not released yet.  These changes are available in
latest master version only.

* Eastwood version 0.2.0 requires Clojure 1.5.0 or later.  Clojure
  1.4.0 is no longer supported.

* The new default lint warning format is no longer a map, but lines of
  the form "FILE:LINE:COL: LINTER MSG".  You can put only the warnings
  lines into a file using the new option `:out`.  Support already
  exists in Emacs, Vim, and probably other editors for stepping
  through the warnings, with another buffer/window jumping to the
  location of the warning for you.  See docs for more details.  Issue
  [#104](https://github.com/jonase/eastwood/issues/104).

* Enhanced `:suspicious-expression` linter so it always uses
  macroexpanded forms, not original source forms.  Thus it no longer
  produces incorrect warnings for expressions using `->` or `->>` like
  `(-> 1 (= 1))`, as it used to.  Issue
  [#93](https://github.com/jonase/eastwood/issues/93).

* New linter `:constant-test` that warns when a test expression in an
  `if`, `cond`, `if-let`, etc. is obviously a constant, or a literal
  collection that will always evaluate as true.  Issue
  [#77](https://github.com/jonase/eastwood/issues/77).

* New linter `:unused-meta-on-macro` that warns when metadata is used
  to annotate a macro invocation, but the Clojure compiler will ignore
  it because it is discarded during macro expansion.  Issue
  [#97](https://github.com/jonase/eastwood/issues/97).

* New linter `:unused-locals` that warns when a `let` binds values to
  symbols, but those symbols are never used.  Disabled by default.
  Issue [#106](https://github.com/jonase/eastwood/issues/106).

* New linter `:no-ns-form-found` that warns about each Clojure file
  found where Eastwood could not find an `ns` form.  Most likely that
  is because there is none, but more unusually there could be an `ns`
  form that is nested inside of another top level form.

* New linter `:non-clojure-file` that warns about each non-Clojure
  file found in your `:source-paths` or `:test-paths`, if you specify
  those in the list of namespaces to lint, or leave them there as the
  default.  Issue
  [#102](https://github.com/jonase/eastwood/issues/102).

* Corrections to the `:wrong-tag` linter where it was throwing
  exceptions while linting some projects -- ones that had not been
  tested before Eastwood 0.1.5 release.

* Corrected a case where `:unlimited-use` warnings were still being
  issued for the namespace `clojure.test`, which was `(:use [clojure
  test])`.  Issue [#95](https://github.com/jonase/eastwood/issues/95).

* For namespace / file name consistency check error messages, made the
  message a little clearer for common special case of file name having
  '-' characters instead of '_'.  Issue
  [#103](https://github.com/jonase/eastwood/issues/103).

* Improved line/column location info for a few `:unused-ret-val`
  warnings.

* Updated versions of several libraries used: `tools.analyzer` 0.6.3 +
  a few commits, `tools.analyzer.jvm` 0.6.4 + a few commits,
  `core.memoize` 0.5.6 + a few commits, `core.cache` 0.6.4.  Issues:
  [#100](https://github.com/jonase/eastwood/issues/100).

* Added several projects to the 'crucible' set of projects on which
  Eastwood is regularly tested: Instaparse, Schema, Plumbing, Carmine,
  Compojure.


## Changes from version 0.1.4 to 0.1.5

* New linter `:local-shadows-var` that warns if a local name (e.g. a
  function argument or let binding) has the same name as a global Var,
  and is called as a function.  This is sometimes a mistake.  Issue
  [#81](https://github.com/jonase/eastwood/issues/81).

* New linter `:wrong-tag` that warns for some kinds of erroneous type
  tags.  For example, a primitive type tag like `^int` or `^bytes` on
  a Var name being `def`'d or `defn`'d should be given as `^{:tag
  'int}` instead.  Also it is best if Java class names outside of the
  `java.lang` package are fully qualified when used to hint the return
  type of a function on its argument vector.  Introducing this linter
  depends upon changes to `tools.analyzer.jvm` that no longer throw
  exceptions when linting source code with these problems.  Issue
  [#37](https://github.com/jonase/eastwood/issues/37).

* New API for running Eastwood from a REPL session, nearly identical
  to what is available from the command line.  This should be used
  with caution due to problems that might result if you run Eastwood
  multiple times from the same REPL session, because it reloads and
  re-evaluates your code each time.  Issue
  [#56](https://github.com/jonase/eastwood/issues/56).

* The default behavior is now to stop analyzing namespaces after the
  first exception thrown during analysis or evaluation.  The new
  option `:continue-on-exception` can be set to true to force the old
  behavior.  The new stopping behavior prevents some spurious warnings
  about undefined Vars that can be confusing to users.  Issue
  [#79](https://github.com/jonase/eastwood/issues/79).

* `:unlimited-use` warnings are no longer issued for the namespace
  `clojure.test`.  It is very common for Clojure developers to have
  `(:use clojure.test)` in test namespaces.  Issue
  [#95](https://github.com/jonase/eastwood/issues/95).

* `:suspicious-expression` warnings are no longer issued for forms
  inside quote forms.  Issue
  [#74](https://github.com/jonase/eastwood/issues/74).

* The warning messages for linters `:unused-fn-args` and
  `:unused-ret-vals` have changed slightly to remove details that were
  not easy to continue to provide with the newest `tools.analyzer`
  libraries.  On the plus side, some of the line and column numbers
  are now more precise than they were before with those linters, and
  also for the `:suspicious-test` linter.

* Lint warning maps contain a new key `:uri-or-file-name`, which has
  the advantage of making it easier to know the exact directory of the
  file being linted when the warning was found, including the
  directory of your classpath that the file is in (the `:file` name
  string is relative to the classpath the file is in).  The value of
  `:uri-or-file-name` is either: (a) a string with the relative path
  name to the file containing the namespace being linted when the
  warning was found (relative to where the `lein eastwood` command was
  run, (b) the absolute full path name if the file is not beneath the
  current directory, or (c) a URI like the following if it is a
  namespace inside of a JAR file:

    `#<URI jar:file:/Users/jafinger/.m2/repository/org/clojure/clojure/1.6.0/clojure-1.6.0.jar!/clojure/test/junit.clj>`

* Eliminated an exception thrown when running the `:suspicious-test`
  linter on forms like `(clojure.test/is ((my-fn args) more-args))`,
  with an expression `(my-fn args)` instead of a symbol `my-fn` as the
  first item.  Issue
  [#88](https://github.com/jonase/eastwood/issues/88).

* Eastwood 0.1.4, and perhaps a few earlier versions as well,
  unintentionally 'hid' exceptions thrown by the Clojure compiler
  while doing `eval`.  Such exceptions are now made visible to the
  user, which makes errors occur earlier, and closer to the actual
  source of the problem.

* Updated versions of several libraries used: `tools.analyzer` 0.6.2,
  `tools.analyzer.jvm` 0.6.3, `tools.reader` 0.8.10, `tools.namespace`
  0.2.7

* Added some new utility/debug functions for showing ASTs trimmed of
  unwanted data, and sorting the keys in orders that put the most
  important values early.

* Made some Eastwood code simultaneously simpler, shorter, and more
  reliable by using a `tools.analyzer(.jvm)` enhancement that
  preserves the original forms of code that goes through macro
  expansion.  Issue
  [#71](https://github.com/jonase/eastwood/issues/71).

* Modified `pprint-ast-node` to avoid an infinite loop for ASTs of
  code containing `defprotocol` forms.  Issue
  [#90](https://github.com/jonase/eastwood/issues/90).

* Extensive internal plumbing changes: replace many `println` calls
  with a probably-too-complex callback function instead.  The hope is
  that this will make it easier for IDE developers to invoke Eastwood
  and control where the different kinds of output go, and get more of
  it as Clojure data rather than strings.


## Changes from version 0.1.3 to 0.1.4

* Only report `:suspicious-test` warnings for `(is ...)` forms if the
  `is` refers to the one defined in `clojure.test`.  This is
  especially helpful to users of `core.typed`, which defines its own
  meaning for `(is ...)` forms.  These often caused false warnings
  with previous Eastwood versions.  Issue
  [#63](https://github.com/jonase/eastwood/issues/63).

* No longer report `nil` values as `:unused-ret-vals` warnings if they
  arise due to the expansion of `gen-class` or `comment` macros.
  Issue [#39](https://github.com/jonase/eastwood/issues/39).

* Improved precision of `:line` and `:column` values in
  `:unlimited-use` warnings.

* Several small changes while working towards being able to lint the
  `core.typed` Clojure contrib library.  First was an algorithmic
  speedup from exponential to linear time in `tools.namespace`
  dependency analysis, which was only first noticed with
  `core.typed`'s inter-namespace dependencies.  Second was creating
  all namespaces before beginning linting, to handle an unusual
  occurrence of `alias` in namespace `B` to namespace `A`, where `A`
  required `B`.  More work still needed here.

* Updated `tools.analyzer` and `tools.analyzer.jvm` to version 0.2.2.

* `:debug` option `:ns` was removed when introducing the use of
  `tools.analyzer.jvm/analyze+eval`.  It may be added back in again
  later.  Other debug options were temporarily removed, then added
  back in, although in some cases forms are now pprint'ed without
  metadata keys such as `:column`, `:end-line`, `:end-column`, and
  `:file`, for brevity.


## Changes from version 0.1.2 to 0.1.3

* Added file name to all linter warnings.  Issue
  [#64](https://github.com/jonase/eastwood/issues/64).

* Added column numbers to :redefd-vars warnings.

* Handle "./" at beginning of :source-paths or :test-paths dir names.
  Fixes issue [#66](https://github.com/jonase/eastwood/issues/66).

* Most of the Clojure contrib libraries upon which Eastwood depends
  are now copied into Eastwood itself, and then renamed to have
  different namespace names.  This helps to avoid potential conflicts
  between the version used by Eastwood, and the version used by
  Clojure projects being linted.  Fixes issue
  [#67](https://github.com/jonase/eastwood/issues/67).

* Updated `tools.analyzer` and `tools.analyzer.jvm` to version
  0.1.0-beta13.

* Updated `data.priority-map` to 0.0.5, and `core.memoize` to 0.5.6
  plus a local patch, both to avoid spurious reflection warnings from
  Eastwood itself.

* Added :compare-forms debug option, only intended for use by Eastwood
  developers for debugging Eastwood itself.  Causes Eastwood to write
  two files forms-read.txt and forms-emitted.txt.  forms-read.txt
  contains the forms after they have been read, top-level do forms are
  recognized and each subform analyzed separately, and after calling
  macroexpand-1 on them, at the top level only.  forms-emitted.txt
  contains the forms after all of those steps, plus being analyzed
  with tools.analyzer(.jvm) to produce an AST, and then emit-form
  called on the AST to produce a form.


## Changes from version 0.1.1 to 0.1.2


* Updated `tools.analyzer` and `tools.analyzer.jvm` to version
  0.1.0-beta10.  Most of the Eastwood issues fixed since Eastwood
  0.1.1 were due to this change.

* Changed method of analyzing code that was throwing exception with
  some projects, e.g. kria.  Fixes issue
  [#60](https://github.com/jonase/eastwood/issues/60), and I think
  this same fix also corrected issue
  [#54](https://github.com/jonase/eastwood/issues/54)

* Fixed analysis problem that caused Eastwood to throw exceptions when
  analyzing [Midje](https://github.com/marick/Midje), and test code of
  libraries that used Midje.  Fixes issue
  [#61](https://github.com/jonase/eastwood/issues/61)

* New functions and macros added to Clojure 1.6.0 will now cause
  :unused-ret-vals or :unused-ret-vals-in-try warnings if they are
  called and their return value is ignored, just as that happens for
  other functions in clojure.core.  Fixes issue
  [#59](https://github.com/jonase/eastwood/issues/59)

* Updates to scripts and files used to test Eastwood, of interest only
  to Eastwood developers.  Now it is straightforward to ensure that
  you get the same version of project source code if you use the test
  scripts on multiple machines, instead of getting whatever the latest
  happened to be at the time you ran the clone.sh script.


## Changes from version 0.1.0 to 0.1.1


* Added consistency checking between namespace and file names before
  actual linting begins, to avoid hard-to-understand error messages
  that could otherwise result.  See [this
  section](https://github.com/jonase/eastwood/#check-consistency-of-namespace-and-file-names)
  in the docs.

* Added `:bad-arglists` linter.  See [this
  section](https://github.com/jonase/eastwood/#bad-arglists---functionmacro-definitions-with-arg-vectors-differing-from-their-arglists-metadata)
  in the docs.

* No longer issue warnings for code inside of `comment` forms.  Fixes
  issue [#47](https://github.com/jonase/eastwood/issues/47)

* `lein help` now gives one-line description of Eastwood plugin, and
  `lein eastwood help` gives a link to the full documentation, and
  help about the same as that in the "Installation & Quick Usage"
  section.

* Reflection warnings appearing in output of `lein eastwood` should
  now be much closer to those produced by Clojure itself, and usually
  include useful line:column numbers.  There may still be some
  differences, so reflection warnings in the output of `lein check`
  are still the ones you want to trust, if there are any differences.

* Updated `tools.analyzer` and `tools.analyzer.jvm` to version
  0.1.0-beta8.  Updated some Eastwood code as a result of changes in
  those libraries.  (Eastwood version 0.1.0 used version 0.1.0-alpha1
  of those libraries).

* Top level `do` forms are now analyzed by Eastwood similarly to how
  Clojure itself does it, as if the forms inside the `do` were
  themselves independent top level forms.  See the article ["in which
  the perils of the gilardi scenario are
  overcome"](http://technomancy.us/143) for some description of why
  Clojure does this.  Fixes issue
  [#49](https://github.com/jonase/eastwood/issues/49)

* Correctly detect `gen-interface` forms even if invoked using
  `clojure.core/gen-interface`.
  [Link](https://github.com/jonase/eastwood/commit/fa61e5f4400c2fd334b87634c31c5c1270f3b9f6)
  to the commit.

* Updates to scripts and files used to test Eastwood, of interest only
  to Eastwood developers.
