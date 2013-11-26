# eastwood - a Clojure lint tool

Eastwood is a clojure lint tool which uses the
[tools.analyzer](https://github.com/clojure/tools.analyzer) and
[tools.analyzer.jvm](https://github.com/clojure/tools.analyzer.jvm)
libraries to inspect namespaces and report possible problems.  It has
been tested with Clojure 1.5.1, but it may work with older Clojure
versions, too.


## What's there?

Eastwood warns when it finds 

- deprecated java instance methods, static fields, static methods and
  constructors
- deprecated clojure vars
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


## Usage

Running

    $ lein eastwood

in the root of your project will lint your projects namespaces.  You
can also lint your projects dependencies:

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
* `:redefd-vars`
* `:def-in-def`
* `:reflection`
* `:deprecations`
* `:unused-fn-args`
* `:unused-private-vars`
* `:keyword-typos`


Available options are:

* `:namespaces` Namespaces to lint
* `:exclude-namespaces` Namespaces to exclude
* `:linters` Linters to use
* `:exclude-linters` Linters to exclude

Note that you can add e.g., `{:eastwood {:exclude-linters
[:keyword-typos]}}` to `.lein/profiles.clj` to disable linters you
don't like.


## Bugs

If more than one namespace is analyzed in a single command, settings
like (set! *warn-on-reflection* true) will be preserved from one
namespace to the next.

Protocols (defined with defprotocol) give a redef'd warning.  They
should not, but I have not yet determined what condition can be used
to show that the var has not been redefined.

Several namespaces in contrib libraries give NullPointerException when
trying to analyze them.


## License

Copyright (C) 2012 Jonas Enlund

Distributed under the Eclipse Public License, the same as Clojure.
