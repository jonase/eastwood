# eastwood - a Clojure lint tool

Eastwood is a Clojure lint tool which uses the
[tools.analyzer](https://github.com/clojure/tools.analyzer) and
[tools.analyzer.jvm](https://github.com/clojure/tools.analyzer.jvm)
libraries to inspect namespaces and report possible problems.  It has
been tested with Clojure 1.4.0 and 1.5.1.  It does not work with
Clojure versions earlier than 1.4.0.


## What's there?

Eastwood warns when it finds:

- deprecated java instance methods, static fields, static methods and
  constructors
- deprecated clojure vars
- def's nested inside other def's
- redefinitions of the same name in the same namespace
- unused function arguments
- unused private vars
- unused return values of pure functions, or some others where it
  rarely makes sense to discard its return value
- reflection
- naked (:use ...)
- misplaced docstrings
- keyword typos

Because Eastwood evaluates the code it is linting, you must use a
version of Clojure that is capable of loading your code, and it can
only finish if Clojure can compile your code without throwing any
exceptions.  Using Eastwood will cause any side effects to occur that
loading your code normally does -- Eastwood is no more and _no less_
dangerous than loading your code normally.  If you have a code base
you do not trust to load, consider a sandbox, throwaway virtual
machine, etc.


## Installation

Eastwood is a Leiningen plugin.  Add `[jonase/eastwood "0.0.3"]` to
your `:plugins` vector in your `:user` profile (Leiningen 2) or if you
are using Leiningen 1:

    $ lein plugin install jonase/eastwood 0.0.3

TBD: Does eastwood really still work with Leiningen 1?  Do we care?


## Usage

Running

    $ lein eastwood

in the root of your project will lint your projects namespaces.  You
can also lint your project's dependencies:

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

* `:naked-use`
* `:misplaced-docstrings`
* `:def-in-def`
* `:redefd-vars`
* `:reflection`
* `:deprecations`
* `:unused-fn-args`
* `:unused-private-vars`
* `:unused-ret-vals`
* `:unused-namespaces`
* `:keyword-typos`


Available options are:

* `:namespaces` Namespaces to lint
* `:exclude-namespaces` Namespaces to exclude
* `:linters` Linters to use
* `:exclude-linters` Linters to exclude

Note that you can add e.g., `{:eastwood {:exclude-linters
[:keyword-typos]}}` to `.lein/profiles.clj` to disable linters you
don't like.


## Known issues

### Interaction between namespaces

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

### Explicit use of Clojure environment `&env`

Code that uses the **values of `&env`** feature of the Clojure compiler
will cause errors when being analyzed. Some known examples are the libraries
libraries
[`immutable-bitset`](https://github.com/ztellman/immutable-bitset) and
[`flatland/useful`](https://github.com/flatland/useful).

Note that if a library uses simply `(keys &env)` it will be analyzed with
no problems, however because the values of &env are `Compiler$LocalBinding`s,
there's no way for `tools.analyzer.jvm` to provide a compatible `&env`

### Namespaces collision

The Clojure Contrib libraries `core.typed` and `jvm.tools.analyzer` cannot
be analyzed because both `tools.analyzer` and `jvm.tools.analyzer` share the
same `clojure.tools.analyzer` namespace.

### Other Issues

Currently, the Clojure Contrib liraries `data.fressian` and `test.generative`
cannot be analyzed due to a known bug in `tools.analyer.jvm`: [TANAL-24](http://dev.clojure.org/jira/browse/TANAL-24)


## Notes on warnings

A lint warning is not always a sign of a problem with your program.
Your code may be doing exactly what it needs to do, but the lint tool
is not able to figure that out.  It often errs on the side of being
too noisy.


### `:redefd-vars` - Redefinitions of the same name in the same namespace

It is possible to accidentally define the same var multiple times in
the same namespace.  Eastwood's :redefd-vars linter will warn about
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
for the later definition to replace the first.  In fact, reloading the
same namespace after editing its source code is a common method for
developing Clojure code, and warnings when reloading a modified source
file would be very annoying in such cases.

If you use `clojure.test` to develop tests for your code, note that
`deftest` statements create vars with the same name as you give to the
test, and if you accidentally create two tests with the same name, the
tests in the first one will never be run, and you will lose test
coverage.  There will be nothing in the source code to indicate this
other than the common name.  Here is an example where the first
`deftest` contains tests that clearly should fail, but since they are
not run, all of your tests could still pass.

```clojure
(deftest test-feature-a
  ;; This test should cause test runs to fail, but IT DOES NOT.
  (is (= 0 1)))

;; lots of other tests here

(deftest test-feature-a   ; perhaps written months after the earlier tests
  (is (= 5 (+ 2 3))))
```

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
Clojure code with def statements inside of functions.  Or you might be
unfamiliar with functional programming style, and try writing code in
imperative style using def like this:

```clojure
(defn count-up-to [n]
  (def i 1)
  (while (<= i n)
    (println i)
    (def i (+ i 1))))
```

This is bad form in Clojure.  It is written in imperative style, which
is not encouraged, but that is not the worst thing about this example.
The worst part is the use of def inside of another def (the defn
count-up-to).  Defs always have an effect on a globally visible var in
the namespace, whether they are nested inside another def or not.

Unless you really know what you are doing and looking for a very
particular effect, it is highly recommended to take `:def-in-def`
warnings as a sign to change your code.


### `:unused-fn-args` - Unused arguments of functions, macros, methods

Writing a function that does not use some of its arguments is not
necessarily a mistake.  In particular, it is fairly common for
multimethods defined with `defmulti` to have a dispatch function that
only uses some of its arguments.

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


### `:misplaced-docstrings` - Strings that appear to be misplaced documentation strings

The correct place to put a documentation string for a function is just
before the arguments, like so:

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


### `:unused-ret-vals` - Function return values that are not used

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
  [k v])
```

There are many Clojure functions that are not pure functions, but for
which it is probably a mistake to discard its return value.  For
example, `assoc!`, `rand`, and `read`.  Eastwood warns about these,
too.

There are many Clojure functions that take other functions as
arguments.  These are often called higher order functions, or HOFs.
Some of these HOFs are 'conditionally pure', meaning that if the
functions passed as arguments are pure, then so is the HOF.  For
example, `map` and `apply` are conditionally pure HOFs.

Eastwood warns about discarding the return value of a conditionally
pure HOF.  It is not sophisticated enough to check whether the
function arguments to the HOF are non-pure, e.g. it will warn about a
case like `(apply printf args)` if the return value is discarded, even
though a person can easily see that discarding the return value is OK.
In the future, Eastwood might be enhanced to avoid warning about such
cases, if it is clear that the functions given as arguments have side
effects, such as I/O.

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


## License

Copyright (C) 2012-2013 Jonas Enlund

Distributed under the Eclipse Public License, the same as Clojure.
