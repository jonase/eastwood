(ns eastwood.test.testcases.f06)


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



(comment

(require '[clojure.inspector :as insp])
(require '[eastwood.analyze-ns :as ana] :reload)
(require '[eastwood.util :as util] :reload)
(require '[eastwood.linters.unused :as un] :reload)

(def a (ana/analyze-ns 'eastwood.test.testcases.f06 :opt {}))
(insp/inspect-tree a)

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
