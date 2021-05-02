(ns testcases.tufte-example
  (:require [taoensso.tufte :refer [defnp p profiled profile]]))

;; https://github.com/jonase/eastwood/issues/161
(defnp my-fun
  []
  (+ 1 1))

;; https://github.com/ptaoussanis/tufte/tree/396ca0d5e05db9d9721635ca11bf4e00fb393ab8#10-second-example

;;; Let's define a couple dummy fns to simulate doing some expensive work
(defn get-x [] (Thread/sleep 500)             "x val")
(defn get-y [] (Thread/sleep (rand-int 1000)) "y val")

;; How do these fns perform? Let's check:

(profile         ; Profile any `p` forms called during body execution
 {}              ; Profiling options; we'll use the defaults for now
 (dotimes [_ 5]
   (p :get-x (get-x))
   (p :get-y (get-y))))
