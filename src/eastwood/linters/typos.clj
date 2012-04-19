(ns eastwood.linters.typos
  (:use [analyze.util :only [expr-seq]]
        [eastwood.util :only [op=]]
        [clojure.pprint :only [pprint]])
  (:import [name.fraser.neil.plaintext diff_match_patch]))


(def ^:private dmp (diff_match_patch.))

(defn levenshtein [s1 s2]
  (.diff_levenshtein dmp (.diff_main dmp s1 s2)))

(defn keyword-typos [exprs]
  (let [freqs (->> (mapcat expr-seq exprs)
                   (filter (op= :keyword))
                   (map :val)
                   (filter keyword?)
                   frequencies)]
    (for [[kw1 n] freqs
          [kw2 _] freqs
          :let [s1 (name kw1)
                s2 (name kw2)]
          :when (and (= n 1)
                     (not= s1 s2)
                     (< 3 (count s1))
                     (< (levenshtein s1 s2) 2))]
      {:linter :keyword-typo
       :msg (format "Possible keyword typo: %s instead of %s?" kw1 kw2)})))

