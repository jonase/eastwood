(ns testcases.cond.green)

(defn foo []
  ;; https://github.com/jonase/eastwood/issues/169
  (cond-> {}
    true (assoc 1 1))

  (cond
    (< (rand) 0.5)
    1

    ;; https://github.com/jonase/eastwood/issues/169#issuecomment-328940985
    true
    false)

  (cond
    (< (rand) 0.5)
    1

    ;; https://github.com/jonase/eastwood/issues/169#issuecomment-144686370
    :default
    false))
