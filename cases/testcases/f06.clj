(ns testcases.f06)


;; y is obviously unused here
(defn fn-with-unused-args [x y]
  (* x x))

;; y is still an unused arg in this fn, too
(defn fn-with-unused-args2 [x y]
  (* x
     (let [y (dec x)]
       (* y 2))))

;; all used here
(defn fn-with-opt-args [x & y]
  [x y])

;; Inner anonymous function has unused args
(defn fn-with-unused-args3 [x]
  (let [foo (fn [y w z]
              (* y z))]
    (foo x (inc x) (dec x))))


;; Macros have implicit &form and &env args that should never be
;; warned about if they are not used, since they are rarely used.
(defmacro macro1 [x & body]
  `(when-not ~x
     ~@body))


;; body is unused
(defmacro macro2 [x & body]
  `(if ~x 1 2))


;; No warning for args with name _
(defn ignore-underline-args [_]
  (+ 5 7))


;; No warnings for args whose names begin with _ character
(defn default-value-fn [_k v]
  v)


;; There was a bug in tools.analyzer in the way it renamed the last
;; occurrence of y in this function -- it had a different :name than
;; the arg y.  Ticket TANAL-15 was filed and Nicola fixed it.
(defn fn-with-unused-args4 [x y z]
  (let [foo (fn [y z]
              (* y z))]
    (foo x y)))


(defprotocol CollReduce
  (coll-reduce [coll f] [coll f val]))


(extend-protocol CollReduce
  nil
  (coll-reduce
   ([coll f] (f))
   ([coll f val] (cons val coll)))

  clojure.lang.ASeq
  (coll-reduce
   ([coll f] [coll])
   ([coll f val] [coll f])))
