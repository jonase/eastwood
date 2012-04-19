# eastwood - a Clojure lint tool

**Note:**  This tool is not yet usable. It's a work in progress.

Eastwood is a clojure lint tool which uses the
[analyze](https://github.com/frenchy64/analyze) library to inspect
namespaces and report anomalies. Currently it works with projects
running Clojure 1.3.0 and newer.

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

## How to use?

In the REPL: 

    > (use 'eastwood.core)
    > (lint-ns 'my.namespace)

If you want to exclude some linters

    (lint-ns 'my.namespace 
             :exclude [:reflection :unused-private-vars])

If you want to run one (or a few) linters:

    (lint-ns 'my.namespace :only [:misplaced-docstrings])

Available linters include

    :deprecations 
    :reflection 
    :misplaced-docstrings 
    :unused-locals 
    :unused-private-vars 
    :naked-use

## TODO

- Report line numbers.
- More linters.
- Lein plugin (will require Leiningen 2.0).

## License

Copyright (C) 2012 Jonas Enlund

Distributed under the Eclipse Public License, the same as Clojure.
