(ns testcases.rewrite-clj
  )


;; Code for testing rewrite-clj library


(defn quoted-symbol []
  'quoted-symbol)


(defn quoted-expr []
  '(1 2 3))


(defn quote-within-quote []
  '(1 2 '(3 4 5)))


(defn quoted-vector []
  '[1 2 3])


(defn hasheq-imap [m]
  (resolve 'clojure.core/hash-unordered-coll))


(defmacro foo6 [x]
  (quote (inc x)))

(defn standalone-checks [counter]
  @counter)


(defn fn-expr-1 [avl-map]
  #(key (nth avl-map %)))


(defn fn-expr-2 []
  #(%2 %1))


(defn fn-expr-2 []
  #(first %&))


(defn hash-seq [s]
  (.hashCode ^Object (first s)))


(defn metadata2 []
  ^{:a 1 :b 2} [1 2 3])


(defn metadata3 []
  #^{:a 1 :b 2} [1 2 3])


(defn var-quoted-symbol [s]
  #'inc)
