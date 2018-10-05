(ns eastwood.reporting-callbacks
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [eastwood.util :as util]
            [clojure.string :as str]))

(defn assert-keys [m key-seq]
  (assert (util/has-keys? m key-seq)))


(defn make-default-msg-cb [wrtr]
  (fn default-msg-cb [info]
    (binding [*out* wrtr]
      (println (:msg info))
      (flush))))


(defn dirs-scanned [dirs]
  (when dirs
    (println "Directories scanned for source files:")
    (print " ")
    (->> dirs
         (map :uri-or-file-name)
         (str/join " ")
         (println))
    (flush)))

;; Use the option :warning-format :map-v1 to get linter warning maps
;; as they were generated in Eastwood 0.1.0 thru 0.1.4, intended only
;; for comparing output from later versions against those versions
;; more easily.

(def last-cwd-shown (atom nil))

(def empty-ordered-lint-warning-map-v1
  (util/ordering-map [:linter
                      :msg
                      :file
                      :line
                      :column]))

(def empty-ordered-lint-warning-map-v2
  (util/ordering-map [:file
                      :line
                      :column
                      :linter
                      :msg
                      :uri-or-file-name]))

(defn make-default-lint-warning-cb [wrtr]
  (fn default-lint-warning-cb [info]
    (binding [*out* wrtr]
      (let [warning-format (or (-> info :opt :warning-format)
                               :location-list-v1)
            i (case warning-format
                :map-v1 (into empty-ordered-lint-warning-map-v1
                              (select-keys (:warn-data info)
                                           [:linter :msg :file :line :column]))
                :map-v2 (into empty-ordered-lint-warning-map-v2
                              (select-keys (:warn-data info)
                                           [:linter :msg :file :line :column
                                            :uri-or-file-name
                                            ;; :uri
                                            ;; :namespace-sym
                                            ]))
                :location-list-v1 (:warn-data info))]
        (if (= warning-format :location-list-v1)
          (do
            (let [cwd (-> info :opt :cwd)]
              (when (not= cwd @last-cwd-shown)
                (reset! last-cwd-shown cwd)
                (println (format "Entering directory `%s'" cwd))))
            (println (format "%s:%s:%s: %s: %s"
                             (-> i :uri-or-file-name str)
                             ;; Emacs compilation-mode default regex's
                             ;; do not recognize warning lines with
                             ;; nil instead of decimal numbers for
                             ;; line/col number.  Make up values if we
                             ;; don't know them.
                             (or (-> i :line) "1")
                             (or (-> i :column) "1")
                             (name (-> i :linter))
                             (-> i :msg))))
          (do
            (pp/pprint i)
            (println)
            (flush)))))))

(defn assert-cb-has-proper-keys [info]
  (case (:kind info)
    :error     (assert-keys info [:msg :opt])
    :lint-warning (assert-keys info [:warn-data])
    :note      (assert-keys info [:msg :opt])
    :debug     (assert-keys info [:msg :opt])))

(defn make-eastwood-cb [{:keys [error lint-warning note
                                eval-out eval-err
                                debug debug-ast
                                debug-form-read debug-form-analyzed
                                debug-form-emitted]}]
  (fn eastwood-cb [info]
    (assert-cb-has-proper-keys info)
    (case (:kind info)
      :error     (error info)
      :lint-warning (lint-warning info)
      :note      (note info)
      :debug     (debug info))))

(defn make-default-cb [opts]
  (let [;;wrtr (io/writer "east-out.txt")   ; see comment above
        wrtr (java.io.PrintWriter. *out* true)
        warn-wrtr (if (contains? opts :out)
                    (io/writer (:out opts))
                    wrtr)
        default-msg-cb (make-default-msg-cb wrtr)
        default-lint-warning-cb (make-default-lint-warning-cb warn-wrtr)]
    (make-eastwood-cb {:error default-msg-cb
                       :lint-warning default-lint-warning-cb
                       :note default-msg-cb
                       :debug default-msg-cb})))
