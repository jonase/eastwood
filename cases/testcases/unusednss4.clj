(ns testcases.unusednss4
  (:use testcases.unusednss2)
  (:require [clojure [string :as s]]
            [clojure [repl :as r]]
            [clojure.data :as d]))



;; This file should only reference the use'd and require'd namespaces
;; in the ns form above via their occurrence in keywords, either as an
;; alias, or as the full namespace name.  Eastwood does not
;; distinguish which of those 2 methods is used.


;; Does _not_ reference namespace testcases.unusednss2, because this
;; keyword has no namespace, only the keyword name.
(def x :testcases.unusednss2)  

;; alias for clojure.string
(def y ::s/foo)

;; Keyword with namespace clojure.repl inside syntax-quote
(defmacro foo2 [x y]
  `(let [x2# ~x y2# ~y z# :clojure.repl/bar]
     (list x2# y2# z#)))
