(ns testcases.manifold-example
  "https://github.com/jonase/eastwood/issues/197"
  (:require
   [manifold.deferred :refer [let-flow]]))

(defn exceptional? [_x]
  (< (rand) 0.5))

(defn wrap-middlewares
  [handler]
  (fn [request]
    (let-flow [response (handler request)]
      (cond (exceptional? response) {:status 500 :body "my b"}
            (empty? response)       {:status 204}
            :else                   {:body response}))))
