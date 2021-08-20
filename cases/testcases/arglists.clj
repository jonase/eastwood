(ns testcases.arglists
  (:require [clojure.test :refer :all]))


(defn fn-no-arglists1
  [name]
  :fn-no-arglists1)

(defn fn-with-arglists1
  {:arglists '([name expr])}
  [name]
  :fn-with-arglists1)

(defn fn-no-arglists2
  [name & sigs]
  :fn-no-arglists2)

(defn fn-no-arglists3
  ([a1] :fn-no-arglists3-1)
  ([a1 a2 a3] :fn-no-arglists3-2))

(defn fn-with-arglists3
  {:arglists '([x y] [x y z w])}
  ([a1] :fn-no-arglists3-1)
  ([a1 a2 a3] :fn-no-arglists3-2))

(defn fn-no-arglists4
  [name & sigs]
  :fn-no-arglists1)

(defn fn-with-arglists4
  {:arglists '([name expr])}
  [name & sigs]
  :fn-with-arglists1)

(defmacro macro-no-arglists1
  [name & sigs]
  (let [[name [expr]] (cons name sigs)]
    `(def ~name ~expr)))

(defmacro macro-with-arglists1
  {:arglists '([name expr])}
  [name & sigs]
  (let [[name [expr]] (cons name sigs)]
    `(def ~name ~expr)))

;; This is adapted from a macro named with-replies* in the
;; carmine library. It has given warnings at one time with Eastwood,
;; but I think it should not.

(defmacro macro-with-arglists-no-warn1
  {:arglists '([:as-pipeline & body] [& body])}
  [& [s1 & sn :as sigs]]
  (let [as-pipeline? (identical? s1 :as-pipeline)
        body         (if as-pipeline? sn sigs)]
    `(do ~@body (do :get-replies ~as-pipeline?))))

;; Adapted from a macro named fnm in the core.logic library. I
;; think it should not give any warnings, because the developer-supplied
;; :arglists is more restrictive than the actual arg vector(s) allow.

(defmacro macro-with-arglists-no-warn2
  {:arglists '([t as tabled? & cs])}
  [t as & cs]
  :macro-with-arglists-no-warn2)
