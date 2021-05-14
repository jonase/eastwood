(ns testcases.let.green)

;; https://github.com/jonase/eastwood/issues/383
(defn ok []
  (let [{:keys [x]
         :as y}
        {:x 9}]
    1))
