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
;; false.  If they intended to assert that y was not nil, we could
;; recommend that they be explicit with (not (nil? y)).

;; To implement this, it seems necessary to check whether the symbol
;; in the condition is the name of an argument or not.

(defn correct-pre-8-but-suspicious [y]
  {:pre [y]}
  (dec y))
