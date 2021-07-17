(ns testcases.unhinted-reflective-call.red)

;; exercise that Eastwood can work even in face of these:
(set! *warn-on-reflection* false)

(defn foo [x y]
  [(-> x .theReflectiveCall)
   (-> x (.theReflectiveCall y))])
