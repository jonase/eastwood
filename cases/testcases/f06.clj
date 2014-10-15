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
   ([coll f val] val))

  clojure.lang.ASeq
  (coll-reduce
   ([coll f] [coll])
   ([coll f val] [coll f])))


(comment

(require '[clojure.inspector :as insp])
(require '[eastwood.analyze-ns :as ana] :reload)
(require '[eastwood.util :as util] :reload)
(require '[eastwood.linters.unused :as un] :reload)

(defn order-ast [ast]
  (-> ast
      util/ast-to-ordered))

(defn clean-ast1 [ast]
  (-> ast
      (util/trim-ast :remove-only [:eastwood/ancestors :env])
      util/ast-to-ordered))

(defn clean-ast2 [ast]
  (-> ast
      (util/trim-ast :remove-only [:eastwood/ancestors])
      util/ast-to-ordered))

(def a (ana/analyze-ns 'testcases.f06 :opt {}))
(def a2 (update-in a [:analyze-results :asts] #(mapv clean-ast1 %)))
(insp/inspect-tree a2)

(def fn1 (:init (nth a 1)))
(def locs1 (->> fn1 util/ast-nodes (filter (util/op= :local))))
(#'un/unused-fn-args* fn1)
(#'un/params (-> fn1 :methods first))
(#'un/used-locals (-> fn1 :methods first :body util/ast-nodes))

(def fn4 (:init (nth a 4)))
(def locs4 (->> fn4 util/ast-nodes (filter (util/op= :local))))

(require '[clojure.tools.analyzer.jvm :as aj])
(def form (read-string "
(defn fn-with-unused-args3 [x y z]
  (let [foo (fn [y z]
              (* y z))]
    (foo x y)))
"))
(def env (aj/empty-env))
(def an (aj/analyze form env))
(def meth1 (-> an :init :methods first))
(def ret-expr-args (-> meth1 :body :ret :body :ret :args))

(map :name (:params meth1))
;;=> (x__#0 y__#0 z__#0)
(map :name ret-expr-args)
;;=> (x__#0 y__#-1)


)
