# Change log for Eastwood


## Changes from version 0.1.3 to 0.1.4


Note: Eastwood 0.1.4 is not released yet.  The things below are
currently planned to be included in that release, but that may change
before the release is made.

* No longer report `nil` values as `:unused-ret-vals` warnings if they
  arise due to the expansion of `gen-class` or `comment` macros.
  Issue [#39](https://github.com/jonase/eastwood/issues/39).

* Only report `:suspicious-test` warnings for `(is ...)` forms if the
  `is` refers to the one defined in `clojure.test`.  This is
  especially helpful to users of `core.typed`, which defines its own
  meaning for `(is ...)` forms.  These often caused false warnings
  with previous Eastwood versions.  Issue
  [#63](https://github.com/jonase/eastwood/issues/63).

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
