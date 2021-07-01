(ns testcases.unhinted-reflective-call.green)

(defn foo [x y]
  [(-> x .theReflectiveCall)
   (-> x (.theReflectiveCall y))])
