# eastwood - a Clojure lint tool

Eastwood is a clojure lint tool which uses the
[analyze](https://github.com/frenchy64/analyze) library to inspect
namespaces and report possible problems. Currently it should works
with projects running Clojure 1.3.0 and newer.

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

Eastwood is a leiningen plugin. Add `[jonase/eastwood "0.0.1"]` to
your `:plugins` vector in your `:user` profile (Leiningen 2) or if you
are using Leiningen 1:

    $ lein plugin install jonase/eastwood 0.0.1

## Usage

Running

    $ lein eastwood

in the root of your project will lint your projects namespaces. You
can also lint your projects dependencies:

    $ lein eastwood '{:namespaces [clojure.set clojure.java.io] :exclude-linters [:unused-fn-args]}'
    == Linting clojure.set ==
    {:linter :misplaced-docstring,
     :msg "Possibly misplaced docstring, #'clojure.set/bubble-max-key",
     :line 13}

    == Linting clojure.java.io ==
    {:linter :deprecated,
     :msg
     "Instance method 'public java.net.URL java.io.File.toURL() throws java.net.MalformedURLException' is deprecated.",
     :line 50}

Available options are:

* `:namespaces` Namespaces to lint
* `:exclude-namespaces` Namespaces to exclude
* `:linters` Linters to use
* `:exclude-linters` Linters to exclude

Note that you can add e.g., `{:eastwood {:exclude-linters
[:keyword-typos]}}` to `.lein/profiles.clj` to disable linters you
don't like.

## License

Copyright (C) 2012 Jonas Enlund

Distributed under the Eclipse Public License, the same as Clojure.
