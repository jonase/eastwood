(ns testcases.core-async-example.alt
  (:require
   [clojure.core.async :refer [alt! alt!! go timeout]]))

(defn sample
  "https://github.com/jonase/eastwood/issues/411"
  [trade-ch]
  (go
    (let [timeout-ch (timeout 1000)
          trade 100]
      (-> (alt!
            [[trade-ch trade]] :sent
            timeout-ch :timed-out)
          print))))

(defn sample2
  "https://github.com/jonase/eastwood/issues/422"
  [trade-ch]
  (let [timeout-ch (timeout 1000)
        trade 100]
    (-> (alt!!
          [[trade-ch trade]] :sent
          timeout-ch :timed-out)
        print)))
