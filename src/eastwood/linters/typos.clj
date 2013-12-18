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

;(defn debug-seq [x]
;  (when (some #{:end-line} (flatten-also-colls x))
;    (println "dbg debug-seq is travering a thing that has :end-line in it:")
;    (binding [*print-meta* true
;              *print-level* 10
;              *print-length* 50]
;      (pp/pprint x)))
;  (seq x))
;
;(defn debug-flatten-also-colls
;  [x]
;  (filter (complement flattenable?)
;          (rest (tree-seq flattenable? debug-seq x))))

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

;; If we used the forms as read during analysis, there is the
;; disadvantage that any backquoted expressions will have keywords
;; like :line, :column, :end-line, and :end-column 'leak' from the
;; metadata added by the reader into the backquoted expression as
;; read.

;; Instead, re-read the forms from the string given by the :source key
;; on the linter argument, in a way that will not attach this :line
;; :column etc. metadata, implemented in string->forms.

(defn keyword-typos [{:keys [asts source]}]
  ;; Hack alert: Assumes that the first form in the file is an ns
  ;; form, and that the namespace remains the same throughout the
  ;; source file.
  (let [this-ns (-> (first asts) :env :ns the-ns)
;        _ (println (format "Namespace= %s\n"
;                           (-> (first asts) :env :ns the-ns)))
        forms (util/string->forms source this-ns)
        freqs (->> forms
                   flatten-also-colls
                   (filter keyword?)
                   frequencies)]
    (when debug-keywords-found
;      (println "dbx: forms:")
;      (binding [*print-meta* true
;                *print-level* 10
;                *print-length* 50]
;        (pp/pprint forms))
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
