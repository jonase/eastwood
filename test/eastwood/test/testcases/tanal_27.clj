(ns eastwood.test.testcases.tanal-27)


;; tools.analyzer.jvm threw an exception when analyzing the following
;; function.  Filed ticket TANAL-27 for this issue.

(defn foo [a]
  (let [err (fn [& msg] (apply str msg))]
      (err "Invalid arg 'a'" a)))
