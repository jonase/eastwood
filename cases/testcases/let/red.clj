(ns testcases.let.red)

(defn faulty []
  (let [a (if (seq? {})
            1
            2)]
    a))
