(ns testcases.f04)


;; This case used to cause tools.analyzer to throw an exception before
;; ticket TANAL-12 was fixed.

(try
  (fn foo
    ([]
       nil)
    ([x]
       (if (< x 5)
         (println x)
         (recur (inc x)))))
  (catch Exception e
    (println "Exception occurred")))


;; Below are test cases for :local-shadows-var linter
(defn bar [x]
  (comment 1 2 3)
  ;; The let-binding of name should not cause a :local-shadows-var
  ;; linter warning, since it is never referenced in the body of the
  ;; let except as a value that is not a function.
  (let [name 'foo
        pmap {:a 1 :b 2}
        comment (fn [y] (println name map y))
        remove #(inc %)
        replace (comp str biginteger)
        shuffle distinct]
    (println "name" name)
    ;; should cause a warning for pmap, but current linter doesn't
    ;; detect that pmap is being used as a function here.
    (println (map pmap [1 2 3]))
    ;; No warning, because local binding to (fn ...) is detected by
    ;; tools.analyzer(.jvm) as a function.
    (comment 7)
    ;; No warning, because local binding to (inc %) is detected as a
    ;; function.
    (println (remove 5))
    ;; Eastwood currently isn't clever enough to recognize that (comp
    ;; ...)  returns a function value. Thus unlike the examples
    ;; above, it warns about this call to replace.
    (println (replace 5))
    ;; Similarly for shuffle being bound locally to the value of
    ;; distinct, which should resolve to clojure.core/distinct.
    (println (shuffle [1 2 2 3]))))

;; core.logic intentionally shadows the name loop using letfn.
;; Hopefully the result of analyzing this code makes it easy to
;; determine that loop's value is a function in this case, so I can
;; suppress the warning.

(defn shadowed-loop [v]
  (letfn [(loop [ys]
            (if ys
              (loop (next ys))
              28))]
    (loop v)))

(defn user-reported-example [fetched-data]
  (let [{count :count
         data  :data} fetched-data
        real-count (count data)]
    (inc real-count)))


;; Example code exhibiting the issue from Benjamin Peter, submitted in
;; description of Clojure ticket CLJ-1249

(defprotocol HasPets
  (dogs [this])
  (cats [this])
  (octopus [this])
  (cute-ones [this]))

;; Here the field "dogs" is added with the same name as the protocol

(defrecord Petshop [dogs] 
  HasPets
  (dogs [this]
    [:pluto :bethoven])
  (cats [this]
    [:tom])
  (octopus [this]
    [:henry])
  (cute-ones [this]
    ;; Here it was intended to call the function "dogs", instead the
    ;; field is used.
    (concat (dogs this) (cats this))))
