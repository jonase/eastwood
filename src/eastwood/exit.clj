(ns eastwood.exit)
;; This is a dedicated ns because `eastwood.versioncheck` performs `(require 'eastwood.lint)` carefully.
;; We want to provide the `exit-fn` without providing more defns unawarely.

;; This is a defn because the result of `find-ns` can change over time
(defn exit-fn []
  (if (find-ns 'leiningen.core.main)
    @(resolve 'leiningen.core.main/exit)
    (fn [n]
      (System/exit n))))
