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
