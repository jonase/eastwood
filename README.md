# eastwood - a Clojure lint tool

Eastwood is a Clojure
[lint](http://en.wikipedia.org/wiki/Lint_%28software%29) tool that
uses the [tools.analyzer](https://github.com/clojure/tools.analyzer)
and
[tools.analyzer.jvm](https://github.com/clojure/tools.analyzer.jvm)
libraries to inspect namespaces and report possible problems.  It has
been tested with Clojure 1.5.1 and 1.6.0.  It has been very lightly
tested with Clojure 1.4.0, but it definitely does not work with
Clojure versions earlier than 1.4.0.


## Installation & Quick usage

Eastwood is a [Leiningen](http://leiningen.org) plugin, tested with
Leiningen 2.3.x.  Merge the following into your `~/.lein/profiles.clj`
file:

```clojure
{:user {:plugins [[jonase/eastwood "0.1.2"]] }}
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

    $ lein eastwood '{:namespaces [:source-paths]}'

See the "Usage" section below for more notes on side effects in test
code.

Eastwood can only finish linting a file if Clojure itself can compile
it.  It is recommended to use a command like `lein check` to check for
compiler errors before running Eastwood.  Even better, `lein test`
will compile files in your source paths and test paths, not merely
your source paths as `lein check` does.

See section "For Eastwood developers" below for instructions on trying
out the latest unreleased version of Eastwood.


## What's there?

Eastwood warns when it finds:

- inconsistencies between file names and the namespaces declared
  within them (new in version 0.1.1)
- misplaced docstrings
- deprecated Java instance methods, static fields, static methods and
  constructors
- deprecated Clojure vars
- redefinitions of the same name in the same namespace
- def's nested inside other def's
- function calls that seem to have the wrong number of arguments
- function/macro `:arglists` metadata that does not match the number
  of args it is defined with (new in version 0.1.1)
- tests using `clojure.test` that may be written incorrectly
- suspicious expressions that appear incorrect, because they always
  return trivial values
- unused values, including unused return values of pure functions, and
  some others functions where it rarely makes sense to discard its
  return value
- unused private vars
- unused function arguments
- unused namespaces
- unlimited (:use ...) without :refer or :only to limit the symbols
  referred by it
- keyword typos


## Usage

Running

    $ lein eastwood

in the root of your project will lint your project's namespaces -- all
of those in your `:source-paths` and `:test-paths` directories and
their subdirectories.  You can also lint your project's dependencies:

    $ lein eastwood '{:namespaces [clojure.set clojure.java.io] :exclude-linters [:unused-fn-args]}'
    == Linting clojure.set ==
    {:linter :misplaced-docstrings,
     :msg "Possibly misplaced docstring, #'clojure.set/bubble-max-key",
     :line 13}

    == Linting clojure.java.io ==
    {:linter :deprecations,
     :msg
     "Instance method 'public java.net.URL java.io.File.toURL() throws java.net.MalformedURLException' is deprecated.",
     :line 50}

Available linters are:

* `:misplaced-docstrings`
* `:deprecations`
* `:redefd-vars`
* `:def-in-def`
* `:wrong-arity`
* `:bad-arglists` (new in version 0.1.1)
* `:suspicious-test`
* `:suspicious-expression`
* `:unused-ret-vals`
* `:unused-ret-vals-in-try`
* `:unused-private-vars` (needs updating)
* `:unused-fn-args` (disabled by default)
* `:unused-namespaces` (disabled by default)
* `:unlimited-use`
* `:keyword-typos` (disabled by default)

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

Available options for specifying linters are:

* `:linters` Linters to use.  If not specified, all linters except
  those mentioned as 'disabled by default' above are used.
* `:exclude-linters` Linters to exclude
* `:add-linters` Linters to add.  The final list of linters is the set
  specified by `:linters`, taking away all in `:excluded-linters`,
  then adding all in `:add-linters`.

Note that you can add e.g., `{:eastwood {:exclude-linters
[:unlimited-use]}}` to `.lein/profiles.clj` to disable linters you
don't like, or add the key `:eastwood` with options to your project's
`project.clj` file.

As mentioned in the "Installation & Quick usage" section above, using
Eastwood causes any and all side effects that loading the file would
cause (e.g. by doing `use` or `require` on the file's namespace).
Eastwood is able to find potential problems in test code, too.  If you
wish to use Eastwood on test files without such side effects, consider
modifying your tests so that merely performing `require`/`use` on the
files does not cause the side effects.  If you can arrange things so
that running your tests requires loading the files and then calling
some function(s) (e.g. as tests written using
[`clojure.test/deftest`](http://clojure.github.io/clojure/#clojure.test)
do), then you can run Eastwood on those files without the side
effects.

If you know how to write tests using other Clojure test libraries
besides `clojure.test` so that merely loading the file does not run
the tests, please create an Issue for Eastwood on Github.

If you have a code base you do not trust to load, consider a sandbox,
throwaway virtual machine, etc.

There are also options that enable printing of additional debug
messages during linting.  These are only intended for tracking down
the cause of errors in Eastwood.  You specify the key `:debug` with a
value that is a set of keywords, e.g.

    lein eastwood '{:exclude-linters [:wrong-arity] :debug #{:eval :ns}}'

* `:all` - enable all debug messages.  This also enables showing the
  list of namespaces near the beginning of the output, before linting
  begins.
* `:time` - print messages about the elapsed time taken during
  analysis, and for each individual linter.
* `:forms` - print the forms as read, before they are analyzed
* `:forms-pprint` - like `:forms` except pretty-print the forms
* `:progress` - show a brief debug message after each top-level form
  is read
* `:eval` - prett-print each form after it has been read, analyzed
  into an AST (abstract syntax tree), and converted back into form
  from the AST, but before that form is evaluated with `eval`.
* `:ns` - print the initial set of namespaces loaded into the Clojure
  run-time at the beginning of each file being linted, and then after
  each top level form print any changes to that list of loaded
  namespaces (typically as the result of evaluating a `require` or
  `use` form).


## Known issues


### Known libraries Eastwood has difficulty with

[`potemkin`](https://github.com/ztellman/potemkin) and libraries that
depend on it (e.g. [`ogre`](https://github.com/clojurewerkz/ogre)) now
throw exceptions during linting as of Eastwood 0.1.2.  A suggested
change to `potemkin` has been submitted that would eliminate this, but
the change in behavior was due to a conscious design choice in
`tools.analyzer`.

Currently, the Clojure Contrib libraries
[`data.fressian`](https://github.com/clojure/data.fressian) and
[`test.generative`](https://github.com/clojure/test.generative) cannot
be analyzed due to a known bug in `tools.analyer.jvm`:
[TANAL-24](http://dev.clojure.org/jira/browse/TANAL-24)

Other libraries known to cause problems for Eastwood because of
`test.generative`: [Cheshire](https://github.com/dakrone/cheshire)
(TBD whether this is truly due to `test.generative`, or something
else).


### Warning messages near beginning of Eastwood output

With Eastwood version 0.1.2 and 0.1.1, it is unfortunately perfectly
normal to see these warning messages near the beginning of the output:

    Reflection warning, clojure/data/priority_map.clj:215:19 - call to equiv can't be resolved.
    Reflection warning, clojure/core/memoize.clj:72:23 - reference to field cache can't be resolved.

Eastwood will still work correctly despite these warnings.

Recommendation: Ignore the warnings.  If you want them to be
eliminated in a future version of Eastwood, consider creating a
Clojure JIRA account and voting for the tickets below to be fixed.

[Link](http://dev.clojure.org/jira/secure/Signup!default.jspa) where
you can create a Clojure JIRA account.

Tickets to vote for:
* [CCACHE-34](http://dev.clojure.org/jira/browse/CCACHE-34)
* [CMEMOIZE-13](http://dev.clojure.org/jira/browse/CMEMOIZE-13)

Why this happens: `tools.analyzer.jvm` uses the library
`core.memoize`, which uses `core.cache`, which in turn uses an older
version of `data.priority-map`, which has this reflection warning in
it.


If you use Eastwood version 0.1.0 and Clojure 1.6.0, it is normal to
see these messages near the beginning of the Eastwood output:

    WARNING: record? already refers to: #'clojure.core/record? in namespace: clojure.tools.analyzer.utils, being replaced by: #'clojure.tools.analyzer.utils/record?
    WARNING: record? already refers to: #'clojure.core/record? in namespace: clojure.tools.analyzer, being replaced by: #'clojure.tools.analyzer.utils/record?

The presence of these warnings does not otherwise impair Eastwood's
ability to find problems in your code.

Recommendation: Upgrade to Eastwood version 0.1.2, but see above for
other warning messages you will see instead.

Why this happens: Eastwood 0.1.0 uses an older version of the
`tools.analyzer` library that defines a function `record?` that has
the same name as a new function introduced in Clojure 1.6.0.


### Code analysis engine is more picky than the Clojure compiler

Eastwood uses
[`tools.analyzer`](https://github.com/clojure/tools.analyzer) and
[`tools.analyzer.jvm`](https://github.com/clojure/tools.analyzer.jvm)
to analyze Clojure source code.  It performs some sanity checks on the
source code that the Clojure compiler does not (at least as of Clojure
versions 1.5.1 and 1.6.0).

For example, Eastwood will throw an exception when analyzing code with
a type hint `^Typename` where the type name is a Java class that has
not been imported by default by the Clojure compiler, nor by an
`:import` inside of an `ns` form.  In most cases, an explanatory
message should be given by Eastwood explaining the problem's cause,
and what you can do to change your code so that Eastwood can analyze
it.


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

    Exception thrown during phase :analyze of linting namespace immutable-bitset
    ClassCastException clojure.lang.PersistentArrayMap cannot be cast to clojure.lang.Compiler$LocalBinding


### Namespaces collision

The Clojure Contrib libraries
[`core.typed`](https://github.com/clojure/core.typed) and
[`jvm.tools.analyzer`](https://github.com/clojure/jvm.tools.analyzer),
and projects that depend upon them, cannot be analyzed.  `core.typed`
use `jvm.tools.analyzer`, and both it and `tools.analyzer` (used by
Eastwood) share the same `clojure.tools.analyzer` namespace.

Updating to the latest version of these libraries (e.g. `core.typed`
version 0.2.21 or later) should solve this for you, although that may
cause conflicts with other code.  See [this
message](https://groups.google.com/d/msg/clojure-dev/N4Ld3dc17_o/e1Ww2i7YbroJ)
for an alternate approach.

If a project uses
[`tools.namespace`](https://github.com/clojure/tools.namespace)
versions older than 0.2.0, there will be a similar conflict that
causes an exception to be thrown.

TBD: `jvm.tools.analyzer` may have had its namespace
`clojure.tools.analyzer` renamed specificaly to avoid this problem.

Linting libraries which Eastwood uses in its implementation often
causes strange warnings or exceptions.  These include
`tools.analyzer`, `tools.analyzer.jvm`, `core.memoize`, and
`core.cache`.


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
versions 0.1.1 and 0.1.2.  There are likely to be a few differences in
reflection warnings from `lein eastwood` that remain, so trust the
`lein check` output if there are differences.


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


### `:misplaced-docstrings` - Strings that appear to be misplaced documentation strings

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


### `:deprecations` - Deprecated Java methods/fields/constructors and Clojure vars

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


### `:redefd-vars` - Redefinitions of the same name in the same namespace

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


### `:def-in-def` - Defs nested inside other defs

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


### `:wrong-arity` - Function call with wrong number of arguments

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

A good potential future enhancement to this linter would be to allow a
developer to specify a list of functions that should never have
`:wrong-arity` warnings generated for calls to the function, or to use
`:arglists` specified in a different place so the warnings are
accurate.


### `:bad-arglists` - Function/macro definitions with arg vectors differing from their `:arglists` metadata

New in Eastwood version 0.1.1

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


### `:suspicious-test` - Suspicious tests that may be written incorrectly

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


### `:suspicious-expression` - Suspicious expressions that may be incorrect

TBD.  Explain and give a few examples.


### `:unused-ret-vals` and `:unused-ret-vals-in-try` - values that are not used

Values which are unused are sometimes a sign of a problem in your
code.  These can be constant values, values of locally bound symbols
like let symbols or function arguments, values of vars, or return
values of functions.

```clojure
(defn unused-val [a b]
  a b)   ; b is returned.  a's value is ignored
```

`comment` expressions in Clojure always return nil, and Eastwood will
warn if this nil value is ignored.  This may be improved in the
future.

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


### `:unused-fn-args` - Unused arguments of functions, macros, methods

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


### `:unlimited-use` - Use statements that do not explicitly limit the symbols they refer

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


### `:keyword-typos` - Keywords that may have typographical errors

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
more likely to run across new undiscovered bugs), install the latest
versions of the tools.analyzer(.jvm) libraries, and Eastwood, in your
local Maven repository:

    $ cd path/to/tools.analyzer
    $ lein install
    $ cd path/to/tools.analyzer.jvm
    $ lein install
    $ cd path/to/eastwood
    $ LEIN_SNAPSHOTS_IN_RELEASE=1 lein install

Then add `[jonase/eastwood "0.1.3-SNAPSHOT"]` (or whatever is the
current version number in the defproject line of `project.clj`) to
your `:plugins` vector in your `:user` profile, perhaps in your
`~/.lein/profiles.clj` file.


## License

Copyright (C) 2012-2014 Jonas Enlund

Distributed under the Eclipse Public License, the same as Clojure.

The source code of the following libraries has been copied into
Eastwood's source code, and each of their copyright and license info
is given below.  They are all distributed under the Eclipse Public
License 1.0.

### core.cache

[core.cache](https://github.com/clojure/core.cache)

Copyright (c) Rich Hickey, Michael Fogus and contributors, 2012. All rights reserved.  The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution. By using this software in any fashion, you are agreeing to be bound bythe terms of this license.  You must not remove this notice, or any other, from this software.

### core.contracts

[core.contracts](https://github.com/clojure/core.contracts)

Copyright (c) Rich Hickey, Michael Fogus and contributors, 2012. All rights reserved.  The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution. By using this software in any fashion, you are agreeing to be bound bythe terms of this license.  You must not remove this notice, or any other, from this software.

### core.memoize

[core.memoize](https://github.com/clojure/core.memoize)

Copyright (c) Rich Hickey and Michael Fogus, 2012, 2013. All rights reserved.  The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution. By using this software in any fashion, you are agreeing to be bound bythe terms of this license.  You must not remove this notice, or any other, from this software.

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
