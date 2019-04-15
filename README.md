# eastwood - a Clojure lint tool

[![Dependencies Status](https://versions.deps.co/jonase/eastwood/status.svg)](https://versions.deps.co/jonase/eastwood)
[![Downloads](https://versions.deps.co/jonase/eastwood/downloads.svg)](https://versions.deps.co/jonase/eastwood)
[![Build Status](https://circleci.com/gh/jonase/eastwood/tree/master.svg?style=shield&circle-token=26d8d2fa593675196734ac6c28ee16e0a9183806)](https://circleci.com/gh/jonase/eastwood)
[![Clojars Project](https://img.shields.io/clojars/v/jonase/eastwood.svg)](https://clojars.org/jonase/eastwood) [![Join the chat at https://gitter.im/eastwood-linter/Lobby](https://badges.gitter.im/eastwood-linter/Lobby.svg)](https://gitter.im/eastwood-linter/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

<img src="doc/Clint_Eastwood_-_1960s_small.jpg"
 alt="Picture of Clint Eastwood in 'A Fistful of Dollars' (1964)" title="Clint Eastwood in 'A Fistful of Dollars' (1964)"
 align="right" />

> "Now remember, things look bad and it looks like you're not gonna
> make it, then you gotta get mean.  I mean plumb, mad-dog mean.
> 'Cause if you lose your head and you give up then you neither live
> nor win.  That's just the way it is."
> - Josey Wales, played by Clint Eastwood in "The Outlaw Josey Wales"

Eastwood is a Clojure
[lint](http://en.wikipedia.org/wiki/Lint_%28software%29) tool that
uses the [tools.analyzer](https://github.com/clojure/tools.analyzer)
and
[tools.analyzer.jvm](https://github.com/clojure/tools.analyzer.jvm)
libraries to inspect namespaces and report possible problems.

Clojure version compatibility:

* Eastwood supports only Clojure on Java, not ClojureScript or
  Clojure/CLR.

* Clojure 1.9.0 - Use Eastwood 0.2.5 or later.  This is necessary if
  you want to lint projects that use the new [map namespace
  syntax](https://clojure.org/reference/reader#map_namespace_syntax),
  for example.

* Clojure 1.8.0 - Use Eastwood 0.2.2 or later.  There are known
  problems using Eastwood 0.2.1 and earlier with Clojure 1.8.0.

* 1.6.0, or 1.7.0 - Many versions of Eastwood have been tested with
  these, up through Eastwood 0.2.3.

* Clojure 1.5.1 - Use Eastwood 0.2.5 or earlier.

* Clojure 1.4.0 - Use Eastwood 0.1.5 or earlier.

The `.cljc` files introduced in Clojure 1.7.0 are linted, starting
with Eastwood version 0.2.4.  Earlier Eastwood versions ignore such
files.


## Installation & Quick usage

Eastwood can be run from within a REPL, regardless of which build
tools you may use.  See the [instructions
here](#running-eastwood-in-a-repl).

Eastwood can be run from the command line as a
[Leiningen](http://leiningen.org) plugin.  As a Leiningen plugin,
Eastwood has been tested most with Leiningen versions 2.4 and later.
Eastwoods support for Leiningen 1.x was dropped at version 0.3.0.
There is a known bug in Leiningen 2.6.0 where many plugins, including
Eastwood, often cause exceptions to be thrown -- Leiningen 2.6.1 was
released to fix that problem.

Merge the following into your `$HOME/.lein/profiles.clj` file:

```clojure
{:user {:plugins [[jonase/eastwood "0.3.5"]] }}
```

To run Eastwood with the default set of lint warnings on all of the
Clojure files in the source _and_ test paths of your project, use the
command:

    $ lein eastwood

WARNING: If loading your code (particularly test files) causes side
effects like writing files, opening connections to servers, modifying
databases, etc., running Eastwood on your code will do that, too.
Eastwood is _no less dangerous_ than loading your code, and should be
no more dangerous.  To confine linting to files in your
`:source-paths`, use this command instead:

    $ lein eastwood "{:namespaces [:source-paths]}"

## deps.edn
If you're using `deps.edn`, you can set options to eastwood linter in a
 EDN map, like this:
```clojure
{:aliases
  {:eastwood
      {:main-opts ["-m" "eastwood.lint" {:source-paths ["src"]}]
	   :extra-deps {jonase/eastwood {:mvn/version "RELEASE"}}}}}

```
to your `deps.edn`, and you should then be able to run Eastwood as

```sh
$ clj -A:eastwood
```

If it is not obvious what a warning message means, _please_ check the
next section, which has a `[more]` link for each type of warning.
Most types of warning messages have a page or more of text describing
the warning, why it occurs, and sometimes suggestions on what you can
do about it.  Also note that there are several types of warnings
marked as '(disabled)', meaning that by default no such warnings will
be checked for.  You may wish to enable those for your project.  See
the [Usage](#usage) section for options to enable or disable types of
warnings for your entire project.

See [Editor Support](#editor-support) below for instructions on using
a text editor or IDE to quickly take you to the file, line, and column
of each warning message.

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

See section [For Eastwood developers](#for-eastwood-developers) below
for instructions on trying out the latest unreleased version of
Eastwood.


## What's there?

Eastwood warns when it finds the following kinds of things.  Each
keyword below is the name of the "linter".  That name can be used on
the command line to enable or disable the linter.  All linters are
enabled by default unless they have '(disabled)' after their name.

| Linter name | Description | Docs |
| ----------- | ----------- | ---- |
| no name* | Inconsistencies between file names and the namespaces declared within them.  * Cannot be disabled. (added 0.1.1) | [[more]](#check-consistency-of-namespace-and-file-names) |
| `:bad-arglists` | Function/macro `:arglists` metadata that does not match the number of args it is defined with (added 0.1.1). | [[more]](#bad-arglists) |
| `:constant-test` | A test expression always evaluates as true, or always false (added 0.2.0). | [[more]](#constant-test) |
| `:def-in-def` | def's nested inside other def's. | [[more]](#def-in-def) |
| `:deprecations` | Deprecated Clojure Vars, and deprecated Java constructors, methods, and fields. | [[more]](#deprecations) |
| `:implicit-dependencies` | A fully-qualified var refers to a namespace that hasn't been listed in `:require`. | [[more]](#implicit-dependencies) |
| `:keyword-typos` (disabled) | Keyword names that may be typos because they occur only once in the source code and are slight variations on other keywords. | [[more]](#keyword-typos) |
| `:local-shadows-var` | A local name, e.g. a function arg or let binding, has the same name as a global Var, and is called as a function (added 0.1.5). | [[more]](#local-shadows-var) |
| `:misplaced-docstrings` | Function or macro doc strings placed after the argument vector, instead of before the argument vector where they belong. | [[more]](#misplaced-docstrings) |
| `:no-ns-form-found` | Warn about Clojure files where no `ns` form could be found (added 0.2.0). | [[more]](#no-ns-form-found) |
| `:non-clojure-file` (disabled) | Warn about files that will not be linted because they are not Clojure source files, i.e. their name does not end with '.clj' (added 0.2.0). | [[more]](#non-clojure-file) |
| `:redefd-vars` | Redefinitions of the same name in the same namespace. | [[more]](#redefd-vars) |
| `:suspicious-expression` | Suspicious expressions that appear incorrect, because they always return trivial values. | [[more]](#suspicious-expression) |
| `:suspicious-test` | Tests using `clojure.test` that may be written incorrectly. | [[more]](#suspicious-test) |
| `:unlimited-use` | Unlimited `(:use ...)` without `:refer` or `:only` to limit the symbols referred by it. | [[more]](#unlimited-use) |
| `:unused-fn-args` (disabled) | Unused function arguments. | [[more]](#unused-fn-args) |
| `:unused-locals` (disabled) | Symbols bound with `let` or `loop` that are never used (added 0.2.0). | [[more]](#unused-locals) |
| `:unused-meta-on-macro` | Metadata on a macro invocation is ignored by Clojure (added 0.2.0). | [[more]](#unused-meta-on-macro) |
| `:unused-namespaces` (disabled) | Warn if a namespace is given in an `ns` form after `:use` or `:require`, but the namespace is not actually used. | [[more]](#unused-namespaces) |
| `:unused-private-vars` (disabled) | Unused private vars (updated in version 0.2.0). | [[more]](#unused-private-vars) |
| `:unused-ret-vals` and `:unused-ret-vals-in-try` | Unused values, including unused return values of pure functions, and some others functions where it rarely makes sense to discard its return value. | [[more]](#unused-ret-vals) |
| `:wrong-arity` | Function calls that seem to have the wrong number of arguments. | [[more]](#wrong-arity) |
| `:wrong-ns-form` | ns forms containing incorrect syntax or options (added 0.2.1). | [[more]](#wrong-ns-form) |
| `:wrong-pre-post` | function has preconditions or postconditions that are likely incorrect (added 0.2.2). | [[more]](#wrong-pre-post) |
| `:wrong-tag` | An incorrect type tag for which the Clojure compiler does not give an error (added 0.1.5). | [[more]](#wrong-tag) |

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
| `:def-in-def`            |  |  |
| `:deprecations`          |  | yes |
| `:keyword-typos`         |  |  |
| `:local-shadows-var`     |  |  |
| `:misplaced-docstrings`  |  |  |
| `:no-ns-form-found`      |  |  |
| `:non-clojure-file`      |  |  |
| `:redefd-vars`           | yes | yes |
| `:suspicious-expression` | yes, for those involving macros | yes |
| `:suspicious-test`       |  |  |
| `:unlimited-use`         |  |  |
| `:unused-fn-args`        |  |  |
| `:unused-locals`         |  |  |
| `:unused-meta-on-macro`  |  |  |
| `:unused-namespaces`     |  |  |
| `:unused-private-vars`   |  |  |
| `:unused-ret-vals` and `:unused-ret-vals-in-try` | yes | yes |
| `:wrong-arity`           | yes | yes |
| `:wrong-ns-form`         |  |  |
| `:wrong-pre-post`        |  |  |
| `:wrong-tag`             |  |  |


## Usage

### From the command line as a Leiningen plugin

Running

    $ lein eastwood

in the root of your project will lint your project's namespaces -- all
of those in your `:source-paths` and `:test-paths` directories and
their subdirectories.  You can also lint individual namespaces in your
project, or your project's dependencies:

    $ lein eastwood "{:namespaces [compojure.handler compojure.core-test] :exclude-linters [:unlimited-use]}"
    == Eastwood 0.2.0 Clojure 1.5.1 JVM 1.7.0_45
    == Linting compojure.handler ==
    Entering directory `/Users/andy/clj/compojure'
    src/compojure/handler.clj:48:8: deprecations: Var '#'compojure.handler/api' is deprecated.
    == Linting compojure.core-test ==
    test/compojure/core_test.clj:112:21: suspicious-test: 'is' form has first arg that is a constant whose value is logical true.  This will always pass.  There is probably a mistake in this test
    test/compojure/core_test.clj:117:21: suspicious-test: 'is' form has first arg that is a constant whose value is logical true.  This will always pass.  There is probably a mistake in this test
    test/compojure/core_test.clj:109:1: constant-test: Test expression is always logical true or always logical false: false
    test/compojure/core_test.clj:109:1: constant-test: Test expression is always logical true or always logical false: true
    test/compojure/core_test.clj:114:1: constant-test: Test expression is always logical true or always logical false: false
    test/compojure/core_test.clj:114:1: constant-test: Test expression is always logical true or always logical false: true
    == Warnings: 7 (not including reflection warnings)  Exceptions thrown: 0
    Subprocess failed

Adding `:out "warn.txt"` to the options map will cause all of the
Eastwood warning lines and 'Entering directory' lines, but no others,
to be written to the file `warn.txt`.  This file is useful for
stepping through warnings as described in the [Editor
Support](#editor-support) section.

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
* `:exclude-linters` Linters to exclude
* `:add-linters` Linters to add.  The final list of linters is the set
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

## Parallelism

As of version 0.3.4, you can now add parallelism to your linting.
Currently only a `:naive` form of parallelism is supported, which just uses
`pmap` to run the linters over your namespaces. You can specify
`:parallelism?` in your options map, currently `:none` and `:naive` are valid options.

## Only lint files modified since last run

As of version 0.3.5, you can now instruct eastwood to only lint the files
changed since the last run. This feature is pr 0.3.5 to be considered
alpha and subject to change.
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

If you know how to write tests using other Clojure test libraries
besides `clojure.test` so that merely loading the file does not run
the tests, please create an Issue for Eastwood on GitHub.

If you have a code base you do not trust to load, consider a sandbox,
throwaway virtual machine, etc.

New in Eastwood 0.1.5 is an option to force continuing to lint more
files even if an exception was thrown while analyzing or evaluating an
earlier file.  To do this, set the key `:continue-on-exception` to
`true` in the option map.  Without this option, the default behavior
is to stop linting if an exception is thrown.

Warning: Setting `:continue-on-exception` to true can cause exceptions
while analyzing later namespaces that would not otherwise occur.  For
example, if namespace `A` is analyzed first, and throws an exception
before function `A/foo` is defined, analyzing a later namespace `B`
that uses `A/foo` will throw an exception because it is undefined.

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

Starting with version 0.2.1, Eastwood `eval`s several config files in
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
   :symbol-matches #{#"^#'my\.old\.project\.*}})
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
additional files to read.  These are file names that can be anywhere
in your file system, specified as strings, or if Eastwood is invoked
from the REPL, anything that can be passed to
`clojure.java.io/reader`.


### Running Eastwood in a REPL

If you use Leiningen, merge this into your project's `project.clj`
file first:

```clojure
:profiles {:dev {:dependencies [[jonase/eastwood "0.3.5" :exclusions [org.clojure/clojure]]]}}
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
  warnings or errors encountered.  This result is likely more useful
  for further processing by editors and IDEs.  For example, file
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


### Editor Support

See the section for the development environment you use for specific
instructions:

* [Eclipse+Counterclockwise](#eclipse--counterclockwise)
* [Emacs+Cider](#emacs--cider)
* [Emacs](#emacs)
* [Vim](#vim)

Below are instructions to create a text file of warnings that can be
useful for at least the Emacs and Vim cases, but are not needed for
the others.

As of Eastwood version 0.2.0, the new default warning message format
is no longer a Clojure map, but lines of the form:

    <file>:<line>:<col>: <linter> <msg>

You can still get the older map format by setting the option
`:warning-format` to `:map-v2` (`:map-v1` is the style used in
Eastwood 0.1.4 and earlier).  The new default format can be specified
explicitly by setting `:warning-format` to `:location-list-v1`.

You can put only the warning lines into a file using the option `:out`
followed by a file name in a double-quoted string, or when running
Eastwood from the REPL, anything convertible to a writer via
`clojure.java.io/writer`.

Note: If you try to mix reflection warnings from the Clojure compiler
in such a file, those messages contain relative path names from a
directory on your classpath, whereas Eastwood warnings contain
relative path names from the current directory.  For example, if the
directory `src` is on your classpath, as it is by default in Leiningen
projects, then Eastwood warnings will contain file names like
`src/your/ns/core.clj`, whereas reflection warnings will not have the
`src/` at the beginning.  If someone knows a good way to mingle
Eastwood and Clojure messages in the same file, please open a GitHub
issue for Eastwood.


### Eclipse + Counterclockwise

As of Dec 2014 this [Eastwood
plugin](https://github.com/laurentpetit/ccw-plugin-eastwood) for
[Eclipse+Counterclockwise](https://github.com/laurentpetit/ccw) was
still in early development, but may be usable for you.


#### Emacs + Cider

If you use Emacs+Cider, take a look at the
[`squiggly-clojure`](https://github.com/clojure-emacs/squiggly-clojure)
project, which can run Eastwood and `core.typed` in the background on
your project using Emacs Flycheck, and displays the warnings as
annotations in your source code.


#### Emacs

(without Cider)

If you open a file with the warnings in their default format in Emacs,
then do the command `M-x compilation-mode`, you can use `next-error`
and `previous-error` commands to step through the warnings, and the
other buffer will jump to the specified file, line, and column.
Adding lines like the following to your Emacs init file
(`$HOME/.emacs.d/init.el` with recent versions of Emacs) is one way to
create convenient function key bindings for `next-error` and
`previous-error`.  Use `C-h f next-error RET` to see the current key
bindings for `next-error`, since you may not mind the defaults.

    (global-set-key [f9]  'previous-error)
    (global-set-key [f10] 'next-error)


#### Vim

A file containing default-format Eastwood warnings can be opened in
vim 'quickfix' mode with the command:

    vim -q filename

The warnings tend to be longer than one screen width.  You can use
`:copen 3` to increase the size of the window displaying locations to
3 lines, for example.  `:cn` jumps to the next warning, `:cp` to the
previous.  See the Vim documentation for more details,
e.g. [here](http://vimdoc.sourceforge.net/htmldoc/quickfix.html).

If you have [Syntastic](https://github.com/scrooloose/syntastic) and
[vim-fireplace](https://github.com/tpope/vim-fireplace/) installed,
you can use [vim-eastwood](https://github.com/venantius/vim-eastwood)
to automatically run Eastwood on the namespace in your current buffer.

### How the Eastwood options map is determined

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
{:user {:plugins [[jonase/eastwood "0.2.8"]]
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
   :warning-format :map-v2
   }
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

Starting with Eastwood version 0.2.1, it 'normal merges' the three
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


With Eastwood version 0.2.0 and most earlier versions, it was similar,
except the order of items 1 and 2 were swapped.  Since Leiningen
always has values for `:source-paths` and `:test-paths`, this meant
that the values for these keys in the Leiningen option map were always
ignored.

Eastwood version 0.2.0 and earlier also had special handling to
recognize the older `:source-path` key (note singular, not pluarl)
from Leiningen projects, and use it, but only if no `:source-paths`
keys were given in any Leiningen profiles, nor on the Eastwood command
line.  Similarly for the old `:test-path` key.


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


## Known issues


### Known libraries Eastwood has difficulty with

[`potemkin`](https://github.com/ztellman/potemkin) version 0.3.4 and
earlier, and libraries that depend on it now throw exceptions during
linting as of Eastwood 0.1.3 (and 0.1.2).  A suggested change to
`potemkin` was made in version 0.3.5 that should eliminate this issue.
The change in Eastwood behavior was due to a conscious design choice
in `tools.analyzer`.

With Eastwood 0.1.4 and earlier, the Clojure Contrib libraries
[`data.fressian`](https://github.com/clojure/data.fressian) and
[`test.generative`](https://github.com/clojure/test.generative) cannot
be analyzed due to a known bug in `tools.analyzer.jvm`:
[TANAL-24](http://dev.clojure.org/jira/browse/TANAL-24).  With
Eastwood 0.1.5, `tools.analyzer.jvm` has been enhanced to enable
Eastwood to issue warnings instead, which it does as part of the
`:wrong-tag` linter.


### Code analysis engine is more picky than the Clojure compiler

Eastwood uses
[`tools.analyzer`](https://github.com/clojure/tools.analyzer) and
[`tools.analyzer.jvm`](https://github.com/clojure/tools.analyzer.jvm)
to analyze Clojure source code.  It performs some sanity checks on the
source code that the Clojure compiler does not (at least as of Clojure
versions 1.5.1 and 1.6.0).


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


### Unreliable reflection warnings during linting

For additional testing of the `tools.analyzer.jvm` library, while
analyzing your source code it is read into an intermediate abstract
syntax tree (AST) data structure, and then forms are generated from
the AST and evaluated.  There are some issues with type hints being
different or missing from these forms, such that when Clojure is used
to eval the forms, the reflection warnings produced (if they are
enabled) will be different than what you get from compiling your code
normally.

This issue was worse in Eastwood 0.1.0, and has been improved in
versions 0.1.1 through 0.1.3.  There are likely to be a few
differences in reflection warnings from `lein eastwood` that remain,
so trust the `lein check` output if there are differences.


### Interaction between namespaces

TBD: This issue might no longer be true as of Eastwood 0.1.1 and
later.  Need to verify before editing this issue, though.

If more than one namespace is analyzed in a single command, settings
like (set! *warn-on-reflection* true) will be preserved from one
namespace to the next.  There are some projects with multiple
namespaces where similar (but different) effects can cause Eastwood to
throw exceptions.  Feel free to report such problems, but as a
workaround it may help to do multiple runs with a subset of the
namespaces, e.g. if only one namespace seems to be causing problems,
use these two commands to analyze one problem namespace separately:

    $ lein eastwood '{:namespaces [trouble.nspace]}'
    $ lein eastwood '{:exclude-namespaces [trouble.nspace]}'


## Notes on linter warnings

A lint warning is not always a sign of a problem with your program.
Your code may be doing exactly what it needs to do, but the lint tool
is not able to figure that out.  It often errs on the side of being
too noisy.


### Check consistency of namespace and file names

New in Eastwood version 0.1.1

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

(This behavior was verified as of Leiningen version 2.5.0)


### `:non-clojure-file`

#### Files that will not be linted because they are not Clojure source files

New in Eastwood version 0.2.0

This linter is disabled by default, because it warns even about
ClojureScript and Java source files it finds, and these are relatively
common in projects with Clojure/Java source files.  You must
explicitly enable it if you wish to see these warnings.

If you specify `:source-paths` or `:test-paths`, or use the default
Eastwood options from the command line that cause it to scan these
paths for Clojure source files, then with this linter enabled it will
warn about each file found that is not a Clojure/Java source file,
i.e. if its file name does not end with '.clj'.


### `:no-ns-form-found`

#### Warn about Clojure files where no `ns` form could be found

New in Eastwood version 0.2.0.  Updated in Eastwood version 0.2.3.

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

Before Eastwood 0.2.3, files named `data_readers.clj` that are in the
root directory of a source or test path directory and contained no
`ns` form would cause a warning when this linter was enabled.  Such
files should not contain an `ns` form.  See
[The Reader - Tagged Literals](http://clojure.org/reader#The%20Reader--Tagged%20Literals)
for the purpose and contents of these files.  Starting with Eastwood
0.2.3, no warning will be generated for these files (their contents
will still not be linted, as before Eastwood version 0.2.3).


### `:misplaced-docstrings`

#### Function or macro doc strings placed after the argument vector, instead of before the argument vector where they belong.

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


### `:deprecations`

#### Deprecated Clojure Vars, and deprecated Java instance methods, static fields, static methods, and constructors.

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

### `:implicit-dependencies`

#### Implicit dependencies

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



### `:redefd-vars`

#### Redefinitions of the same name in the same namespace.

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


### `:def-in-def`

#### `def` nested inside other `def`s

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

### `:wrong-arity`

#### Function calls that seem to have the wrong number of arguments.

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

Starting with Eastwood version 0.2.1, you can create a [config
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


### `:bad-arglists`

#### Function/macro `:arglists` metadata that does not match the number of args it is defined with

New in Eastwood version 0.1.1.  Significant bug fixes made in version
0.2.2.

Clearly this linter needs to be better documented.

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


### `:wrong-ns-form`

#### ns forms containing incorrect syntax or options

New in Eastwood version 0.2.1

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


### `:wrong-pre-post`

#### function has preconditions or postconditions that are likely incorrect

New in Eastwood version 0.2.2

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


### `:suspicious-test`

#### Tests using `clojure.test` that may be written incorrectly.

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


### `:suspicious-expression`

#### Suspicious expressions that appear incorrect, because they always return trivial values.

TBD.  Explain and give a few examples.

This linter was updated in Eastwood version 0.2.0 so that it always
examines forms after macroexpansion.  Eastwood 0.1.5 and earlier had a
mix of some cases caught by examining code after macroexpansion, but
some before.

The cases checked for before macroexpansion could produce false
positives, e.g. it could warn about the `(= 1)` part of `(-> 1 (=
1))`, even though that clearly expands to `(= 1 1)`, which should not
be warned about.

The good news is that with Eastwood version 0.2.0, those incorrect
warnings are gone.

The bad news is that with some libraries, there can be many incorrect
warnings from this linter, because it is macroexpanding before
checking.

For example, if you use the `core.match` library with Eastwood version
0.2.0, you may find warnings from this linter that have nothing
obvious to do with your code, about expressions of the form `(and x)`
with only one argument.  They are due to the way that macros in
`core.match` are written.  Starting with version 0.2.1, Eastwood's
built-in [config files](#eastwood-config-files) contain code that
should disable these warnings for `core.match` and macros in several
other libraries.  Search those config files for
`:suspicious-expression` to find them.


### `:constant-test`

#### A test expression always evaluates as true, or always false

New in Eastwood version 0.2.0

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

Like most Eastwood linters, these checks are performed after
macroexpansion, so at times the code that causes the warning may not
be in your source file.  Users of the `core.match` library may see
many such warnings with Eastwood version 0.2.0 that are not directly
in their code, but in the way `core.match` macros expand.

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

It is common across Clojure projects tested to use `:else` as the last
'always do this case' at the end of a `cond` form.  It is also fairly
common to use `true` or `:default` for this purpose, and Eastwood will
not warn about these.  If you use some other constant in that
position, Eastwood will warn.

It is somewhat common to use `(assert false "msg")` to throw
exceptions in Clojure code.  This linter has a special check never to
warn about such forms.

This linter does not yet examine tests in `if-some` or `when-some`
forms.  It is also not able to determine that expressions like `(/ 84
2)` are constant.


### `:unused-meta-on-macro`

#### Metadata on a macro invocation is ignored by Clojure

New in Eastwood version 0.2.0

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

The macro `schema.core/fn` in Prismatic's
[Schema](https://github.com/Prismatic/schema) library also has special
handling similar to `clojure.core/fn`, but at least as of Eastwood
0.2.0 and 0.2.1 there is no special handling of this case, so it will
warn if metadata annotates an invocation of this macro.


### `:unused-ret-vals`

#### Unused values, including unused return values of pure functions, and some others functions where it rarely makes sense to discard its return value.

The variant `:unused-ret-vals-in-try` is also documented here.

Values which are unused are sometimes a sign of a problem in your
code.  These can be constant values, values of locally bound symbols
like let symbols or function arguments, values of vars, or return
values of functions.

```clojure
(defn unused-val [a b]
  a b)   ; b is returned.  a's value is ignored
```

`comment` and `gen-class` expressions in Clojure (or `:gen-class`
inside of an `ns` form) always return nil.  Starting with Eastwood
0.1.4, it should no longer warn if this nil value is ignored.

Many of Clojure's core functions are 'pure' functions, meaning that
they do not modify any state of the system other than perhaps
allocating memory and heating up the CPU.  A pure function calculates
a value that depends only on the value of its arguments, and returns
it.

Calling such a function in a place where its return value is not used
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

In the future, Eastwood might be enhanced to avoid warning about such
cases for other HOFs besides `apply`, if it is clear that the
functions given as arguments have side effects, such as I/O.

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
of data about Clojure core functions.  It would be possible to enhance
Eastwood so that developers can add to this map, so their own
functions will cause similar warnings, but right now any function not
in this map will never cause one of these warnings.


### `:local-shadows-var`

#### A local name, e.g. a function arg, let binding, or record field name, has the same name as a global Var, and is called as a function

New in Eastwood version 0.1.5

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


### `:wrong-tag`

#### An incorrect type tag for which the Clojure compiler does not give an error

New in Eastwood version 0.1.5

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

Such type hints on function return values can also help avoid
reflection in Java interop calls, but in this case the places where it
can help are wherever the function is called, and its return value is
used in a Java interop call.  If that is in the same namespace where
the function is defined, then the same rules apply as for function
arguments.  Note: You should consider putting the type hint on the Var
name rather than on the argument vector if it is a Java class, as
shown above, since this avoids the problems described below.

If:

* the Java class type tag is on the argument vector, and
* the class name is not fully qualified, i.e. it is `LinkedList`
  rather than `java.util.LinkedList`, and
* the function is called in a different namespace where you have not
  imported the class, and
* the Java class is not imported by default by Clojure, i.e. it is
  outside the `java.lang` package,

then Clojure will give an error (see Clojure ticket
[CLJ-1232](http://dev.clojure.org/jira/browse/CLJ-1232)).  For this
reason, Eastwood will give a warning for function definitions that
have a type tag on the argument vector that is not in `java.lang`, and
is not fully qualified.

```clojure
;; Eastwood issues a warning for this
(defn linklist1 ^LinkedList [coll] (java.util.LinkedList. coll))

;; no warning for this because the tag is on the Var name, not the
;; argument vector, and the CLJ-1232 behavior does not apply
(defn ^LinkedList linklist2 [coll] (java.util.LinkedList. coll))

;; no warning for this since it is fully qualified
(defn linklist3 ^java.util.LinkedList [coll] (java.util.LinkedList. coll))

;; no warning for this because it is private
(defn ^:private linklist4 ^LinkedList [coll] (java.util.LinkedList. coll))

;; no warning for this because Class is in java.lang package
(defn cls ^Class [obj] (class obj))
```


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


### `:unused-fn-args`

#### Unused arguments of functions, macros, methods

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


### `:unused-locals`

#### Symbols bound with `let` or `loop` that are never used

New in Eastwood version 0.2.0

This linter is disabled by default, because it often produces a large
number of warnings, and even the ones that are correct can be
annoying, and usually just vestigial code that isn't really a bug (it
might hurt your performance).

Also, there are currently many warnings of this kind produced for
`core.async`, `core.match`, and Prismatic Schema code, and probably
also code that uses those libraries (judging from a small sample set
which in some case only includes test code for the library).  Perhaps
in the future Eastwood will be improved, but it is not there as of
version 0.2.1.

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


### `:unused-namespaces`

#### A namespace you use/require could be removed

Significant bug fixes made in version 0.2.5.

This linter is disabled by default, because it can be fairly noisy.
You must explicitly enable it if you wish to see these warnings.

Warn if a namespace is given in an `ns` form after `:use` or
`:require`, but the namespace is not actually used.  Thus the
namespace could be eliminated.

This linter is known to give false positives in a few cases.  See
these issues:

* Issue [#192](https://github.com/jonase/eastwood/issues/192)
* Issue [#210](https://github.com/jonase/eastwood/issues/210)


### `:unused-private-vars`

#### A Var declared to be private is not used in the namespace where it is def'd

Updated in Eastwood version 0.2.0

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


### `:unlimited-use`

#### Unlimited `(:use ...)` without `:refer` or `:only` to limit the symbols referred by it.

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

If you still want the refer-all-public-symbols effect of `use` without
a warning from this linter, it is recommended that you use `require`
with `:refer :all`, like so:

```clojure
(ns my.namespace
  (:require [clojure.string :refer :all]))
```

In addition, since it is so common (and in my opinion, harmless) to do
an unlimited use of namespace `clojure.test` in test files, this
linter never warns about `clojure.test`.


For an infrequently-changing namespace like `clojure.string`, the set
of symbols referred by this `use` is pretty stable across Clojure
versions, but even so, it only takes one symbol added to shadow an
existing symbol in your code to ruin your day.

TBD: Is it the default behavior of some/all Clojure versions to abort
in such a case?  Or perhaps it is some versions of Leiningen that
enable this option?  If a developer uses such an environment, a new
version of the namespace would get an explicit description of what had
changed and their code would not run, which is better than a subtle
bug in running code.


### `:keyword-typos`

#### Keywords that may have typographical errors

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


## Change log

See the
[changes.md](https://github.com/jonase/eastwood/blob/master/changes.md)
file.


## For Eastwood developers

To be on the bleeding edge (with all that phrase implies, e.g. you are
more likely to run across new undiscovered bugs), install Eastwood in
your local Maven repository:

    $ cd path/to/eastwood
    $ lein install

Then add `[jonase/eastwood "0.2.8"]` (or whatever is the
current version number in the defproject line of `project.clj`) to
your `:plugins` vector in your `:user` profile, perhaps in your
`$HOME/.lein/profiles.clj` file.


## License

Copyright (C) 2012-2015 Jonas Enlund, Nicola Mometto, and Andy Fingerhut

Distributed under the Eclipse Public License, the same as Clojure.

The source code of the following libraries has been copied into
Eastwood's source code, and each of their copyright and license info
is given below.  They are all distributed under the Eclipse Public
License 1.0.

### core.cache

[core.cache](https://github.com/clojure/core.cache)

Copyright (c) Rich Hickey, Michael Fogus and contributors, 2012. All rights reserved.  The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution. By using this software in any fashion, you are agreeing to be bound by the terms of this license.  You must not remove this notice, or any other, from this software.

### core.contracts

[core.contracts](https://github.com/clojure/core.contracts)

Copyright (c) Rich Hickey, Michael Fogus and contributors, 2012. All rights reserved.  The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution. By using this software in any fashion, you are agreeing to be bound by the terms of this license.  You must not remove this notice, or any other, from this software.

### core.memoize

[core.memoize](https://github.com/clojure/core.memoize)

Copyright (c) Rich Hickey and Michael Fogus, 2012, 2013. All rights reserved.  The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution. By using this software in any fashion, you are agreeing to be bound by the terms of this license.  You must not remove this notice, or any other, from this software.

### core.unify

[core.unify](https://github.com/clojure/core.unify)

Copyright © 2011 Rich Hickey

Licensed under the EPL. (See the file epl.html.)

### data.priority-map

Copyright (C) 2013 Mark Engelberg

Distributed under the Eclipse Public License, the same as Clojure.

[data.priority-map](https://github.com/clojure/data.priority-map)

### leinjacker

[leinjacker](https://github.com/sattvik/leinjacker)

Copyright © 2012 Sattvik Software & Technology Resources, Ltd. Co.
All rights reserved.

Distributed under the Eclipse Public License, the same as Clojure.

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
