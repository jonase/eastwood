(ns eastwood.linters
  (:require [eastwood.linters.misc
             eastwood.linters.reflection
             eastwood.linters.deprecated
             eastwood.linters.unused
             eastwood.linters.typos]))

(def naked-use eastwood.linters.misc/naked-use)
(def misplaced-docstrings eastwood.linters.misc/misplaced-docstrings)
(def def-in-def eastwood.linters.misc/def-in-def)
(def redefd-vars eastwood.linters.misc/redefd-vars)
(def reflection eastwood.linters.reflection/reflection)
(def deprecations eastwood.linters.deprecated/deprecations)
(def unused-fn-args eastwood.linters.unused/unused-fn-args)
(def unused-private-vars eastwood.linters.unused/unused-private-vars)
(def unused-namspaces eastwood.linters.unused/unused-namespaces)
(def unused-ret-vals eastwood.linters.unused/unused-ret-vals)
(def keyword-typos eastwood.linters.typos/keyword-typos)