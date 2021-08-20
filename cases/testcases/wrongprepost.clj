(ns testcases.wrongprepost)


(defn wrong1 [x]
  {:pre (pos? x)}
  x)


(defn wrong1b
  ([x] x)
  ([x y]
     {:pre (> x y)}
     (- x y)))


(defn ok2 [x]
  {:pre [(pos? x)]}
  x)


(defn ok-pre-wrong-post-3 [x]
  {:pre [(number? x)]
   :post [number?]}
  (+ x 3))


(defn ok-pre-ok-post-4 [x]
  {:pre [(number? x)]
   :post [(number? %)]}
  (+ x 3))


(defn ok-pre-wrong-post-5 [x]
  {:pre [(number? x)]
   :post (number? %)}
  (+ x 3))



(defn wrong-pre-ok-post-6 [x]
  {:pre (number? x)
   :post [(number? %)]}
  (+ x 3))


(defn f [x]
  (inc x))


(defn wrong-pre-7 [y]
  {:pre [f]}
  (dec y))


;; It would be good to give a different warning for the following case
;; than for wrong-pre-7, because the following may actually be what
;; the user intended, to assert that the argument y is neither nil nor
;; false. If they intended to assert that y was not nil, we could
;; recommend that they be explicit with (not (nil? y)).

;; To implement this, it seems necessary to check whether the symbol
;; in the condition is the name of an argument or not.

(defn correct-pre-8-but-suspicious [y]
  {:pre [y]}
  (dec y))


(defn wrong-pre-9 [y]
  {:pre [>= y 7]}
  (dec y))


(defn wrong-pre-wrong-post-10 [y]
  {:pre [(>= y 7) :a]
   :post [(>= % 6) % "constant"]}
  (dec y))



;; Certain kinds of destructuring in fn arg vectors cause an
;; additional let to be in the macroexpansion, which caused an earlier
;; version of the :wrong-pre-post linter to not find the assert form
;; ASTs. This example is stripped down a bit from a similar function
;; found in the Carmine library where this issue was first discovered.

(defn ok-different-macroexpansion-11
  [datastore & [{:keys [tqname freezer redis-ttl-ms]
                 :or   {tqname :default}}]]
  {:pre [(instance? Number datastore)
         (or (nil? freezer) (instance? clojure.lang.IPersistentMap freezer))
         (or (nil? redis-ttl-ms) (>= redis-ttl-ms (* 1000 60 60 10)))]}
  (assoc freezer :foo 7))


;; Like the above, but include some preconditions that should warn, to
;; verify that Eastwood can find them.

(defn wrong-pre-different-macroexpansion-12
  [datastore & [{:keys [tqname freezer redis-ttl-ms]
                 :or   {tqname :default}}]]
  {:pre [(instance? Number datastore)
         wrong-pre-9
         (or (nil? redis-ttl-ms) (>= redis-ttl-ms (* 1000 60 60 10)))]}
  (assoc freezer :foo 7))


(defn wrong-pre-different-macroexpansion-13
  [datastore & [{:keys [tqname freezer redis-ttl-ms]
                 :or   {tqname :default}}]]
  {:pre (instance? Number datastore)}
  (assoc freezer :foo 7))
