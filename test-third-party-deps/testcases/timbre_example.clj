(ns testcases.timbre-example
  (:require [taoensso.timbre :as log]))

(defn uses-logging []
  (log/error 42)
  (log/warn 42)
  (log/info 42)
  (log/debug 42)

  (log/errorf "%s" 42)
  (log/warnf "%s" 42)
  (log/infof "%s" 42)
  (log/debugf "%s" 42))
