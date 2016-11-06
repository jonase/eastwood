(ns testcases.rewrite-clj
  (:require [clojure.string :as string]))


;; Code for testing rewrite-clj library


(defn quoted-symbol []
  'quoted-symbol)


(defn quoted-expr []
  '(1 2 3))


(defn quote-within-quote []
  '(1 2 ' (3 4 5)))


(defn quoted-vector []
  '
  ;; comment between quote and quoted expression.  Unusual, but legal.
  [1 2 3])


(defn hasheq-imap [m]
  (resolve 'clojure.core/hash-unordered-coll))


(defmacro foo6 [x]
  (quote (inc x)))

(defn standalone-checks [counter]
  @counter)

(defn deref-with-expr [x]
  (let [a (atom x)
        m {:a a, :b 2}
        a2 (atom a)
        m2 {:a2 a2}]
    [@(m :a) @@(m2 :a2)  @
     @   (m2 :a2)   ]))


(defn fn-expr-1 [avl-map]
  #(key (nth avl-map %)))


(defn fn-expr-2 []
  #(%2 %1))


(defn fn-expr-3 []
  #(first %&))


(defn hash-seq [s]
  (.hashCode ^Object (first s)))


(defn metadata2 []
  ^{:a 1 :b 2} [1 2 3])


(defn metadata3 []
  #^{:a 1 :b 2} [1 2 3])


(defn var-quoted-symbol [s]
  #'inc)


(defn multi-line-string-may-be-special-in-rewrite-clj
  "first line
second line
third line"
  []
  (+ 2 3))


(defn regex-compare-to-themselves-as-not-=-in-clojure []
  #"b?.*$")


(defn namespaced-keyword []
  [
   ;; These seem to compare fine with current code, and I think all of
   ;; them have the namespaced? flag equal to false when read by
   ;; rewrite-clj, because they all begin with a single colon.
   [:clojure.pprint/blink :blank :string/bar]
   ;; These miscompare with current code, between rewrite-clj and
   ;; tools.reader.  I believe it is because their namespace portion
   ;; is set to 'user' the way I am reading it with rewrite-clj,
   ;; because that is what it sees the value of *ns* as while reading.
   ;; tools.reader does the correct thing, because Eastwood binds *ns*
   ;; to the current namespace every time an ns or in-ns form is read,
   ;; _because it is then immediately eval'd_.
   [
    ;; tools.reader reads this as :clojure.string/foo, expanding the
    ;; string alias to clojure.string
    ::string/foo   

    ;; tools.reader reads this as :testcases.rewrite-clj/id, because
    ;; testcases.rewrite-clj is the current namespace, as set by the
    ;; ns form at the top of this file.
    ::id
    ]])
