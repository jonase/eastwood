(ns testcases.deprecations.own-ns.green)

(defn ^:deprecated foo [])

;; https://github.com/jonase/eastwood/issues/402
(defn bar []
  (foo))
