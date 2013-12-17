(ns eastwood.linters.typos
  (:require [clojure.pprint :as pp])
  (:require [eastwood.util :as util])
  (:import [name.fraser.neil.plaintext diff_match_patch]))

(def debug-keywords-found false)

(defn flattenable?
  [x]
  (or (sequential? x)
      (coll? x)))

(defn flatten-also-colls
  [x]
  (filter (complement flattenable?)
          (rest (tree-seq flattenable? seq x))))

(def ^:private ^diff_match_patch dmp (diff_match_patch.))

(defn levenshtein [s1 s2]
  (.diff_levenshtein dmp (.diff_main dmp s1 s2)))

;; Note: Walking the asts and looking for keywords also finds keywords
;; from macro expansions, ones that the developer never typed in their
;; code.  Better to use the forms to stay closer to the source code
;; they typed and warn about similar keywords there only.

;; The only disadvantage I know of in doing it this way is that
;; binding forms like {:keys [k1 k2 k3]} will see k1 k2 k3 as symbols
;; k1 k2 k3, not keywords :k1 :k2 :k3.

;; TBD: This method of using the forms and flattening them still
;; sometimes includes :line, :column, :end-line, and :end-column
;; metadata keys, and probably others I am not aware of, from the
;; reader.  It would be best to find a way to ignore that metadta, but
;; still pay attention to any keys in metadata that the user
;; explicitly typed in the source code.
(defn keyword-typos [{:keys [forms]}]
  (let [freqs (->> forms
                   flatten-also-colls
                   (filter keyword?)
                   frequencies)]
    (when debug-keywords-found
      (println "dbx: keyword-typos frequencies:")
      (pp/pprint (into (sorted-map) freqs)))
    (for [[kw1 n] freqs
          [kw2 _] freqs
          :let [s1 (name kw1)
                s2 (name kw2)]
          :when (and (= n 1)
                     (not= s1 s2)
                     (< 3 (count s1))
                     (< (levenshtein s1 s2) 2))]
      {:linter :keyword-typos
       :msg (format "Possible keyword typo: %s instead of %s ?" kw1 kw2)})))
