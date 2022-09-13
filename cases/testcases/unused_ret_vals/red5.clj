(ns testcases.unused-ret-vals.red5
  "https://github.com/jonase/eastwood/issues/441")

(defn bad1 [x]
  (conj! x 2)
  42)

(defn bad2 [x]
  (assoc! x :a 2)
  42)

(defn bad3 [x]
  (pop! x)
  42)

(defn bad4 [x]
  (dissoc! x :a)
  42)
