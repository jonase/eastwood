(ns testcases.const
  "Exercises https://github.com/jonase/eastwood/issues/341")

;; Makes this test case more deterministic when re-running the associated `deftest` from a repl:
(ns-unmap (-> ::_ namespace symbol the-ns)
          'balanced?)

(defn ^:const balanced?
  "Returns whether brackets contained in expr are balanced"
  ([expr] (balanced? expr 0))
  ([[x & xs] count]
   (cond (neg? count) false
         (nil? x) (zero? count)
         (= x \[) (recur xs (inc count))
         (= x \]) (recur xs (dec count))
         :else (recur xs count))))
