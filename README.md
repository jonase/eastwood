# eastwood - a Clojure lint tool

[![Dependencies Status](https://versions.deps.co/jonase/eastwood/status.svg)](https://versions.deps.co/jonase/eastwood)
[![Downloads](https://versions.deps.co/jonase/eastwood/downloads.svg)](https://versions.deps.co/jonase/eastwood)
[![Build Status](https://circleci.com/gh/jonase/eastwood/tree/master.svg?style=shield&circle-token=26d8d2fa593675196734ac6c28ee16e0a9183806)](https://circleci.com/gh/jonase/eastwood)
[![Clojars Project](https://img.shields.io/clojars/v/jonase/eastwood.svg)](https://clojars.org/jonase/eastwood)

<img src="doc/Clint_Eastwood_-_1960s_small.jpg"
 alt="Picture of Clint Eastwood in 'A Fistful of Dollars' (1964)" title="Clint Eastwood in 'A Fistful of Dollars' (1964)"
 align="right" />

> "Now remember, things look bad and it looks like you're not gonna
> make it, then you gotta get mean.  I mean plumb, mad-dog mean.
> 'Cause if you lose your head and you give up then you neither live
> nor win.  That's just the way it is."
> - Josey Wales, played by Clint Eastwood in "The Outlaw Josey Wales"

Eastwood is a Clojure [linter](http://en.wikipedia.org/wiki/Lint_%28software%29); it inspects namespaces and reports possible problems.

Because it uses [tools.analyzer](https://github.com/clojure/tools.analyzer.jvm), its analysis and diagnostics tend to be particularly accurate, avoiding false positives and false negatives.

In particular it's about as accurate as the Clojure compiler itself - it prefers evaluation and macroexpansion over other approaches.

This approach is not free of tradeoffs. The use case where it shines is in CI environments, where a matrix of JDKs and/or Clojure versions can be leveraged, and where linter performance is not as critical as in editors or CLIs.

Eastwood's main area of focus is spotting bugs (as opposed to, say, helping following coding conventions). Other tools can complement or partly overlap with Eastwood's offering. 

> Eastwood supports only JVM Clojure (>= 1.7.0) , not ClojureScript or
  Clojure/CLR. Consider using .cljc for obtaining certain degree of ClojureScript support.

## Installation & Quick usage

Eastwood can be run from within a REPL, regardless of which build
tools you may use.  See the [instructions
here](#running-eastwood-in-a-repl).

### Leiningen
Eastwood can be run from the command line as a
[Leiningen](http://leiningen.org) plugin.

Merge the following into your `project.clj` or `~/.lein/profiles.clj`:

```clojure
:plugins [[jonase/eastwood "1.4.1"]]
```

To run Eastwood with the default set of lint warnings on all of the
Clojure files in the source _and_ test paths of your project, use the
command:

    $ lein eastwood

### deps.edn

If you're using `deps.edn`, you can set Eastwood options in an edn map, like this:
```clojure
{:aliases
  {:eastwood
    {:main-opts ["-m"
                 "eastwood.lint"
                 ;; Any Eastwood options can be passed here as edn:
                 {}]
     :extra-deps {jonase/eastwood {:mvn/version "1.4.1"}}}}}

```
to your `deps.edn`, and you should then be able to run Eastwood as

```sh
clojure -M:test:eastwood
```

For deps.edn projects in particular, you don't need to set the `:source-paths` and `:test-paths`
as configuration options; they will be accurately inferred at runtime.

The only requirement is that you enable all relevant deps.edn aliases - mainly `test` but possibly others,
depending on your project layout.

Any `:paths` not present at runtime, as computed by the Clojure CLI, will not be analyzed by Eastwood. 

---

If it is not obvious what a warning message means, please check the
next section, which has a `[more]` link for each type of warning.
Most types of warning messages have a page or more of text describing
the warning, why it occurs, and sometimes suggestions on what you can
do about it.  Also note that there are several types of warnings
marked as '(disabled)', meaning that by default no such warnings will
be checked for.  You may wish to enable those for your project.  See
the [Usage](#usage) section for options to enable or disable types of
warnings for your entire project.

See the [Usage](#usage) section below for more notes on side effects
in test code, and instructions on [running Eastwood in a REPL
session](#running-eastwood-in-a-repl).

Eastwood can only finish linting a file if Clojure itself can compile
it (unlike some other lint tools, which try to give meaningful error
messages for programs with syntax errors).  It is recommended to use a
command like `lein check` to check for compiler errors before running
Eastwood.  Even better, `lein test` will compile files in your source
paths and test paths, not merely your source paths as `lein check`
does.

If you run Eastwood from a `lein` command line, it is perfectly normal
to see the message `Subprocess failed` at the end if either the
warning or exception thrown counts are not 0.  Eastwood exits with a
non-0 [exit status](http://en.wikipedia.org/wiki/Exit_status) in this
situation, so that shell scripts or build tools running Eastwood will
have a simple way to check that something was not perfect.  If
Eastwood quits due to some internal error that throws an exception,
you will typically see much more voluminous output about what went
wrong, often including a stack trace.

You can override the exit code with the `:forced-exit-code 0` option.
That can be helpful when wanting to see the results of linting merely for informative purposes. 

## What's there?

Eastwood warns when it finds the following kinds of things.  Each
keyword below is the name of the "linter".  That name can be used on
the command line to enable or disable the linter.  All linters are
enabled by default unless they have '(disabled)' after their name.

| Linter name | Description | Docs |
| ----------- | ----------- | ---- |
| no name* | Inconsistencies between file names and the namespaces declared within them.  * Cannot be disabled. | [[more]](#check-consistency-of-namespace-and-file-names) |
| `:bad-arglists` | Function/macro `:arglists` metadata that does not match the number of args it is defined with. | [[more]](#bad-arglists) |
| `:boxed-math` | Boxed math compiler warnings | [[more]](#boxed-math) |
| `:constant-test` | A test expression always evaluates as true, or always false. | [[more]](#constant-test) |
| `:def-in-def` | def's nested inside other def's. | [[more]](#def-in-def) |
| `:deprecations` | Deprecated Clojure Vars, and deprecated Java constructors, methods, and fields. | [[more]](#deprecations) |
| `:implicit-dependencies` | A fully-qualified var refers to a namespace that hasn't been listed in `:require`. | [[more]](#implicit-dependencies) |
| `:keyword-typos` (disabled) | Keyword names that may be typos because they occur only once in the source code and are slight variations on other keywords. | [[more]](#keyword-typos) |
| `:local-shadows-var` | A local name, e.g. a function arg or let binding, has the same name as a global Var, and is called as a function. | [[more]](#local-shadows-var) |
| `:misplaced-docstrings` | Function or macro doc strings placed after the argument vector, instead of before the argument vector where they belong. | [[more]](#misplaced-docstrings) |
| `:no-ns-form-found` | Warn about Clojure files where no `ns` form could be found. | [[more]](#no-ns-form-found) |
| `:non-clojure-file` (disabled) | Warn about files that will not be linted because they are not Clojure source files, i.e. their name does not end with '.clj'. | [[more]](#non-clojure-file) |
| `:non-dynamic-earmuffs` | Vars marked `^:dynamic` should follow the "earmuff" naming convention, and vice versa. | [[more]](#non-dynamic-earmuffs) |
| `:performance` | Performance warnings | [[more]](#performance) |
| `:redefd-vars` | Redefinitions of the same name in the same namespace. | [[more]](#redefd-vars) |
| `:reflection` | Reflection warnings | [[more]](#reflection) |
| `:suspicious-expression` | Suspicious expressions that appear incorrect, because they always return trivial values. | [[more]](#suspicious-expression) |
| `:suspicious-test` | Tests using `clojure.test` that may be written incorrectly. | [[more]](#suspicious-test) |
| `:unlimited-use` | Unlimited `(:use ...)` without `:refer` or `:only` to limit the symbols referred by it. | [[more]](#unlimited-use) |
| `:unused-fn-args` (disabled) | Unused function arguments. | [[more]](#unused-fn-args) |
| `:unused-locals` (disabled) | Symbols bound with `let` or `loop` that are never used. | [[more]](#unused-locals) |
| `:unused-meta-on-macro` | Metadata on a macro invocation is ignored by Clojure. | [[more]](#unused-meta-on-macro) |
| `:unused-namespaces` (disabled) | Warn if a namespace is given in an `ns` form after `:use` or `:require`, but the namespace is not actually used. | [[more]](#unused-namespaces) |
| `:unused-private-vars` (disabled) | Unused private vars. | [[more]](#unused-private-vars) |
| `:unused-ret-vals` and `:unused-ret-vals-in-try` | Unused values, including unused return values of pure functions, and some others functions where it rarely makes sense to discard its return value. | [[more]](#unused-ret-vals) |
| `:wrong-arity` | Function calls that seem to have the wrong number of arguments. | [[more]](#wrong-arity) |
| `:wrong-ns-form` | ns forms containing incorrect syntax or options. | [[more]](#wrong-ns-form) |
| `:wrong-pre-post` | function has preconditions or postconditions that are likely incorrect. | [[more]](#wrong-pre-post) |
| `:wrong-tag` | An incorrect type tag for which the Clojure compiler does not give an error. | [[more]](#wrong-tag) |

The following table gives some additional detail about each linter.

The 'debug' column indicates whether extra debug messages about a
linter's warnings can be enabled via the `:debug-warning` option.
This option can be given a value of `true` to enable all such
warnings, or it can be a set of keywords that also enables additional
details to be printed.  The only keyword currently supported in this
set is `:ast`, which prints AST contents related to issued warnings
for most linters that implement `:debug-warning`.

The 'suppress' column indicates whether warnings produced by the
linter can be selectively disabled via Eastwood config files.  See
[Eastwood config files](#eastwood-config-files) for more details.

| Linter name | debug | suppress |
| ----------- | ----- | -------- |
| no name*                 |  |  |
| `:bad-arglists`          |  |  |
| `:constant-test`         | yes | yes |
| `:def-in-def`            |  | yes |
| `:deprecations`          |  | yes |
| `:keyword-typos`         |  |  |
| `:local-shadows-var`     |  |  |
| `:misplaced-docstrings`  |  |  |
| `:no-ns-form-found`      |  |  |
| `:non-clojure-file`      |  |  |
| `:redefd-vars`           | yes | yes |
| `:suspicious-expression` | yes, for those involving macros | yes |
| `:suspicious-test`       |  | yes |
| `:unlimited-use`         |  |  |
| `:unused-fn-args`        |  | yes  |
| `:unused-locals`         |  |  |
| `:unused-meta-on-macro`  |  | yes |
| `:unused-namespaces`     |  |  |
| `:unused-private-vars`   |  |  |
| `:unused-ret-vals` and `:unused-ret-vals-in-try` | yes | yes |
| `:wrong-arity`           | yes | yes |
| `:wrong-ns-form`         |  |  |
| `:wrong-pre-post`        |  |  |
| `:wrong-tag`             |  | yes |


## Usage

### From the command line as a Leiningen plugin

Running

    $ lein eastwood

in the root of your project will lint your project's namespaces -- all
of those in your `:source-paths` and `:test-paths` directories and
their subdirectories.  You can also lint individual namespaces in your
project, or your project's dependencies:

    $ lein eastwood "{:namespaces [compojure.handler compojure.core-test] :exclude-linters [:unlimited-use]}"
    == Linting compojure.handler ==
    src/compojure/handler.clj:48:8: deprecations: Var '#'compojure.handler/api' is deprecated.
    == Linting compojure.core-test ==
    test/compojure/core_test.clj:112:21: suspicious-test: 'is' form has first arg that is a constant whose value is logical true.  This will always pass.  There is probably a mistake in this test
    test/compojure/core_test.clj:117:21: suspicious-test: 'is' form has first arg that is a constant whose value is logical true.  This will always pass.  There is probably a mistake in this test
    test/compojure/core_test.clj:109:1: constant-test: Test expression is always logical true or always logical false: false
    test/compojure/core_test.clj:109:1: constant-test: Test expression is always logical true or always logical false: true
    test/compojure/core_test.clj:114:1: constant-test: Test expression is always logical true or always logical false: false
    test/compojure/core_test.clj:114:1: constant-test: Test expression is always logical true or always logical false: true
    == Warnings: 7. Exceptions thrown: 0
    Subprocess failed

Adding `:out "warn.txt"` to the options map will cause all of the
Eastwood warning lines and 'Entering directory' lines, but no others,
to be written to the file `warn.txt`.  This file is useful for
stepping through warnings.

    # This works on bash shell in Linux and Mac OS X, and also in
    # Windows cmd shell
    $ lein eastwood "{:out \"warn.txt\"}"

    # This saves a little typing in bash shell, but does not work in
    # Windows cmd shell.  For all example command lines, you can use
    # single quotes in bash if you prefer.
    $ lein eastwood '{:out "warn.txt"}'

Available options for specifying namespaces and paths are:

* `:namespaces` Vector of namespaces to lint.  A keyword
  `:source-paths` in this vector will be replaced with a list of
  namespaces in your Leiningen `:source-paths` and their
  subdirectories.  These namespaces will be in an order that honors
  inter-namespace dependencies as determined by `:require` and `:use`
  keys in `ns` forms.  Similarly for a keyword `:test-paths`.  If you
  do not specify `:namespaces`, it defaults to `[:source-paths
  :test-paths]`.
* `:exclude-namespaces` Vector of namespaces to exclude.
  `:source-paths` and `:test-paths` may be used here as they can be
  for `:namespaces`.  Defaults to an empty list if you do not specify
  `:exclude-namespaces`.
* `:source-paths` is normally taken from your Leiningen `project.clj`
  file, which is `[ "src" ]` by default if not specified there.  You
  can also specify `:source-paths` in the Eastwood option map to
  override what Leiningen uses.
* `:test-paths` is similar in behavior to `:source-paths`, except it
  defaults to `[ "test" ]` if not specified in your `project.clj`
  file.

Linter names are given in the previous section.  Available options for
specifying which linters are enabled or disabled are:

* `:linters` Linters to use.  If not specified, same as `[:default]`,
  which is all linters except those documented as 'disabled by
  default'.
* `:exclude-linters` Linters (or [linter sub `:kind`s](#ignoring-linter-sub-kinds)) to exclude
* `:add-linters` Linters to add. You can use to enable linters that are disabled by default. The final list of linters is the set
  specified by `:linters`, taking away all in `:excluded-linters`,
  then adding all in `:add-linters`.

The keyword `:all` in any of the collections of linters listed above
will be replace with the collection of all linters.  The keyword
`:default` will be replaced with the collection of default linters.
Thus `:linters [:all]` enables all linters, even those disabled by
default, and `:linters [:all] :exclude-linters [:default]` enables
only those that are disabled by default.

Note that you can add Eastwood options to a user-wide Leiningen
`profiles.clj` file or to your project's `project.clj` file if you
wish.  See [How the Eastwood options map is
determined](#how-the-eastwood-options-map-is-determined) for more
details.


## Only lint files modified since last run

You can now instruct eastwood to only lint the files
changed since the last run.

If passed `:only-modified` with the value true, Eastwood will only lint the 
files which are modified since the timestamp stored in `.eastwood`.

```
  :only-modified true
```

## Usage

As mentioned in the [Installation & Quick
usage](#installation--quick-usage) section above, Eastwood causes any
and all side effects that loading the file would cause (e.g. by doing
`use` or `require` on the file's namespace).  Eastwood is able to find
potential problems in test code, too.  If you wish to use Eastwood on
test files without such side effects, consider modifying your tests so
that merely performing `require`/`use` on the files does not cause the
side effects.  If you can arrange things so that running your tests
requires loading the files and then calling some function(s) (e.g. as
tests written using
[`clojure.test/deftest`](http://clojure.github.io/clojure/#clojure.test)
do), then you can run Eastwood on those files without the side
effects.

If you have a code base you do not trust to load, consider a sandbox,
throwaway virtual machine, etc.

There are also options that enable printing of additional debug
messages during linting.  These are only intended for tracking down
the cause of errors in Eastwood.  You specify the key `:debug` with a
value that is a list or vector of keywords, e.g.

    lein eastwood "{:exclude-linters [:unlimited-use] :debug [:options :ns]}"

* `:all` - enable all debug messages.  This also enables showing the
  list of namespaces near the beginning of the output, before linting
  begins.
* `:options` - print the contents of the options map at several steps
  during startup.  May be useful to debug where options are coming
  from.
* `:config` - print the names of Eastwood config files just before
  they are read.
* `:time` - print messages about the elapsed time taken during
  analysis, and for each individual linter.
* `:forms` - print the forms as read, before they are analyzed
* `:forms-pprint` - like `:forms` except pretty-print the forms
* `:ast` - print ASTs as forms are analyzed and `eval`d.  These can be
  quite long.
* `:progress` - show a brief debug message after each top-level form
  is read
* `:compare-forms` - print all forms as read to a file
  `forms-read.txt`, all forms after being analyzed to a file
  `forms-analyzed.txt`, and all forms after being read, analyzed into
  an AST, and converted back into a form from the AST, to a file
  `forms-emitted.txt`.
* `:ns` - print the initial set of namespaces loaded into the Clojure
  run-time at the beginning of each file being linted.  (TBD: it used
  to do the following, but this needs to be reimplemented if it is
  desired: "and then after each top level form print any changes to
  that list of loaded namespaces (typically as the result of
  evaluating a `require` or `use` form).")
* `:var-info` - print some info about Vars that exist in various
  namespaces, and how many of them have data describing them in
  Eastwood's `var-info.edn` resource file, and how many do not.
  Useful when new releases of Clojure are made that add new Vars, to
  determine which ones Eastwood does not know about yet.


### Eastwood config files

Eastwood `eval`s several config files in
its internal resources.  You can see the latest versions
[here](https://github.com/jonase/eastwood/tree/master/resource/eastwood/config).
It also supports command line options to change which of these files
are read, or to read user-written config files.

Currently Eastwood supports config files that contain code to
selectively disable warnings of some linters.  For example, consider
this expression from config file `clojure.clj`:

```clojure
(disable-warning
 {:linter :suspicious-expression
  ;; specifically, those detected in function suspicious-macro-invocations
  :for-macro 'clojure.core/let
  :if-inside-macroexpansion-of #{'clojure.core/when-first}
  :within-depth 6
  :reason "when-first with an empty body is warned about, so warning about let with an empty body in its macroexpansion is redundant."})
```

The `:deprecations` linter accepts a set of symbols which are marked as ok to be deprecated.
This is useful for when you're working on an old project and want to mark stuff
as deprecated without breaking the build.

```clojure
(disable-warning
  {:linter :deprecations
   :symbol-matches #{#"^#'my\.old\.project\.*"}})
```

Eastwood would normally report a `:suspicious-expression` warning if
it encounters a form `(let [x y])`, because the `let` has an empty
body.  It does so even if the `let` is the result of expanding some
other macro.

However, if such a `let` occurs because of a macro expansion of the
expression `(when-first [x y])`, the warning for the `let` will be
suppressed.  Eastwood already warns about the `(when-first [x y])`
because it has an empty body, so warning about the `let` would be
redundant.

The exact configurations supported are not documented yet, but will be
in the documentation for each linter.

You can specify the key `:builtin-config-files` in the options map to
override the built-in config files read.  It defaults to
`["clojure.clj" "clojure-contrib.clj" "third-party-libs.clj"]`.  All
such file names are only looked for in Eastwood's built-in config
files.

Similarly you can specify `:config-files` in the options map to give
additional files to read.  These are filenames that can be anywhere
in your file system, specified as strings, or if Eastwood is invoked
from the REPL, anything that can be passed to
`clojure.java.io/reader`.


### Running Eastwood in a REPL

<details>

If you use Leiningen, merge this into your project's `project.clj`
file first:

```clojure
:profiles {:dev {:dependencies [[jonase/eastwood "1.4.1" :exclusions [org.clojure/clojure]]]}}
```

If you use a different build tool, you will need to add the dependency
above in the manner appropriate for it.  See
[Clojars](https://clojars.org/jonase/eastwood) for Gradle and Maven
syntax.

From within your REPL, there are two different functions you may call,
depending upon the kind of results you want.

* `eastwood` prints output similar to when you run Eastwood from the
  Leiningen command line, but it does not exit the JVM when it
  finishes.

* `lint` returns a map containing data structures describing any
  warnings or errors encountered. For example, file
  names, line numbers, and column numbers are all available
  separately, requiring no parsing of strings containing those things
  combined together.  See the doc string of `eastwood.lint/lint` for
  details of the return value.

```clojure
(require '[eastwood.lint :as e])

;; Replace the values of :source-paths and :test-paths with whatever
;; is appropriate for your project.  You may omit them, and then the
;; default behavior is to search all directories in your Java
;; classpath, and their subdirectories recursively, for Clojure source
;; files.
(e/eastwood {:source-paths ["src"] :test-paths ["test"]})

(e/lint {:source-paths ["src"] :test-paths ["test"]})
```

All of the same options that can be given on a Leiningen command line
may be used in the map argument of the `eastwood` and `lint`
functions.

There is a `:callback` key that can be added to the argument map for
the `eastwood` function.  Its value is a callback function that gives
you significant control of where warning and error messages appear --
by default these all appear on the writer `*out*`.  This callback
function should not be overridden for the `lint` function, since
`lint` uses it in its implementation.

There is no documentation for this callback function yet.  You are
welcome to read Eastwood source code to see examples of how to write
one, but note that this is alpha-status code that will likely have API
changes in future Eastwood versions.


#### Warnings about using Eastwood in a REPL

Eastwood behaves similarly to `clojure.core/require` while performing
its analysis, in that it loads your code.  In particular, Eastwood
does these things:

* Reads and analyzes the source code you specify.
* Generates _new_ forms from the analysis results.  Note: if there are
  bugs, these new forms might not be identical to the original source
  code.
* Calls `eval` on the generated forms.

Hopefully you can see from this that Eastwood bugs, especially in the
portion up to generating new forms to be evaluated, could lead to
incorrect Clojure code being loaded into a running JVM.

It would be foolhardy to run Eastwood in a JVM running a live
production system.  We recommend that you use a different JVM process
for Eastwood than the one where you do your ongoing testing and
development work.

When reporting problems with Eastwood when run from the REPL, please
reproduce it in as few steps as possible after starting a new JVM
process, and include those steps in your problem report.

Running Eastwood from the REPL more than once in the same JVM process
requires you to manage your namespaces manually.  Eastwood will not
force the removal of any namespaces, and I would guess if there are
any issues from reloading a namespace that is already loaded with
protocols, `deftype`, etc. then they are yours to deal with.

Stuart Sierra's [component](https://github.com/stuartsierra/component)
library and workflow might be helpful in automatically removing old
versions of namespaces from a JVM process.  If you have instructions
that you have used with Eastwood and component or a similar tool,
please file a GitHub issue so they can be included here.

</details>

### How the Eastwood options map is determined

<details>

If you start Eastwood from a REPL using the function
`eastwood.lint/eastwood`, then the options map you supply is modified
only slightly before use.  Skip down to the section "Last options map
adjustments".

If you start Eastwood from a Leiningen command line, there are two
main steps in the creation of the Eastwood options map before those
last adjustments.  First is what Leiningen itself does before Eastwood
starts, followed by some adjustments made by Eastwood.


#### Options map calculation before Eastwood starts

Leiningen creates a value for the `:eastwood` key in the effective
project map using its normal rules for combining profiles from
multiple possible files.  In case those are unfamiliar to you, here is
a quick summary that should be correct, but leaves out some cases that
are recommended against in the Leiningen documentation, e.g.
including a `:user` profile in your project's `project.clj` file.

From lowest priority to highest, the sources are the value of an
`:eastwood` key in:

* the top level of the `defproject` in your `project.clj` file
* the `:system` profile of a system-wide `/etc/leiningen/profiles.clj`
  file.
* the `:user` profile of a user-wide `$HOME/.lein/profiles.clj`, or
  the top level of a `$HOME/.lein/profiles.d/user.clj` file.
* the `:dev` profile of your project's `project.clj` file.
* the `:dev` profile of your project's `profiles.clj` file
  (recommended only for temporary overrides of your `project.clj`
  file, not to be checked in to revision control).

The value associated with the `:eastwood` key in any of these
locations should be maps.  If there is more than one, they are merged
similarly to how `clojure.core/merge` does, where later values for the
same key replace earlier values.  However, if the values in this map
are collections, then they are combined.  Vectors and lists are
concatenated, sets are combined with `clojure.set/union`, and sub-maps
are merged, recursing down to apply the same rules to their nested
values.  See the section on
[Merging](https://github.com/technomancy/leiningen/blob/stable/doc/PROFILES.md#merging)
in the Leiningen documentation for more details and for metadata that
can be used to modify this merging behavior.

For example, if your user-wide `profiles.clj` file contains this:

```clojure
{:user {:plugins [[jonase/eastwood "1.4.1"]]
        :eastwood {:exclude-linters [:unlimited-use]
                   :debug [:time]}
        }}
```

and your `project.clj` file's `defproject` contains this:

```clojure
  :profiles {:dev {:eastwood {:exclude-linters [:wrong-arity :bad-arglists]
                              :debug [:progress]
                              :warning-format :map-v2
                              }}}
```

then Leiningen will merge them to produce the following combined value
for the `:eastwood` key:

```clojure
  {:exclude-linters (:unlimited-use :wrong-arity :bad-arglists)
   :debug (:time :progress)
   :warning-format :map-v2}
```

We will call this value the Leiningen option map.

Independently of this Leiningen option map that is a combination of
the values of the `:eastwood` key in various Leiningen files,
Leiningen also calculates values for the `:source-paths` and
`:test-paths` keys (and all other keys, but only these 3 are ever used
later to calculate Eastwood options).

### Options map adjustments made by Eastwood when invoked from command line

After the Leiningen option map is calculated, Eastwood starts making
new modified versions.

It 'normal merges' the three
maps below, in the order given.  Thus values for the same key in later
maps override earlier ones, with no special Leiningen merging behavior
for collections:

1. Leiningen paths - a map containing only the keys `:source-paths`
   and `:test-paths`, and the Leiningen-calculated values for them.
   The value of `:source-paths` defaults to `["src"]` even if you
   never specify one.  Similarly `:test-paths` defaults to `["test"]`.
2. Leiningen options map - the map for the `:eastwood` key.  Note that
   this may contain values for `:source-paths` and/or `:test-paths`
   that override the ones above.
3. command line option map

#### Last options map adjustments

If you start Eastwood from a REPL, the only changes made to the
options map specified as an argument are to fill in default values for
some keys if you do not supply them, i.e. to merge a map like the
following _before_ the supplied options map.  See
`eastwood.lint/last-options-map-adjustments` for details.

* `:cwd` - the full path name to the current working directory at the
  time the function is called.  This is used to cause file names
  reported in warnings to be relative to this directory, and thus
  shorter, if they are beneath it.
* `:linters` - default value of all linters documented to be enabled
  by default.
* `:namespaces` - default value of `[:source-paths :test-paths]`
* `:source-paths` - a list of all directories on the Java classpath.
  This is a special case that cannot be implemented with `merge`,
  because it is only used if neither of the keys `:source-paths` nor
  `:test-paths` are present in the supplies options map, as a
  convenience for use in the REPL.
* `:callback` - a default message callback function, which simply
  formats all callback data as strings and prints it to `*out*`, or
  the writer specified by the value of the `:out` key in the options
  map (e.g. if it is a string, the file named by that string will be
  written).

</details>

## Known issues


### Code analysis engine is more picky than the Clojure compiler

Eastwood uses
[`tools.analyzer`](https://github.com/clojure/tools.analyzer) and
[`tools.analyzer.jvm`](https://github.com/clojure/tools.analyzer.jvm)
to analyze Clojure source code.  It performs some sanity checks on the
source code that the Clojure compiler generally does not.


### Explicit use of Clojure environment `&env`

Code that uses the **values of `&env`** feature of the Clojure
compiler will cause errors when being analyzed.  Some known examples
are the libraries
[`immutable-bitset`](https://github.com/ztellman/immutable-bitset) and
[`flatland/useful`](https://github.com/flatland/useful).

Note that if a library uses simply `(keys &env)` it will be analyzed with
no problems, however because the values of `&env` are `Compiler$LocalBinding`s,
there's no way for `tools.analyzer.jvm` to provide a compatible `&env`

The following exception being thrown while linting is a symptom of
this issue:

    Exception thrown during phase :analyze+eval of linting namespace immutable-bitset
    ClassCastException clojure.lang.PersistentArrayMap cannot be cast to clojure.lang.Compiler$LocalBinding

## Notes on linter warnings

### Check consistency of namespace and file names

<details>

This is not a linter like the others, in that it has no name, cannot
be disabled, and the check is always performed by Eastwood before any
other linter checks are done.

When doing `require` or `use` on a namespace like `foo.bar.baz-tests`,
it is searched for in the Java classpath in a file named
`foo/bar/baz_tests.clj` (on Unix-like systems) or
`foo\bar\baz_tests.clj` (on Windows).  Dots become path separator
characters and dashes become underscores.

Such a file will normally have an `ns` form with the specified
namespace.  If the namespace name is not consistent with the file
name, then undesirable things can happen.  For example, `require`
could fail to find the namespace, or Leiningen could fail to run the
tests defined in a test namespace.

Eastwood checks all Clojure files in `:source-paths` and `:test-paths`
when starting up (or in whatever files are specified by the
:namespaces option).  If there are any mismatches between file names
and the namespace names in the `ns` forms, an error message will be
printed and no linting will be done at all.  This helps avoid some
cases of printing error messages that make it difficult to determine
what went wrong.  Fix the problems indicated and try again.

Leiningen's `lein check` and `lein test` commands do not perform as
complete a check as Eastwood does here.

If a file on the `:source-path` contains a non-matching namespace
name, but that namespace name exists in another file in your project,
`lein check` will compile the file containing that namespace again,
never compiling the file containing the wrong namespace name.

If a file on the `:test-path` contains a non-matching namespace name,
but that namespace name exists in another file, `lein test` will not
run the tests in that file at all, and will only run the tests in the
wrongly-given namespace once, not multiple times.  In both cases, such
a wrong namespace is easy to create by copying a Clojure file and
editing it, forgetting to edit the namespace.

</details>

### `:non-clojure-file`

#### Files that will not be linted because they are not Clojure source files

<details>

This linter is disabled by default, because it warns even about
ClojureScript and Java source files it finds, and these are relatively
common in projects with Clojure/Java source files.  You must
explicitly enable it if you wish to see these warnings.

If you specify `:source-paths` or `:test-paths`, or use the default
Eastwood options from the command line that cause it to scan these
paths for Clojure source files, then with this linter enabled it will
warn about each file found that is not a Clojure/Java source file,
i.e. if its file name does not end with '.clj'.

</details>

### `:no-ns-form-found`

#### Warn about Clojure files where no `ns` form could be found

<details>

If you explicitly specify `:source-paths` or `:test-paths`, or use the
default Eastwood options from the command line that cause it to scan
these paths for Clojure source files, with this linter enabled (the
default), it will warn about each file where it could not find an `ns`
form.  For each such file, its contents will not be linted, unless it
is loaded from another linted file.

Eastwood uses the library `tools.namespace` to scan for Clojure source
files, and in each Clojure source file it looks for a top-level `ns`
form.  This form need not be the first form, but Eastwood will not
find it if it is not at the top level, e.g. if it is inside of a
`let`, `if`, `compile-if`, etc.

It is somewhat unusual to have a file with no `ns` form at all, not
even inside of a `let`, `compile-if`, etc.  However, there are valid
reasons to have them, e.g. you have some code that you want to use in
common between Clojure/Java and ClojureScript, and you use `load` to
include it from two or more other source files.  Starting with Clojure
1.7.0, this purpose is better satisfied with `.cljc` files (see
[Reader Conditions](http://clojure.org/reader#The%20Reader--Reader%20Conditionals)).

</details>

### `:misplaced-docstrings`

#### Function or macro doc strings placed after the argument vector, instead of before the argument vector where they belong.

<details>

The correct place to put a documentation string for a function or
macro is just before the arguments, like so:

```clojure
(defn my-function
  "Do the thing, with the stuff.  Fast."
  [thing stuff]
  (conj stuff thing))
```

It is an easy mistake to accidentally put them in the opposite order,
especially if you like to place your arguments on the same line as the
function name.

```clojure
(defn my-function [thing stuff]
  "Do the thing, with the stuff.  Fast."
  (conj stuff thing))
```

This function will still return the desired value.  The primary
disadvantage is that there is no doc string for a function defined
this way, so `(doc my-function)` will not show what you intended, and
tools that extract documentation from Clojure code will not find it.

</details>

### `:deprecations`

#### Deprecated Clojure Vars, and deprecated Java instance methods, static fields, static methods, and constructors.

<details>

The warnings issued are based upon the particular JDK you are using
when running Eastwood, and can change between different JDK versions.

Clojure vars are considered deprecated if they have metadata with a
key `:deprecated`, and the value associated with that key is neither
`false` nor `nil`.  Which vars are deprecated can change from one
version of Clojure, or a Clojure library you use, to the next.

One example of such a function is `clojure.core/replicate`, deprecated
as of Clojure version 1.3 as you can see from its definition, copied
below.

```clojure
(defn replicate
  "DEPRECATED: Use 'repeat' instead.
   Returns a lazy seq of n xs."
  {:added "1.0"
   :deprecated "1.3"}
  [n x] (take n (repeat x)))
```

</details>

### `:implicit-dependencies`

#### Implicit dependencies

<details>

A qualified var like `some-namespace/foo` will resolve if `some-namespace`
has been loaded, regardless of whether or not `some-namespace` has been
explicitly required in the current namespace.
That is,

```clojure
(ns a)

(some-namespace/foo)
```

may work by accident, depending on load order.

This linter raises a warning in these cases, so you can list the dependency explicitly:
```clojure
(ns a
  (:require some-namespace)

(some-namespace/foo)
```

</details>

### `:redefd-vars`

#### Redefinitions of the same name in the same namespace.

<details>

It is possible to accidentally define the same var multiple times in
the same namespace.  Eastwood's `:redefd-vars` linter will warn about
these.

```clojure
(defn my-favorite-function-name [x]
   ;; code here
   )

;; lots of other functions here

(defn my-favorite-function-name [a b c]
   ;; different code here
   )
```

Clojure's behavior in this situation is not to give any warnings, and
for the later definition to replace the first.  It is common practice
for many Clojure developers to reload namespaces after editing their
source code.  If Clojure issued warnings when reloading a modified
source file for every redefined var, it would be a significant
annoyance.

If you use `clojure.test` to develop tests for your code, note that
`deftest` statements create vars with the same name as you give to the
test.  If you accidentally create two `deftest`s with the same name,
the tests in the first `deftest` will never be run, and you will lose
test coverage.  There will be nothing in the source code to indicate
this other than the common name.  Below is an example where the first
`deftest` contains tests that clearly should fail, but since they are
not run, all of the tests actually run could still pass.

```clojure
(deftest test-feature-a
  ;; This test should cause test runs to fail, but IT DOES NOT.
  (is (= 0 1)))

;; lots of other tests here

(deftest test-feature-a   ; perhaps written months after the earlier tests
  (is (= 5 (+ 2 3))))
```

The best fix here is simply to rename the tests so no two have the
same name.

Eastwood will treat a `declare` as if it were not there, for the
purposes of issuing `:redefd-vars` warnings.  These are specifically
intended to create a var but not yet give it a value, e.g. in cases
where you want to write mutually recursive functions.

There are some macros that define a var multiple times, e.g. the
`deftrace` macro in the
[`tools.trace`](https://github.com/clojure/tools.trace) contrib
library.  Eastwood will issue a warning in such cases, and the reason
will not necessarily be obvious unless you read the macro definition.
Eastwood contains code specifically to avoid issuing a warning when
this is done in the implementation of Clojure's `defprotocol` and
`defmulti` macros, but it is not possible for it to do this correctly
in all cases, no matter how a macro might be written in the future.

If you want to write a macro that uses a similar technique as these
others, consider using `declare` for all but the last definition, if
possible, and Eastwood will ignore all but that last definition.

</details>

### `:def-in-def`

#### `def` nested inside other `def`s

<details>

If you come to Clojure having learned Scheme earlier, you may write
Clojure code with `def` statements inside of functions.  Or you might
be unfamiliar with functional programming style, and try writing code
in imperative style using `def` like this:

```clojure
(defn count-up-to [n]
  (def i 1)
  (while (<= i n)
    (println i)
    (def i (+ i 1))))
```

This is bad form in Clojure.  It is written in imperative style, which
is not encouraged, but that is not the worst thing about this example.
The worst part is the use of `def` inside of another `def` (the `defn
count-up-to` counts as the outer `def`).  `def`s always have an effect
on a globally visible var in the namespace, whether they are nested
inside another `def` or not.

Unless you really know what you are doing and looking for a very
particular effect, it is recommended to take `:def-in-def` warnings as
a sign to change your code.

If you want local functions that can only be used inside of an outer
function, not visible or callable elsewhere, consider using `let`:

```clojure
(defn outer-fn-callable-elsewhere [n]
  (let [helper-fn (fn [m] (* m m))]
    (if (> n 10)
      (helper-fn n)
      (helper-fn (+ n 17)))))
```

If you need local functions that can all call each other, `let` will
not work, but [`letfn`](http://clojuredocs.org/clojure.core/letfn)
will.

If you want to write code in a style like you would in a language that
uses mutable variables by default, e.g. most other languages, the
first recommendation is to learn the functional style of doing things,
if you can find a way that keeps the code understandable.
[`loop`](http://clojuredocs.org/clojure.core/loop) may fit your
purpose when other ways are not easy to find.

If you have considered that advice and still want local mutable
variables, other recommendations are:

* [`atom`](http://clojuredocs.org/clojure.core/atom),
  [`reset!`](http://clojuredocs.org/clojure.core/reset!),
  [`swap!`](http://clojuredocs.org/clojure.core/swap!)
* Clojure's
  [`with-local-vars`](http://clojuredocs.org/clojure.core/with-local-vars)
  or the [proteus](https://github.com/ztellman/proteus) library

</details>

### `:wrong-arity`

#### Function calls that seem to have the wrong number of arguments.

<details>

Eastwood warns if a function call is found that has a number of
arguments not equal to any of the defined signatures (also called
arities) of the function.

Often this is a mistake in your code, and it is a good idea to correct
the erroneous function call.  However, there are some projects with
unit tests that intentionally make such calls, to verify that an
exception is thrown.

Some libraries explicitly set the `:arglists` metadata on their public
functions for documentation purposes, because `:arglists` are what is
shown by `doc` in the REPL.  This `:arglists` metadata is also used by
Eastwood to determine whether a function is being called with a wrong
arity, so such functions can lead to incorrect warnings from Eastwood.
This is known to affect several functions in
[`java.jdbc`](https://github.com/clojure/java.jdbc) 0.3.x, the
[Midje](https://github.com/marick/Midje) test library, and functions
created with the [Hiccup](https://github.com/weavejester/hiccup)
library's macro `defelem`.

You can create a [config
file](#eastwood-config-files) for Eastwood that specifies the arglists
to use for this linter.  An example for the function `query` in the
[`java.jdbc`](https://github.com/clojure/java.jdbc) Clojure contrib
library is given below, copied from Eastwood's built-in config files
that it uses by default.  The value of the `:arglists-for-linting` key
is a list of all argument vectors taken by the function, as the
argument vectors are given in the function definition, not as modified
via metadata.

```clojure
(disable-warning
 {:linter :wrong-arity
  :function-symbol 'clojure.java.jdbc/query
  :arglists-for-linting
  '([db sql-params & {:keys [result-set-fn row-fn identifiers as-arrays?]
                      :or {row-fn identity
                           identifiers str/lower-case}}])
  :reason "clojure.java.jdbc/query uses metadata to override the default value of :arglists for documentation purposes.  This configuration tells Eastwood what the actual :arglists is, i.e. would have been without that."})
```

</details>

### `:bad-arglists`

#### Function/macro `:arglists` metadata that does not match the number of args it is defined with

<details>

<!-- 

TBD: Give examples of function/macro definitions from Clojure and
other libraries that will cause this warning, and some that will not,
even though they explicitly specify a value for :arglists.

TBD: Perhaps also give this example.  Is it possible for Eastwood to
ever warn about a function defined in this way?  I am guessing it
would not be practical to do so, and whatever :arglists is specified
will never cause a :bad-arglists warning, but verify that.

```clojure
(def
 ^{:tag Boolean
   :doc "Returns false if (pred x) is logical true for every x in
  coll, else true."
   :arglists '([pred coll])
   :added "1.0"}
 not-every? (comp not every?))
```

-->

This linter was created because of the belief that it is better if
the value of `:arglists` for vars accurately represents the number of
arguments that can be used to call the function/macro, as opposed
to some other thing used purely for documentation purposes.

It is true that even Clojure itself does not conform to this
restriction.  For example, the arglists of `defn`, `defmacro`, and
several other macros override `:arglists` for purposes of clearer
documentation.  However, all but these few exceptional macros

Other facts supporting this belief are:

The value of metadata key :arglists is set automatically by macros
like defn and defmacro.

The Clojure compiler uses these arglists to determine things like
the type of the return value of a function call.

It would be nice if Eastwood (in particular its :wrong-arity
linter) and other Clojure development tools could rely upon
:arglists matching the actual arities of the function or macro that
have been defined.

</details>

### `:wrong-ns-form`

#### ns forms containing incorrect syntax or options

<details>

Clojure will accept and correctly execute `ns` forms with references
in vectors, as shown in this example:

```clojure
(ns clojure.tools.test-trace
  [:use [clojure.test]
        [clojure.tools.trace]]
  [:require [clojure.string :as s]])
```

However, Clojure does this despite the documentation of the `ns` macro
showing only parentheses around references.  The `tools.namespace`
library ignores references unless they are enclosed in parentheses,
thus leading Eastwood and any other software using `tools.namespace`
to detect incomplete dependencies between namespaces if they are
enclosed in square brackets.  Thus Eastwood warns about all such
references.

Eastwood also warns about ns forms:

* if more than one `ns` form is found in a file
* if a reference begins with anything except one of the documented
  keywords `:require`, `:use`, `:import`, `:refer-clojure`, `:load`,
  `:gen-class`
* if a reference contains flag keywords that are not one of the
  documented flags `:reload`, `:reload-all`, or `:verbose`
* if a reference contains a valid flag keyword, because typically
  those are only used during interactive use of `require` and `use`
* if a `:require` or `:use` is followed by a list with only 1 item in
  it, e.g. `(:require (eastwood.util))`.  This is a prefix list with
  only a prefix, and no libspecs, so it does not do anything.
* if a `:require` libspec has any of the option keys other than the
  documented ones of `:as` and `:refer`.  Even though it is not
  documented, Clojure's implementation of `require` correctly handles
  options `:exclude` and `:rename`, if `:refer` is also used, so
  Eastwood will not warn about these if `:refer` is present.
* if a `:use` libspec has any option keys other than the documented
  ones `:as` `:refer` `:exclude` `:rename` `:only`.
* if any of the libspec option keys are followed by a value of the
  wrong type, e.g. if `:refer` is followed by anything other than a
  list of symbols or `:all`.

No warning is given if a prefix list is contained within a vector.
Clojure processes prefix lists in vectors, and `tools.namespace`
recognizes them as dependencies as Clojure does.  It is also somewhat
common in the many Clojure projects on which Eastwood is tested.

</details>

### `:wrong-pre-post`

#### function has preconditions or postconditions that are likely incorrect

<details>

Preconditions and postconditions that throw exceptions if they are
false can be specified for any Clojure function by putting a map after
the function's argument vector, with the key `:pre` for preconditions,
or `:post` for postconditions, or both.  The value of these keys
should be a vector of expressions to evaluate, all of which are
evaluated at run time when the function is called.  For example:

```clojure
(defn square-root [x]
  {:pre [(>= x 0)]}
  (Math/sqrt x))

;; AssertionError exception thrown when called with negative number
user=> (square-root -5)

AssertionError Assert failed: (>= x 0)  user/square-root (file.clj:38)
```

It is an easy mistake to forget that the conditions should be a vector
of expressions, and to give one expression instead:

```clojure
(defn square-root [x]
  {:pre (>= x 0)}     ; should be [(>= x 0)] like above
  (Math/sqrt x))

;; No exception when called with negative number!
user=> (square-root -5)
NaN
```

In this case, Clojure does not give any error or warning when defining
`square-root`.  It treats the precondition as three separate assertion
expressions: `>=`, `x`, and `0`, each evaluated independently when the
function is called.  Every value in Clojure is logical true except
`nil` and `false`, so unless you call `square-root` with an argument
equal to one of those values, all three of those expressions evaluate
to logical true, and no exceptions are thrown.

The `:warn-pre-post` linter will warn about any precondition or
postcondition that is not enclosed in a vector.  Even if you do
enclose it in a vector, the linter will check whether any of the
conditions appear to be values that are always logical true or always
logical false.  For example:

```clojure
(defn non-neg? [x]
  (>= x 0))

(defn square-root [x]
  {:pre [non-neg?]}     ; [(non-neg? x)] would be correct
  (Math/sqrt x))

;; No exception when called with negative number!
user=> (square-root -5)
NaN
```

Here Clojure also gives no warning or error.  The assert expression it
evaluates is the value of `non-neg?` -- not the value when you call
`non-neg?` with the argument `x`, but the value of the Var `non-neg?`.
That value is a function, and neither `nil` nor `false`, so logical
true.

</details>

### `:suspicious-test`

#### Tests using `clojure.test` that may be written incorrectly.

<details>

It is easy to misunderstand or forget the correct arguments to
`clojure.test`'s `is` macro, and as a result write unit tests that do
not have the desired effect.  The `:suspicious-test` linter warns
about some kinds of tests that appear to be incorrect.

The form of correct tests written using `clojure.test`'s `is` macro
are as follows:

```clojure
(is expr)
(is expr message-string)
(is (thrown? ExceptionClass expr1 ...))
(is (thrown? ExceptionClass expr1 ...) message-string)
(is (thrown-with-msg? ExceptionClass regex expr1 ...))
(is (thrown-with-msg? ExceptionClass regex expr1 ...) message-string)
```

Here are some examples of tests that are not quite one of these forms,
but will silently pass.  The `:suspicious-test` linter will warn about
all of them, but it may take some thought to learn how to correct the
test.

```clojure
(is ["josh"] names)    ; warns that first arg is a constant
;; Any values except nil or false are treated as logical true in if
;; conditions, so the test above will always pass.  Probably what was
;; intended was:
(is (= ["josh"] names))   ; probably intended


(is (= #{"josh"}) (get-names x))   ; warns that second arg is not a string
;; The warning message is true, but perhaps misleading.  It appears
;; that the author intended to compare the set against the return
;; value of get-names, but the extra parens are legal Clojure.  (= x)
;; always returns true.
(is (= #{"josh"} (get-names x)))   ; probably intended


(is (= ["josh"] names) (str "error when testing with josh and " names))
;; This linter has a special case that if the 2nd arg to 'is' is a
;; form beginning with str, format, or a few other macros and
;; functions commonly used to return strings, it will not issue a
;; warning.  It does this with the assumption that this symbol has not
;; been redefined to return something other than a string.


(deftest test1
  (= 5 (my-func 1))       ; warns that = expr occurs directly inside deftest
  (contains? #{2 4 6} 4)) ; similar warning for contains? or any 'predicate'
                          ; function in clojure.core
;; The = and contains? expressions above will be evaluated during
;; testing, but whether the results are true or false, the test will
;; pass.
(deftest test1
  (is (= 5 (my-func 1)))        ; probably intended
  (is (contains? #{2 4 6} 4)))


(is (thrown? Throwable #"There were 2 vertices returned."
             (expr-i-expect-to-throw-exception)))
;; The above warns that the second arg to thrown? is a regex, but that
;; (is (thrown? ...)) ignores this regex.  Why is it ignored?  Because
;; thrown? can take any number of expressions.  If any of them is a
;; regex, it is evaluated, and then Clojure goes on to evaluate the
;; other expressions.  The developer probably intended to use
;; thrown-with-msg? so that not only is it verified that an exception
;; is thrown, but also that the message in the exception matches the
;; given regex.
(is (thrown-with-msg? Throwable #"There were 2 vertices returned."
                      (expr-i-expect-to-throw-exception)))
```

</details>

### `:suspicious-expression`

#### Suspicious expressions that appear incorrect, because they always return trivial values.

### `:constant-test`

#### A test expression always evaluates as true, or always false

<details>

Warn if you have a test expression in `if`, `cond`, `if-let`,
`when-let`, etc. that is obviously a constant, or it is a literal
collection like a map, vector, or set that always evaluates as true.

For example:

```clojure
;; These all cause :constant-test warnings, because the test condition
;; is a compile-time constant.
(if false 1 2)
(if-not [nil] 1 2)
(when-first [x [1 2]] (println "Goodbye"))

;; Even though Eastwood knows that the test condition is not a compile
;; time constant here, it is a map, which always evaluate to logical
;; true in a test condition.
(defn foo [x]
  (if {:a (inc x)} 1 2))
```

The blanket approach to disabling all `:constant-test` warnings is to
use the `:exclude-linters` keyword in the Eastwood options map, or
from Leiningen you can merge the following into your `project.Clj` or
`$HOME/.lein/profiles.clj` file:

```clojure
:eastwood {:exclude-linters [:constant-test]}
```

Starting with Eastwood version 0.2.1, the more surgical approach is to
add expressions to a [config file](#eastwood-config-files)
to disable these warnings, only when they occur within particular
macro expansions.  Search those config files for `:constant-test` to
find examples.

It is common across Clojure projects to use `:else` as the last
'always do this' case at the end of a `cond` form.  It is also fairly
common to use `true` or `:default` for this purpose, and Eastwood will
not warn about these.  If you use some other constant in that
position, Eastwood will warn.

It is somewhat common to use `(assert false "msg")` to throw
exceptions in Clojure code.  This linter has a special check never to
warn about such forms.

</details>

### `:unused-meta-on-macro`

#### Metadata on a macro invocation is ignored by Clojure

<details>

When you invoke a macro and annotate it with metadata, in most cases
that metadata will be discarded when the macro is expanded, unless the
macro has been written explicitly to use that metadata.

As a simple example, the macro `my-macro` below will have all metadata
discarded any time it is invoked:

```clojure
(require 'clojure.java.io)
(import '(java.io Writer StringWriter))

(defn my-fn [x]
  (clojure.java.io/writer x))

;; Example behavior below is for Clojure 1.5.0 through 1.7.0 alphas,
;; at least.
(defmacro my-macro [x]
  (if (>= (compare ((juxt :major :minor) *clojure-version*) [1 5])
          0)
    `(my-fn ~x)
    'something-else))

;; No metadata here, so nothing to lose, and no Eastwood warning.
;; .close call will give reflection warning, though.
(.close (my-macro (StringWriter.)))

;; All metadata is discarded, including type tags like ^Writer, which
;; is just a shorthand for ^{:tag Writer}.  Clojure will give a
;; reflection warning, which is mightily confusing if you are not
;; aware of this issue.  Eastwood will warn about it.
(.close ^Writer (my-macro (StringWriter.)))
```

If your purpose for annotating a macro invocation with metadata is to
type hint it, to avoid reflection in a Java interop call, you can work
around this behavior by binding the macro invocation return value to a
symbol with `let`, and type hint that symbol.  For example:

```clojure
;; No reflection warning from Clojure, and no warning from Eastwood,
;; for this.
(let [^Writer w (my-macro (StringWriter.))]
  (.close w))
```

A Clojure ticket has been filed for this behavior:
[CLJ-865](http://dev.clojure.org/jira/browse/CLJ-865).  However, most
ways of changing it would change the behavior of at least some
existing Clojure code, so it seems unlikely to change.  Hence, this
Eastwood linter to alert people unaware of the behavior.

Most Java interop forms are macro invocations, expand like them, and
thus lose any metadata annotating their invocations.  However, there
are special cases in the Clojure compiler where such Java interop
forms will have `:tag` type hint metadata preserved for them.
Eastwood will warn if you try to use metadata on such a Java interop
form that is discarded by the compiler.

Java interop forms that remove _all_ metadata on them, even type
hints:

* constructor calls - `(ClassName. args)`

Java interop forms that remove all metadata, except they explicitly
preserve type hints:

* class method calls - `(ClassName/staticMethod args)`
* class field access - `(ClassName/staticField)`
* instance method calls - `(.instanceMethod obj args)`
* instance field access - `(.instanceField obj)`

Java interop forms that are not macroexpanded, and thus do not lose
any metadata annotating them:

* constructor calls beginning with `new` - `(new ClassName args)`
* calls beginning with a `.` (not `.close`, but just a `.` by itself) - `(. x close)`

Clojure's `clojure.core/fn` macro uses the hidden `&form` argument to
all Clojure macros to explicitly preserve the metadata on any `(fn
...)` forms.  Eastwood has a special case not to warn about those
cases.

</details>

### `:unused-ret-vals`

#### Unused values, including unused return values of pure functions, and some others functions where it rarely makes sense to discard its return value.

<details>

The variant `:unused-ret-vals-in-try` is also documented here.

Values which are unused are sometimes a sign of a problem in your
code.  These can be constant values, values of locally bound symbols
like let symbols or function arguments, values of vars, or return
values of functions.

```clojure
(defn unused-val [a b]
  a b)   ; b is returned.  a's value is ignored
```

Calling a side-effect-free function in a place where its return value is not used
is likely to be a mistake, and Eastwood issues warnings for this.

```clojure
(defn unused-ret-val [k v]
  (assoc {} k v)   ; return value of assoc is discarded
  [k v])           ; [k v] is the only return value of the function
```

There are many Clojure functions that are not pure functions, but for
which it is probably a mistake to discard its return value.  For
example, `assoc!`, `rand`, and `read`.  Eastwood warns about these,
too.

Discarding the return value of a lazy function such as `map`,
`filter`, etc. is almost certainly a mistake, and Eastwood warns about
these.  If the return value is not used, these functions do almost
nothing, and never call any functions passed to them as args, whether
those functions have side effects or not.

```clojure
;; This use of map calls print 4 times, because the REPL will print
;; the return value of anything you evaluate in it, and thus force its
;; evaluation.
user=> (map print [1 2 3 4])
(1234nil nil nil nil)

;; The call to foo1 below will never call print, because nothing is
;; forcing the evaluation of the return value of the lazy function map
user=> (defn foo1 [coll]
  #_=>   (map print coll)
  #_=>   (count coll))
#'user/foo1
user=> (foo1 [1 2 3 4])
4
```

There are many Clojure functions that take other functions as
arguments.  These are often called higher order functions, or HOFs.
Some of these HOFs are 'conditionally pure', meaning that if the
functions passed as arguments are pure, then so is the HOF.  For
example, `mapv`, `group-by`, `every?`, and `apply` are conditionally
pure HOFs (and none of them are lazy).

Eastwood warns about discarding the return value of a conditionally
pure non-lazy HOF.  It is not sophisticated enough to check whether
the function arguments to the HOF are non-pure, e.g. it will warn
about a case like `(mapv print args)` if the return value is
discarded, even though a person can easily see that discarding the
return value still causes the side effects of `print` to occur.  As a
special case, Eastwood only warns about the return value of `apply`
being discarded based upon the properties of the function passed as
its first argument, so `(apply print args)` will not cause a warning
because `print` is known by Eastwood to have side effects.

It is not commonly done, but it can be useful to invoke what we have
called a pure function, even if its return value is discarded.  The
only reason to do so (known to this author) is when the 'pure'
function can throw an exception for some argument values judged to be
invalid, and you want to determine whether your data is valid by
calling the pure function and catching an exception if it is thrown.
For example, `str` can throw an exception if the value passed to it is
unprintable.

Technically, a function that can throw an exception is not really pure
in the mathematical sense of the term.  However, it is common to refer
to it as a pure function if the only thing 'unpure' about it is the
possibility of throwing an exception, since in most circumstances it
is pure.

If you use `clojure.test`, this warning can also occur if such an
expression is evaluated in an `(is (thrown? ThrowableType
(expression)))`.  This is another case of an expression's return value
being discarded, in the expansion of the `is` macro.  The expression
is being evaluated only to see if it will throw an exception.

When such a discarded return value occurs directly within the body of
a `try` form, it is warned about with a linter having a different
name, `:unused-ret-vals-in-try`.  The detection of an unused return
value being done within a `try` form is done after macro expansion.
Thus since the `(is ...)` forms of `clojure.test` macro expand into
try blocks, unused return values _directly_ in their bodies will be
reported by the `:unused-ret-vals-in-try` linter.  You can exclude
this linter, but keep `:unused-ret-vals`, or vice versa, if one or the
other linter gives too many false warnings for your code.

Implementation note: Eastwood does not automatically determine whether
a function is pure, conditionally pure, a HOF, etc.  All of these
properties were determined by manual inspection and recorded in a map
of data about Clojure core functions.

</details>

### `:local-shadows-var`

#### A local name, e.g. a function arg, let binding, or record field name, has the same name as a global Var, and is called as a function

<details>

Many functions in `clojure.core` have names that you might like to use
as local names, such as function arguments or let bindings.  This is
not necessarily a mistake, and Clojure certainly allows it, but it is
easy to do so and accidentally introduce a bug.

For example, below the intent was to call `clojure.core/count` on the
collection `data`, but instead the `let` binds a value to `count`, and
that value is called as a function instead (or at least Clojure tries
to call it as a function):

```clojure
(let [{count :count
       data  :data} (fetch-data)
      real-count (count data)]
  ... )
```

It is very common in Clojure code to 'shadow' the names of global Vars
like `name`, `list`, `symbol`, etc., so the `:local-shadows-var`
linter does not warn every time you use such a local name.  It only
does so if:

* The name is used as the first position in a form, as for a function
  call, and
* Eastwood cannot prove that the value bound to the name is a
  function.

For example, this will not cause a warning, because it is assumed that
the developer has intentionally used the name `replace` as a locally
defined function.

```clojure
(let [replace #(str (biginteger %))]
  (println (replace 5)))
```

The following _will_ cause a warning, because Eastwood's analysis is
not sophisticated enough to determine that the value bound to
`replace` is a function.

```clojure
(let [replace (comp str biginteger)]
  (println (replace 5)))
```

The following example will not cause a warning, because even though
`pmap` is determined to have a non-function value, Eastwood does not
'know' that the function call to `map` will use `pmap`'s value as a
function.

```clojure
(let [pmap {:a 1 :b 2}]
  (println (map pmap [1 2 3])))
```

Eastwood also warns if a field of a Clojure record is called as a
function, where there is a Var visible with the same name.

No matter what kind of local symbol is shadowing a Var, you can force
use of the Var by qualifying it with a namespace, or an alias of a
namespace as created by `:as` in a `require` form.

</details>

### `:wrong-tag`

#### An incorrect type tag for which the Clojure compiler does not give an error

<details>

You can use a type tag on a Var name, like in the examples below.
This does not force the type of the value assigned to the Var, but
Clojure does use the type tag to avoid reflection in Java interop
calls where the Var name is used as an argument.

```clojure
;; Correct primitive/primitive-array type hints on Vars
(def ^{:tag 'int} my-int -2)
(def ^{:tag 'bytes} bytearr1 (byte-array [2 3 4]))
(defn ^{:tag 'boolean} positive? [x] (> x 0))
```

However, the following examples cause Clojure to use the values of the
functions `clojure.core/int`, `clojure.core/bytes`, and
`clojure.core/boolean` as (incorrect) type tags.  They will not help
Clojure avoid reflection in Java interop calls.  Clojure gives no
errors or warnings for such type hints, but Eastwood will.  This
happens because the Clojure compiler `eval`s metadata applied to a Var
being `def`d, as documented [here](http://clojure.org/special_forms).

```clojure
;; Incorrect primitive/primitive-array type hints on Vars, for which
;; Eastwood will warn
(def ^int my-int -2)
(def ^bytes bytearr1 (byte-array [2 3 4]))
(defn ^boolean positive? [x] (> x 0))
```

For Java classes, it is correct to use type tags on Vars like in these
examples:

```clojure
;; Correct Java class type hints on Vars
(def ^Integer my-int -2)
(defn ^Boolean positive? [x] (> x 0))
(defn ^java.util.LinkedList ll [coll] (java.util.LinkedList. coll))

;; For type tags on the Var name, you may even avoid fully qualifying
;; the name, as long as you have imported the class.  Unlike some
;; examples below with type tags on the argument vector, this does not
;; cause problems for Clojure.
(defn ^LinkedList l2 [coll] (java.util.LinkedList. coll))
```

You can define functions that take primitive long or double values as
arguments, or that return a primitive long or double as its return
value, as shown in the examples below.  Note that the return type tag
must be given immediately before the argument vector, _not_ before the
name of the function.

```clojure
;; correct primitive type hints on function arguments and return value
(defn add ^long [^long x ^long y] (+ x y))
(defn reciprocal ^double [^long x] (/ 1.0 x))
```

Clojure will give a compilation error with a clear message if you
attempt to use any primitive type besides `long` or `double` in this
way.

You can also type hint function arguments and return values with Java
class names.

Such type hints on function arguments can help avoid reflection in
Java interop calls within the function body, and it does not matter
whether such type hints use fully qualified Java class names
(e.g. `java.util.LinkedList`) or not (e.g. `LinkedList`), although
using the version that is not fully qualified only works if it is in
the `java.lang` package, or you have imported the package into the
Clojure namespace.

#### `:wrong-tag` warnings in uses of `extend-type` and `extend-protocol` macro


`extend-type` and `extend-protocol` convenience macros take the class
name you specify and propagate them as type tags on the first argument
of all functions.  This is handy, as long as the class name is a valid
type tag, as in this example:

```clojure
(defprotocol MyType
  (get-type [x]))

;; A more interesting example would avoid reflection only because of
;; the auto-propagated type tags on the argument m.  Better example
;; welcome.

(extend-protocol MyType
  Long
  (get-type [m] :long)
  Double
  (get-type [m] :double))

;; The extend-protocol expression above becomes the following after
;; macro expansion, with valid type tags ^Long and ^Double.

(do
  (clojure.core/extend Long
    MyType
    {:get-type (fn ([^{:tag Long} m] :long))})
  (clojure.core/extend Double
    MyType
    {:get-type (fn ([^{:tag Double} m] :double))}))

(get-type 5)
;; => :long

(get-type 5.3)
;; => :double
```


However, if you try to use `extend-type` or `extend-protocol` with an
expression that evaluates to a class at run time, e.g. `(Class/forName
"[D")` as the type, most things will work correctly, but it will also
expand to code that has that expression as a type tag on the first
argument of all functions.  That expression is not a valid type tag.
Clojure silently ignores such type tags, so there are no errors or
warnings during compilation.  Since the invalid type tag is ignored,
you will be disappointed if you were relying on it to avoid
reflection.

Note: `(Class/forName "[D")` evaluates to the Java class for an array
of primitive doubles.  In places where you want to use such a type as
a type tag, you can use `^doubles` in Clojure.  However, that will not
work as an argument to `extend` or its variants.

```clojure
(defprotocol PGetElem
  (get-elem [m idx]))

;; This will cause reflection on the aget call, because m has an
;; invalid, ignored type tag of (Class/forName "[D").  Eastwood will
;; give a :wrong-tag warning on m.

(extend-protocol PGetElem
  (Class/forName "[D")
    (get-elem [m idx] (aget m idx)))

;; This also causes reflection on the aget call, because the ^doubles
;; type tag is replaced by the invalid, ignored type tag when
;; extend-protocol is macroexpanded.  Eastwood will give a :wrong-tag
;; warning on m.

(extend-protocol PGetElem
  (Class/forName "[D")
    (get-elem [^doubles m idx] (aget m idx)))

;; No reflection here, because the valid type hint ^doubles is inside
;; of the aget call, where extend-protocol does not overwrite it.
;; Eastwood will give a :wrong-tag warning on m.

(extend-protocol PGetElem
  (Class/forName "[D")
    (get-elem [m idx] (aget ^doubles m idx)))

;; You will always get an Eastwood :wrong-tag warning if you use a
;; run-time evaluated expression as a type in extend-protocol or
;; extend-type.  You can suppress Eastwood's warning, or instead use
;; the function extend.

;; This is the only version that both (a) avoids reflection, and (b)
;; Eastwood will not warn about.  It calls the function extend, and
;; uses a correct type tag ^doubles on the first argument.  You could
;; also put the type tag inside of the aget call if you prefer.

(extend (Class/forName "[D")
 PGetElem
 {:get-elem
  (fn ([^doubles m idx]
    (aget m idx)))})
```

See Clojure ticket
[CLJ-1308](http://dev.clojure.org/jira/browse/CLJ-1308).  Vote on it
if you are interested in Clojure changing its implementation and/or
make its documentation more explicit.

</details>

### `:unused-fn-args`

#### Unused arguments of functions, macros, methods

<details>

This linter is disabled by default, because it often produces a large
number of warnings that are not errors.  You must explicitly enable it
if you wish to see these warnings.

Writing a function that does not use some of its arguments is not
necessarily a mistake.  In particular, it is common for multimethods
defined with `defmulti` to have a dispatch function that only uses
some of its arguments.

However, Eastwood warns about such unused function arguments, to
signal to a developer that there might be a problem.

Eastwood will not issue an `:unused-fn-args` warning for any argument
whose name begins with an underscore character, such as `_` or
`_coll`.  It is a common convention in Clojure to use `_` as a name
for something that will not be used, and this convention is recognized
and extended by Eastwood.  If you wish to enable the `:unused-fn-args`
linter, but have several unused arguments that are acceptable to you,
consider prepending an underscore to their names to silence the
warnings.

</details>

### `:unused-locals`

#### Symbols bound with `let` or `loop` that are never used

<details>

This linter is disabled by default, because it often produces a large
number of warnings, and even the ones that are correct can be
annoying, and usually just vestigial code that isn't really a bug (it
might hurt your performance).

However, for many projects tested, the warnings are correct.  If you
wish to eliminate such symbols from your code using these warnings,
you must explicitly enable it.  You can specify it in `:add-linters`
on the command line or when invoked from a REPL.  To avoid specifying
it each time when using Leiningen, you can merge a line like the
following into your `project.clj` file or user-wide
`$HOME/.lein/profiles.clj` file.

```clojure
:eastwood {:add-linters [:unused-locals]}
```

If you bind a value to a symbol in a `let` or `loop` binding, but then
never use that symbol, this linter will issue a warning for it.  You
can disable individual warnings by prepending the symbol name with a
`_` character, as for the `:unused-fn-args` linter.

The warning occurs even if the `let` or `loop` is the result of
expanding a macro, so sometimes the source of the warning is not
obvious.  If the symbol name looks like `somename__5103`, it is most
likely from a `let` introduced during macroexpansion.  Such warnings
may be split into a separate disabled-by-default linter in a future
version of Eastwood.

Destructuring forms like `[x & xs]` and `{:keys [keyname1 keyname2
...] :as mymap}` are macroexpanded into `let` forms, even when used as
function arguments, and can thus cause these warnings.  You can
disable individual ones by prepending their names with a `_`
character.

It may seem a little odd to disable such destructuring warnings for
keys in a map, since it changes the name of the keyword in the
macroexpansion, and thus it will not be bound to the same value.  But
hey, the value wasn't being used anyway, right?  Consider removing it
from the list of keywords completely.

</details>

### `:unused-namespaces`

#### A namespace you use/require could be removed

<details>

This linter is disabled by default, because it can be fairly noisy.
You must explicitly enable it if you wish to see these warnings.

Warn if a namespace is given in an `ns` form after `:use` or
`:require`, but the namespace is not actually used.  Thus the
namespace could be eliminated.

This linter is known to give false positives in a few cases.  See
these issues:

* Issue [#192](https://github.com/jonase/eastwood/issues/192)
* Issue [#210](https://github.com/jonase/eastwood/issues/210)

</details>v

### `:unused-private-vars`

#### A Var declared to be private is not used in the namespace where it is def'd

<details>

This linter is disabled by default, but at least with a collection of
projects on which Eastwood is frequently tested, it is an uncommon
warning for most of them (Seesaw is an exception where it is common).
You must explicitly enable it if you wish to see these warnings.

If a Var is defined to be private using `^:private`, `^{:private
true}`, `defn-`, etc., but is not used elsewhere in the same
namespace, it is likely to be dead code.  It is still possible to
refer to the Var in another namespace using syntax like
`#'name.space/private-var-name`, but this is not checked for by this
linter.

This linter never warns for private Vars that also have `^:const` or
`^{:const true}` metadata.  This is due to some uncertainty whether
uses of such Vars can be reliably detected in the `tools.analyzer`
ASTs.

It will cause undesirable warnings in case like this, which have been
seen in some namespaces:

```clojure
(defn- private-fn [x]
  (inc x))

(defmacro public-macro [y]
  `(#'my.ns/private-fn ~y))
```

It is not straightforward for Eastwood to determine from the ASTs that
private-fn is used elsewhere in the namespace, since syntax quoting
with the backquote character causes the resulting code to not refer to
the Var directly, but to create a function that _only when evaluated_
contains `(var my.ns/private-fn)`.

</details>

### `:unlimited-use`

#### Unlimited `(:use ...)` without `:refer` or `:only` to limit the symbols referred by it.

<details>

An `ns` statement like the one below will refer all of the public
symbols in the namespace `clojure.string`:

```clojure
(ns my.namespace
  (:use clojure.string))
```

Any symbols you use from namespace `clojure.string` will typically
have no namespace qualifier before them, which is likely your reason
for using `use` instead of `require`.  This can make it difficult for
people to determine which namespace the symbols are defined in.  A
`require` followed by `:refer` and a list of symbols makes it clearer
to readers the origin of such symbols.  You can also put in an `:as
str` in the same `require` so you have an alias to prefix any other
symbols you need from the namespace:

```clojure
(ns my.namespace
  (:require [clojure.string :as str :refer [replace join]]))
```

The `:unlimited-use` linter will not warn about 'limited' `use`
statements, i.e. those with explicit `:only` or `:refer` keywords to
limit their effects, such as these:

```clojure
(ns my.namespace
  (:use [clojure.string :as str :only [replace]]
        [clojure.walk :refer [prewalk]]
        [clojure [xml :only [emit]]]))
```

In addition, since it is so common (and in my opinion, harmless) to do
an unlimited use of namespace `clojure.test` in test files, this
linter never warns about `clojure.test`.

For an infrequently-changing namespace like `clojure.string`, the set
of symbols referred by this `use` is pretty stable across Clojure
versions, but even so, it only takes one symbol added to shadow an
existing symbol in your code to ruin your day.

</details>

### `:non-dynamic-earmuffs`

<details>

Vars marked `^:dynamic` should follow the "earmuff" naming convention, and vice versa:

* `(def foo 42)` (OK: non-dynamic, non-earmuffed)
* `(def ^:dynamic foo 42)` (NOK: dynamic, non-earmuffed)
* `(def *foo* 42)` (NOK: earmuffed, non-dynamic)
* `(def ^:dynamic *foo* 42)` (OK: dynamic, earmuffed)

</details>

### `:boxed-math`

#### Boxed math warnings from the Clojure compiler

<details>

See: [`*unchecked-math*`](https://clojuredocs.org/clojure.core/*unchecked-math*)

Disabled by default because it's not customary or necessarily justified to aim for a boxed-math-free codebase.

Note that if enabling it, all code will be evaluated with a surrounding `*unchecked-math* :warn-on-boxed` binding,
which not only enables the warnings but it actually affects the final code that will be emitted
(although in minor ways, only concerned with unchecked math matters).

Generally this won't affect you in any way except in the case that you are invoking Eastwood in a REPL,
such that namespaces re-compiled by Eastwood's analysis will be visible and used by your application.

</details>

### `:performance`

#### Performance warnings from the Clojure compiler

<details>

The Clojure compiler optionally emits performance warnings related to the use of `case` and `recur` and their relationship with primitive numerics.

> Please refer to https://clojure.org/reference/java_interop for a guide on primive math
(tldr: use `(long)`, or occasionally `^long` where it is permitted).

Eastwood wraps these warnings, enhancing them when needed (the reported file name can be misleading),
restricting them to _your project's_ source paths and allowing them to be omitted on a file/line basis.  

This linter is disabled by default because it's not customary or necessarily justified to address these warnings.
For some corner cases it might not be even possible (however the [`:ignored-faults`](https://github.com/jonase/eastwood#ignored-faults) would allow you to prevent that corner case from failing your build).

</details>

### `:reflection`

#### Reflection warnings from the Clojure compiler

<details>

Addressing reflection warnings systematically is a good idea for many reasons:

* Performance will be improved
* Performance will easier to measure
  * Even assuming that JITs can emit optimizations akin to manual type hints, having one's code left unoptimized for an indefinite time (maybe forever, in your local JVM) makes it harder to accurately measure performance, as it can be potentially full of distractions caused by slow, reflective calls.
* Code will be more maintainable, as anyone reading the code will know the class a given piece of code is dealing with
  * e.g. some code may be dealing with a very unusual/specific Java class. By using a type hint, maintainers can quickly know of this class instead of having to reverse-engineer that info. 
* Increased compatibility with newer JDKs
  * newer JDKs may emit warnings or even not work at all depending on reflective access.
  * this has changed substantially how Clojure programmers deal with reflection - before it was more of an optimization only.
* Increased compatibility with GraalVM native images [ref](https://www.graalvm.org/reference-manual/native-image/Reflection/)
* Better integration with various Clojure tooling
  * e.g. [compliment](https://github.com/alexander-yakushev/compliment) (used by CIDER) is able to perceive type hints and offer better completions accordingly.
* They might be propagated downstream
  * If you are a library or tooling author, reflective code you write will show up as warnings for your consumers.
  * Consumers might be actually be negatively impacted in terms of performance - one never can know how other people use a given piece of code.
  * Since consumers can't do anything to directly fix this, it's most considerate to avoid in advance any reflective calls. 

Eastwood helps you systematically avoid reflection warnings by considering reflection warnings yet another lintable thing.

It generally doesn't matter whether a given ns uses `(set! warn-on-reflection ...)` - Eastwood analyses each top-level form with a `binding` overriding any surrounding choice.

The default behavior is only emitting warnings if the reflection happens inside your source paths or test paths: this way one doesn't have to pay a price for unrelated code.

However if a third-party macro expands to reflective access within our source path, it will be reported.
This is because, in the end, one is creating reflective code in _one's_ codebase, which can be a severe problem and therefore should be fixed, even if it can take some extra effort. 

Sibling linters such as `:wrong-tag` and `:unused-meta-on-macro` help guaranteeing that reflection is being addressed in a veridic way.

</details>

### `:keyword-typos`

#### Keywords that may have typographical errors

<details>

This linter is disabled by default, because it often produces a large
number of warnings that are not errors.  You must explicitly enable it
if you wish to see these warnings.

If you use a keyword like `:frangible` in several places in your
source code, but then in one place you accidentally type `:frangable`
instead, that will likely lead to incorrect behavior of your program.

This linter cannot guarantee finding such misspelled keywords, but if
there is a keyword in your source code that appears only once, and is
nearly the same spelling as keywords that appear elsewhere in the same
source file, this linter will warn about them.  It can of course
report keywords that are exactly what you intended them to be.

As implemented now, this linter only works if (a) there are no
keywords of the form `::ns-alias/name` in the file, or (b) the first
expression in the file is an `ns` expression, and the namespace
remains the same throughout the file.  This is a common convention
followed by most Clojure source code, and required by several other
Clojure development tools.

</details>

## Ignored faults

If there are specific instances of linter faults that you need to supress
(e.g. for making a CI build pass), you can use the `:ignored-faults` option.

It has the following shape:

```clj
;;linter-name            ns-name                target
;;---                    ---                    ---
{:implicit-dependencies {'example.namespace     [{:line 3 :column 2}]
                         'another.namespace     [{:line 79}]
                         'random.namespace      [{:line 89}, {:line 110}, {:line 543 :column 10}]} 
 :unused-ret-vals       {'yet.another.namespace true}}
```

An entry like `:implicit-dependencies {'example.namespace [{:line 3 :column 2}]` has the meaning
"the linter `:implicit-dependencies` should be ignored in line 3, column 2".

Note that the `target`s are expressed as vectors, since there may be multiple instances to ignore.

The following are acceptable `target`s:

* `[{:line 1 :column 1}]`
  * will only ignore a linter if line _and_ column do match
* `[{:line 1}]`
  * will match line, disregarding the column
  * it's a bit more lenient than the previous syntax, while not too much
* `true`
  * will match any ocurrence within the given namespace, regardless of line/column
  * this is the most lenient choce, which of course can create some false negatives.
  * if passing `true`, you don't need to wrap it in a vector.

> Please, if encountering an issue in Eastwood, consider reporting it in addition to (or instead of) silencing it.
> This way Eastwood can continue to be a precise linter, having as few false positives as possible.

## Ignoring linter sub `:kind`s

A given linter may have different `:kind`s of emitted warnings. For example the `:suspicious-test` linter has the following kinds:

> `:first-arg-is-string, :first-arg-is-constant-true, :second-arg-is-not-string, :thrown-regex, :thrown-string-arg, :string-inside-thrown`

That `:kind` is reported to stdout when running Eastwood as a tool, and returned as data when invoking Eastwood programatically. 

You can prevent a specific `:kind` from emitting warnings by using any of the following syntaxes:

```clj
;; simple pairs of [<linter>, <kind>]:
:exclude-linters [[:suspicious-test :first-arg-is-constant-true], [:suspicious-test :thrown-regex]]
;; or
;; pairs of [<linter>, <vector of kinds for that linter>]:
:exclude-linters [[:suspicious-test [:first-arg-is-constant-true :thrown-regex]]]

;; You can mix and match both syntaxes.
;; You can also mix them with single keywords which denote a whole linter to be omitted:
:exclude-linters [:constant-test, [:suspicious-test :first-arg-is-constant-true]]
```

With newer versions, Eastwood disables `[:suspicious-test :first-arg-is-constant-true]` by default.

To re-enable it, set `:exclude-linters` to `[]` or any custom value. Whatever value you provide will replace the default entirely.  

## Changelog

See the [changes.md](https://github.com/jonase/eastwood/blob/master/changes.md) file.

## For Eastwood developers

To be on the bleeding edge, install Eastwood in
your local Maven repository:

    $ cd path/to/eastwood
    $ lein with-profile -user,-dev,+eastwood-plugin install

Then add `[jonase/eastwood "1.4.1"]` to
your `:plugins` vector in your `:user` profile, perhaps in your
`$HOME/.lein/profiles.clj` file.


## License

Copyright (C) 2012-2023 Jonas Enlund, Nicola Mometto, and Andy Fingerhut

Distributed under the Eclipse Public License, the same as Clojure.

The source code of the following libraries has been copied into
Eastwood's source code, and each of their copyright and license info
is given below.  They are all distributed under the Eclipse Public
License 1.0.

<details>

### core.cache

[core.cache](https://github.com/clojure/core.cache)

Copyright (c) Rich Hickey, Michael Fogus and contributors, 2012. All rights reserved.  The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution. By using this software in any fashion, you are agreeing to be bound by the terms of this license.  You must not remove this notice, or any other, from this software.

### core.memoize

[core.memoize](https://github.com/clojure/core.memoize)

Copyright (c) Rich Hickey and Michael Fogus, 2012, 2013. All rights reserved.  The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution. By using this software in any fashion, you are agreeing to be bound by the terms of this license.  You must not remove this notice, or any other, from this software.

### data.priority-map

Copyright (C) 2013 Mark Engelberg

Distributed under the Eclipse Public License, the same as Clojure.

[data.priority-map](https://github.com/clojure/data.priority-map)

### tools.analyzer

[tools.analyzer](https://github.com/clojure/tools.analyzer)

Copyright © 2013-2014 Nicola Mometto, Rich Hickey & contributors.

Distributed under the Eclipse Public License, the same as Clojure.

### tools.analyzer.jvm

[tools.analyzer.jvm](https://github.com/clojure/tools.analyzer.jvm)

Copyright © 2013-2014 Nicola Mometto, Rich Hickey & contributors.

Distributed under the Eclipse Public License, the same as Clojure.

### tools.namespace

[tools.namespace](https://github.com/clojure/tools.namespace)

Copyright © 2012 Stuart Sierra All rights reserved. The use and
distribution terms for this software are covered by the
[Eclipse Public License 1.0] which can be found in the file
epl-v10.html at the root of this distribution. By using this software
in any fashion, you are agreeing to be bound by the terms of this
license. You must not remove this notice, or any other, from this
software.

[Eclipse Public License 1.0]: http://opensource.org/licenses/eclipse-1.0.php

### tools.reader

[tools.reader](https://github.com/clojure/tools.reader)

Copyright © 2013-2014 Nicola Mometto, Rich Hickey & contributors.

Licensed under the EPL. (See the file epl.html.)

</details>
