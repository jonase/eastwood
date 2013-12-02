# eastwood - a Clojure lint tool

Eastwood is a clojure lint tool which uses the
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
- reflection
- naked (:use ...)
- misplaced docstrings
- keyword typos


## Installation

Eastwood is a leiningen plugin.  Add `[jonase/eastwood "0.0.3"]` to
your `:plugins` vector in your `:user` profile (Leiningen 2) or if you
are using Leiningen 1:

    $ lein plugin install jonase/eastwood 0.0.2

TBD: Does eastwood really still work with Leininge 1?  Do we care?


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

Code that uses the `&env` feature of the Clojure compiler may cause
errors when being analyzed.  Some known examples are Clojure contrib
libraries `core.async`, `core.logic`, and `core.typed`, and the
library
[`immutable-bitset`](https://github.com/ztellman/immutable-bitset).
It is still to be determined how much effort it might be to support
such code.


## License

Copyright (C) 2012-2013 Jonas Enlund

Distributed under the Eclipse Public License, the same as Clojure.
