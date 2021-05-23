(ns testcases.unknown-reify
  "https://github.com/jonase/eastwood/issues/205")

(def foo (reify Unknown (foo [this])))
