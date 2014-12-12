(ns testcases.unusednss
  (:use testcases.unusednss2)
  (:require [clojure [string :as s]]
            [clojure [repl :as r]]
            [clojure.data :as d]))

;; clojure.repl should be considered used because of the macro
;; invocation r/doc in foo1.

(defn foo1 [x]
  (r/doc x))


;; TBD: clojure.data should be considered used because of the function
;; call d/diff in macro foo2.  However, as of right now Eastwood does
;; not search through syntax-quoted forms for uses of namespaced vars.

(defmacro foo2 [x y]
  `(d/diff ~x ~y))


;; testcases.unusednss2 should be considered used because of the use
;; of atom1 in foo3.

(defn foo3 [n]
  (swap! atom1 conj n))


;; Should be no warning for this require, because it is outside of ns
;; form.

(defn foo4 [ns]
  (require ns))
