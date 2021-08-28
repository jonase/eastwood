## Changes from 0.9.7 to 0.9.8

#### New

* Implement a new linter: `:performance`
  * Note that it's disabled by default. There are no drawbacks to enabling it (other than the burden of having to fix or silence these).
  * Closes https://github.com/jonase/eastwood/issues/416
  * [Documentation](https://github.com/jonase/eastwood#performance)
* Improve Eastwood performance substantially.
  * Various redundant work is avoided or DRYed.
  * Various operations are now (safely) parallelized and cached.
    * Caching works on a per-run manner, so separate invocations from REPL will remain isolated from one another.
* Add `:kind`s to all remaining linters.
  * [Documentation](https://github.com/jonase/eastwood#ignoring-linter-sub-kinds)
  * Closes https://github.com/jonase/eastwood/issues/418

#### Bugfixes

* Handle `:refer-clojure :exclude` better in `:suspicious-test`.
  * Closes https://github.com/jonase/eastwood/issues/185
* Prevent occasional issues related to ns-interns when using Eastwood from a REPL
  * Closes https://github.com/jonase/eastwood/issues/419
* var-info.edn: mark various side-effectful fns as such
  * This silences a few `:unused-ret-vals` false positives.
  * Fixes https://github.com/jonase/eastwood/issues/421
* Exclude Lein checkouts' directories from the inferred `:source-paths` (source path inference being Eastwood's fallback to a lack of explicitly specified `:source-paths` / `:test-paths`). 

## Changes from 0.9.6 to 0.9.7

#### New

* Implement ability to omit a specific linter `:kind`.
  * Closes https://github.com/jonase/eastwood/issues/387
  * [Documentation](https://github.com/jonase/eastwood#ignoring-linter-sub-kinds)
* Now the `:second-arg-is-not-string` sub-kind for the `:suspicious-test` is disabled by default.
  * Rationale: https://github.com/jonase/eastwood/issues/387
  * You can re-enable it by passing `:exclude-linters #{}`
  * [Learn more about sub-kind silencing](https://github.com/jonase/eastwood#ignoring-linter-sub-kinds)

#### Bugfixes

* Silence `:wrong-arity` false positives for honeysql the time being.
  * Part of https://github.com/jonase/eastwood/issues/399

## Changes from 0.9.4 to 0.9.6

#### Bugfixes

* Fix various false positives which could arise when using the `clojure.core.async/go` macro.
  * Fixes https://github.com/jonase/eastwood/issues/411
* Upgrade `tools.reader`
  * Closes https://github.com/jonase/eastwood/issues/297
  * Fixes https://github.com/jonase/eastwood/issues/413 

## Changes from 0.9.3 to 0.9.4

#### Bugfixes

* Honor `:exclude-namespaces` for the `:reflection` and `:boxed-math` linters.

## Changes from 0.9.2 to 0.9.3

#### New

* deps.edn integration: don't lint files under `~/.gitlibs`.
  * These files represent third-party dependencies, so while they should be taken into account for analysis, typically the shouldn't be linted.
  * Closes https://github.com/jonase/eastwood/issues/409

#### Bugfixes

* Fix a NullPointerException.

## Changes from 0.9.1 to 0.9.2

#### Bugfixes

* Fix integration of the `:reflection` linter with the `:ignored-faults` mechanism.

## Changes from 0.9.0 to 0.9.1

#### Bugfixes

* Fix an implementation detail which could throw exceptions over .cljc files.
* Silence a `println` over a certain code path.
* Improve `:reflection` linter for an edge case related to .cljc files.

## Changes from 0.8.1 to 0.9.0

#### New

* Implement a new linter: `:reflection`
  * See https://github.com/jonase/eastwood#reflection
  * Part of https://github.com/jonase/eastwood/issues/133
* Implement a new linter: `:boxed-math`
  * See https://github.com/jonase/eastwood#boxed-math
  * Part of https://github.com/jonase/eastwood/issues/133
* Enable and refine `:non-dynamic-earmuffs`
  * See https://github.com/jonase/eastwood#non-dynamic-earmuffs
  * It used to be disabled by default.
  * Now It lints for both missing earmuffs and missing `^:dynamic` metadata. 

#### Bugfixes

* Consider `doall` side-effectful (for the `:unused-ret-vals` linter).
* Omit the `:no-ns-form-found` linter for Eastwood config files.
  * Closes https://github.com/jonase/eastwood/issues/406
* Analyze files in topographical order.
  * This generally happens naturally, but now is emphasized.
  * This way, it is less likely that `defprotocol` analysis causes issues.
  * Closes https://github.com/jonase/eastwood/issues/407

## Changes from 0.7.1 to 0.8.1

#### New

* Linter warnings ultimately coming from third-party macroexpansions (that is, the given macro resides outside `src/` `test/` or such) will be silenced, for most linters where doing so is low-risk.
  * This way, Eastwood defaults to linting _your_ code, not code from your dependencies.
  * You can revert to the old behavior by setting the `:ignore-faults-from-foreign-macroexpansions? false` global option.
  * Closes https://github.com/jonase/eastwood/issues/394

## Changes from 0.7.0  to 0.7.1

#### Bugfixes

* Fix a NullPointerException.
  * Closes https://github.com/jonase/eastwood/issues/404

## Changes from 0.6.0 to 0.7.0

#### New

* One now can override the exit code with the `:forced-exit-code 0` option.
  * That can be helpful when wanting to see the results of linting merely for informative purposes.
* Now `:unused-ret-vals`/`:unused-ret-val-on-try` don't trigger warnings so easily for static method calls.
  * For static method calls (i.e. Java interop), these linters are based on a hardcoded list (`jvm-method-info.edn`) describing their side-effects. Now, if a method isn't found in said list it will be considered side-effectful, favoring false negatives over false positives.
* Now `:unused-ret-vals`/`:unused-ret-val-on-try` also lint instance method calls.
  * Before, they only linted static method calls (aside from vanilla Clojure calls/exprs)
  * Similarly to the previous bullet point, these new linters won't fail easily in face of missing method information. 
* Now the `:deprecations` linter won't emit faults if the deprecated defn and a given consumer defn belong to the same ns.
  * Closes https://github.com/jonase/eastwood/issues/402

#### Bugfixes

* Fix false positive `:unused-ret-vals` for `clojure.core/read`
  * Closes https://github.com/jonase/eastwood/issues/403

## Changes from 0.5.2 to 0.6.0

#### New

* Omit exceptions coming from individual top-level forms, if they made use of the `clojure.core.async/go` macro.
  * Closes https://github.com/jonase/eastwood/issues/395
  * This makes Eastwood more capable of analyzing core.async -based projects. It's a temporary measure though, as it ideally this would be simply fixed in the tools.analyzer project. 
  * You can revert to the old behavior (which most likely will result in errors) by passing `:abort-on-core-async-exceptions? true` as a top-level Eastwood option. 
* Don't exit succesfully when no namespaces were linted.
  * This could happen on misconfigured `:source-paths`, etc.
  * Closes https://github.com/jonase/eastwood/issues/400
* Make the `:wrong-arity` linter omittable via the `disable-warning` mechanism.

#### Bugfixes

* Fix the `eastwood.lint` `-main` program when invoked with no arguments.
* Support `(class (byte-array 0))` as an `extend-protocol` target without it triggering `:wrong-tag`.

## Changes from 0.5.1 to 0.5.2

#### Bugfixes

* Fix false positives for `some->`, `some->>`
  * Closes https://github.com/jonase/eastwood/issues/397

## Changes from 0.5.0 to 0.5.1

#### Bugfixes

* Fix an implementation detail which could throw exceptions over .cljc files.

## Changes from 0.4.3 to 0.5.0

#### New

* Make the `:constant-test` linter available for `if-some`, `when-some`
 * Closes https://github.com/jonase/eastwood/issues/110
* Update var-info.edn
  * This increases overall accurancy.
  * Closes https://github.com/jonase/eastwood/issues/236
* Make the `:unused-fn-args` linter omittable via the `disable-warning` mechanism
  * Reminder: this linter is disabled by default anyway.
* Make the `:unused-fn-args` linter play better with multimethods
  * Closes https://github.com/jonase/eastwood/issues/1
* Consistently use punctuation in linting messages
  * Closes https://github.com/jonase/eastwood/issues/146

#### Bugfixes

* Make the `:implicit-requires` linter work corectly in presence of dynamic `require` forms
  * Closes https://github.com/jonase/eastwood/issues/22

## Changes from 0.4.1 to 0.4.3

#### New

* Support `(is false)` pattern
  * Closes https://github.com/jonase/eastwood/issues/384
* Support `(while true)` pattern
  * Closes https://github.com/jonase/eastwood/issues/386

#### Bugfixes

* Fix a false positive for `let` destructuring
  * Closes https://github.com/jonase/eastwood/issues/383
* Fix a false positive for `extend-protocol`
  * Closes https://github.com/jonase/eastwood/issues/385
* Support Manifold's `let-flow`
  * Closes https://github.com/jonase/eastwood/issues/197
* Fix various false positives
  * These could be triggered, under certain conditions, by `are`, `with-out-str`, some external libraries, etc.

## Changes from 0.4.0 to 0.4.1

#### New

* Now the `:wrong-tag` linter can also be configured via the [`disable-warning`](https://github.com/jonase/eastwood#eastwood-config-files) mechanism.
  * Related: the `disable-warnings` that Eastwood ships by default now prevent false positives against the [speced.def](https://github.com/nedap/speced.def) lib. 
* Now the `:suspicious-test` linter can also be configured via the [`disable-warning`](https://github.com/jonase/eastwood#eastwood-config-files) mechanism.
  * Relatedly, a certain pattern of usage of the `clojure.test/are` macro now does not trigger a linter fault. 
* Now the `:unused-meta-on-macro` linter can also be configured via the [`disable-warning`](https://github.com/jonase/eastwood#eastwood-config-files) mechanism.
  * Closes https://github.com/jonase/eastwood/issues/303
* If you invoke `eastwood.lint/eastwood` programatically, now a new key is offered: `:some-errors`, akin to `:some-warnings`.
  * This allows to distinguish why did Eastwood fail.

#### Bugfixes

* Fix false positive `unused-ret-vals-in-try` for `clojure.test` 
  * Fixes https://github.com/jonase/eastwood/issues/206
* Fix false positive `suspicious-expression` for `clojure.test/is`
  * Fixes https://github.com/jonase/eastwood/issues/207
* Support `clojure.test/assert-expr` better
  * Fixes https://github.com/jonase/eastwood/issues/313
* Vanilla `defn`s having `:test` metadata don't result in false positives for the `:bad-arglists` linter anymore. 

## Changes from 0.3.14 to 0.4.0 

#### New

* Introduce `:ignored-faults` option
   * See: https://github.com/jonase/eastwood#ignored-faults
   * Fixes https://github.com/jonase/eastwood/issues/21
* Default to linter parallelism
  * Linter parallelism (as opposed to the `:parallelism?` option, which affects analysis/evaluation) is thread-safe.
  * Fixes https://github.com/jonase/eastwood/issues/339

#### Bugfixes

* Improve compatibility with Leiningen higher-order tasks, plugins, etc
  * Fixes https://github.com/jonase/eastwood/issues/244
* Improve compatibility with forms defined with `^:const` 
  * Fixes https://github.com/jonase/eastwood/issues/341
* Improve compatibility with CIDER
  * Fixes https://github.com/jonase/eastwood/issues/298
* Improve compatibility with large defprotocols
  * Fixes https://github.com/jonase/eastwood/issues/191
* Support require+import pattern for defrecords, without triggering "Namespace is never used"
   * Fixes https://github.com/jonase/eastwood/issues/210
* Remove a noisy println, on certain cases that would be already caught by the reflection warnings mechanism.
   * Fixes https://github.com/jonase/eastwood/issues/355
* Restore accidentally-dropped support for Clojure < 1.10
   * Fixes https://github.com/jonase/eastwood/issues/356

#### Breaking changes

* Drop support for Clojure 1.6

## Changes from 0.3.13 to 0.3.14 

* Improve `:implicit-dependencies` to support potemkin/import-vars

## Changes from 0.3.12 to 0.3.13

* Fix `:rethrow-exceptions?` option which could try to hash-map
* Keep order for `:namespaces` option

## Changes from 0.3.11 to 0.3.12

* Introduce `:rethrow-exceptions?` option, which offers throwing any encountered exceptions during analysis/linting instead of only reporting them.
You might want this if using Eastwood programatically.

## Changes from 0.3.10 to 0.3.11

 * Add `set-linter-executor!` configuration option

## Changes from 0.3.7 to 0.3.8

 * Fix pre-post warning for dynamic vars

## Changes from 0.3.6 to 0.3.7

 * Fix memory leak on repeated runs

## Changes from 0.3.5 to 0.3.6

* Add support for files with tagged literals using custom data readers

## Changes from 0.3.4 to 0.3.5

* Add support for only linting changed files since last run.
This feature is to be considered alpha. If passed `:only-modified` with the value true,
Eastwood will only lint the files which are modified since the timestamp stored in
`.eastwood`

## Changes from 0.3.3 to 0.3.4

* Add support for parallelism. First shot is `:naive` which runs `pmap` over
the namespaces. 

## Changes from 0.3.2 to 0.3.3
* Disable `:redefd-vars` warning for mount's `defstate`

## Changes from 0.3.1 to 0.3.2

* Add `:implicit-dependencies` linter
* Fix `:constant-test` warning on macro expansion of `clojure.spec`'s
  `coll-of`

## Changes from version 0.2.x to 0.3.0

* Eastwood now drops support for leiningen 1.x

## Changes from version 0.2.6 to 0.2.7

* Fix warnings on clojure macro expansions for `as->` and `coll-of`

## Changes from version 0.2.4 to 0.2.5

The main changes with version 0.2.5 are for improving how Eastwood
works with Clojure 1.9.0, and eliminating false positives when using
the `:unused-namespaces` linter.

Thanks to contributions from Daniel Compton, Derek Passen, Emlyn
Corrin, and Reid McKenzie.

No new linters.

* Updated version of tools.reader adds support for new syntax in
  Clojure 1.9.0-beta2, e.g. [map namespace
  syntax](https://clojure.org/reference/reader#map_namespace_syntax),
  Issue [#228](https://github.com/jonase/eastwood/issues/228)
  [#201](https://github.com/jonase/eastwood/issues/201)

* Eliminate some common `:suspicious-expression` warnings due to how
  some `clojure.spec` macros such as `every` and `and` are
  implemented.  Issue
  [#227](https://github.com/jonase/eastwood/issues/227).  There are
  likely further improvements that can be made in this area in the
  future.

* `:unused-namespaces` linter has been significantly improved, in that
  it gives far fewer false positive warnings.
  Issue [#200](https://github.com/jonase/eastwood/issues/200)
  [#211](https://github.com/jonase/eastwood/issues/211)
  [#186](https://github.com/jonase/eastwood/pull/186)

* Eliminate warnings when running with Clojure 1.9.0 for conflict with
  new functions in core like boolean? and uri?
  Issue [#232](https://github.com/jonase/eastwood/issues/232)
  [#233](https://github.com/jonase/eastwood/pull/233)

* Eliminate new warnings that appeared with Clojure 1.9.0 defrecord
  performance improvement, because Eastwood was using internal details
  that changed.
  Issue [#231](https://github.com/jonase/eastwood/issues/231)

* New version of leinjacker allows Eastwood to be invoked via `lein`
  command line for Leiningen projects that use [managed
  dependencies](https://github.com/technomancy/leiningen/blob/master/doc/MANAGED_DEPS.md).
  Issue [#230](https://github.com/jonase/eastwood/issues/230)

* Eliminate some unwanted debug messages when `clojure.spec/assert` is
  used inside of function preconditions.
  Issue [#219](https://github.com/jonase/eastwood/issues/219)

* Eliminate some false `:non-dynamic-earmuffs` warnings.
  Issue [#213](https://github.com/jonase/eastwood/pull/213)


## Changes from version 0.2.3 to 0.2.4

No new linters.  Added initial support for .cljc files and reader
conditionals.

* Read .cljc files in addition to .clj files when scanning namespaces.

* Handle reader conditionals by always parsing the :clj branch.

* Allow ClojureScript-specific libspec entries, such as `:include-macros
  true`.


Internal enhancements:

* Updated versions of tools.analyzer, tools.reader, tools.namespace,
  core.memoize, and clojure.java.classpath.

* Updated documentation, cleaned up comments, and removed unused
  require statements.


## Changes from version 0.2.2 to 0.2.3

No new linters.  The only difference with 0.2.2 is a few bug fixes:

* Ignore the contents of any file `data_readers.clj` in any directory
  of your classpath.  Earlier versions of Eastwood would lint their
  contents, and by default complain that there was no `ns` form at the
  beginning.  There should not be an `ns` form in such a file, and
  Eastwood no longer issues such incorrect warnings.  Issue
  [#172](https://github.com/jonase/eastwood/issues/172).

* Eliminate an exception caused by Eastwood's `unused-ret-vals` and
  `unused-ret-vals-in-try` linters that could occur if a Java method
  cannot be resolved.  Issue
  [#173](https://github.com/jonase/eastwood/issues/173).


## Changes from version 0.2.1 to 0.2.2

New linter:

* New linter `:wrong-pre-post` that warns about several kinds of wrong
  or suspicious preconditions or postconditions in a function.
  [link](https://github.com/jonase/eastwood#keyword-typos).  Issue
  [#89](https://github.com/jonase/eastwood/issues/89).


Other enhancements and bug fixes:

* Updates to support Clojure 1.8.0-RC1.

* Picture of Clint Eastwood in the README.

* Warn when a record field and function name are identical.  This was
  actually implemented in an earlier version of Eastwood, but I did
  not notice that until now.  Issue
  [#55](https://github.com/jonase/eastwood/issues/55).

* Several fixes to the bad-arglists linter.

* Change keyword-typos linter so that it no longer warns if the only
  difference between two keywords is the presence or absence of an
  initial underscore character.  Issue
  [#163](https://github.com/jonase/eastwood/issues/163).

* If you use the APIs to invoke Eastwood that return a map of info
  about each warning, there is a new :warning-details-url key that
  gives a link to the Eastwood documentation giving more detail about
  each type of warning.  Issue
  [#105](https://github.com/jonase/eastwood/issues/105).

* Eliminate exception thrown when linting byte-streams library.  Issue
  [#120](https://github.com/jonase/eastwood/issues/120).

* Disable unused-ret-vals warnings inside Potemkin's import-vars
  macro.  Issue [#135](https://github.com/jonase/eastwood/issues/135).


Internal enhancements:

* Correct the output put into debug file forms-emitted.txt.  Issue
  [#136](https://github.com/jonase/eastwood/issues/136).

* Updated versions of tools.analyzer, tools.analyzer.jvm and several
  other libraries used by Eastwood.  The current version of these
  should always be visible in the file
  [clone.sh](https://github.com/jonase/eastwood/blob/master/copy-deps-scripts/clone.sh)


There were changes in the way the 'defn' macro was implemented in
Clojure 1.8.0-RC1 that caused the tools.analyzer ASTs (abstract syntax
trees) to differ when analyzing Clojure code.  Eastwood 0.2.1 and
earlier did not gracefully handle those changes, causing the warnings
issued for the same Clojure project to differ significantly when using
Clojure 1.8.0-RC1 instead of an earlier version of Clojure, most
noticeably for misplaced doc strings and wrong tag warnings.
Hopefully all of these issues have been eliminated with Eastwood
0.2.2.


## Changes from version 0.2.0 to 0.2.1

If you use Emacs+Cider or Eclipse+Counterclockwise development
environments, there are now add-ons that integrate Eastwood warnings.
See https://github.com/jonase/eastwood#editor-support

New linters, and new good warnings from existing linters:

* New linter `:wrong-ns-form` that warns about several kinds of wrong
  or suspicious `:require` or `:use` subforms inside `ns` forms.
  Issue [#85](https://github.com/jonase/eastwood/issues/85),
  [#98](https://github.com/jonase/eastwood/issues/98)

* `:suspicious-expression` linter now warns about trivial uses of more
  `clojure.core` macros then before.

Fewer unwanted warnings, via logic enhancements or configuration
options:

* Several linters now have configuration options to disable their
  warnings based upon whether the warnings occur inside of a
  macroexpansion of a particular macro.  By default, Eastwood loads
  several config files worth of such disabling options for the linters
  `:constant-test`, `:redefd-vars`, `:suspicious-expression`, and
  `:unused-ret-vals` that prevent them from generating many unwanted
  warning messages, at least when certain macros are used, such as
  those in `core.contracts`, `core.match`, `core.typed`, Korma,
  Carmine, Timbre, Instaparse, and Schema.  Eastwood users may write
  their own config files to disable more warnings.
  Issue [#45](https://github.com/jonase/eastwood/issues/45),
  [#96](https://github.com/jonase/eastwood/issues/96),
  [#108](https://github.com/jonase/eastwood/issues/108),
  [#111](https://github.com/jonase/eastwood/issues/111),
  [#118](https://github.com/jonase/eastwood/issues/118),
  [#122](https://github.com/jonase/eastwood/issues/122),
  [#123](https://github.com/jonase/eastwood/issues/123)

* The `:wrong-arity` linter now generates nearly no unwanted warnings
  when you use the `java.jdbc` and Hiccup libraries.  Those libraries
  modify the `:arglists` key in metadata of some of their functions
  and macros for documentation purposes, but in a way that fooled
  Eastwood into generating incorrect warnings.  Like the previous
  item, this is also configurable, and Eastwood users may extend these
  configurations for their own situations.
  Issue [#119](https://github.com/jonase/eastwood/issues/119),
  [#124](https://github.com/jonase/eastwood/issues/124)

* Limited documentation for how to specify these new config files,
  plus links to the current ones, where one might learn from the
  current example configs.  This documentation should expand in the
  future.

* The `:unused-namespaces` linter had several bugs causing it to
  report a namespace that was `require`d or `use`d as being unused,
  when in fact it was.  The only remaining case of such unwanted
  warnings is at least documented.
  Issue [#25](https://github.com/jonase/eastwood/issues/25)

* `:suspicious-test` linter now correctly infers in more cases when
  the last argument to `clojure.test/is` is a string, eliminating some
  incorrect warnings.
  Issue [#117](https://github.com/jonase/eastwood/issues/117)

Other enhancements:

* `:constant-test` warning messages now include the expression in
  which the constant test expression was found.  In some cases where
  the expression is inside of a macro expansion, this can give more
  clues about the cause of the constant test.

* When reflection or boxed math warnings are enabled and the Clojure
  compiler prints them during Eastwood's `eval`ing of your code,
  Eastwood will recognize them and change their format to match that
  of Eastwood's own warnings, so that they may be stepped through in
  editors in the same way as other Eastwood warnings.  Note that any
  such warnings produced when Leiningen is loading other namespaces,
  before Eastwood analysis begins, are outside of Eastwood's knowledge
  or control, and are thus not modified.

* New `eastwood.lint/lint` function intended for use by developers
  integrating Eastwood with editors and IDEs.
  Issue [#131](https://github.com/jonase/eastwood/issues/131)

* When specifying lists of linters to use in Eastwood options, can now
  use the keyword `:all` as an abbreviation for all linters, or
  `:default` for all linters enabled by default.
  Issue [#130](https://github.com/jonase/eastwood/issues/130)

* If code uses the values of `&env` in a macro expansions, which
  causes Eastwood to throw an exception, it now recognizes that
  exception message and gives a message that explains a little more
  clearly why the exception occurred, with links to the documentation.

* Documentation of how Eastwood's options map is created from
  Leiningen configuration files and the command line, plus a new debug
  option `:options` to show what the options are at several steps of
  the process.
  Issue [#125](https://github.com/jonase/eastwood/issues/125)

* Invoking `lein help eastwood` from the command line now prints some
  help, plus link to the full documentation.

* `:debug` key value in Eastwood options map can now be a list or
  vector, to avoid the need to type the `#` character as part of the
  set literal syntax.

Internal Eastwood test/development enhancements:

* Enhancements to Eastwood's tests to make it easier to update
  expected results to match actual results.

* Update projects in Eastwood's test suite (the "crucible") to include
  newer versions of `core.logic` and Elastisch.


## Changes from version 0.1.5 to 0.2.0

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

* Updated the `:unused-private-vars` linter so that it should be
  correct in more cases, and have better line number info.

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
