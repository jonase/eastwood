(ns eastwood.reporting-callbacks
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [eastwood.util :as util]))

(defn assert-keys [m key-seq]
  (assert (util/has-keys? m key-seq)))

(defn replace-path-in-compiler-error
  [msg opt]
  (let [[match pre _ path
         line-col post] (re-matches #"((Reflection|Boxed math) warning), (.*?)(:\d+:\d+)(.*)"
                                    msg)
        url (and match (io/resource path))
        inf (and url (util/file-warn-info url (:cwd opt)))]
    (if inf
      ;; The filename:line:col should be first in the output
      (str (:uri-or-file-name inf) line-col ": " pre post)
      msg)))


(defn make-default-msg-cb [wrtr]
  (fn default-msg-cb [info]
    (binding [*out* wrtr]
      (println (:msg info))
      (flush))))

(defn make-default-eval-msg-cb
  ([wrtr]
     (make-default-eval-msg-cb wrtr {}))
  ([wrtr opt]
     (fn default-msg-cb [info]
       (let [orig-msg (:msg info)
             msg (if (= :eval-err (:kind info))
                   (replace-path-in-compiler-error orig-msg opt)
                   orig-msg)]
         (binding [*out* wrtr]
           (println msg)
           (flush))))))


(defn make-default-dirs-scanned-cb [wrtr]
  (fn default-dirs-scanned-cb [info]
    (binding [*out* wrtr]
      (println "Directories scanned for source files:")
      (print " ")
      (doseq [d (:dirs-scanned info)]
        (print " ")
        (print (:uri-or-file-name d)))
      (println)
      (flush))))


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

(defn make-default-debug-ast-cb [wrtr]
  (fn default-debug-ast-cb [info]
    (binding [*out* wrtr]
      (util/pprint-ast-node (:ast info))
      (flush))))

(defn make-default-form-cb [wrtr]
  (fn [{:keys [event form]}]
    (binding [*out* wrtr]
      (case event
        :begin-file (println (format "\n\n== Analyzing file '%s'\n" form))
        :form (util/pprint-form form)))))


(defn assert-debug-form-cb-has-proper-keys [info]
  (assert-keys info [:event :opt])
  (case (:event info)
    :form (assert-keys info [:form])
    :begin-file (assert-keys info [:filename])))


(defn assert-cb-has-proper-keys [info]
  (case (:kind info)
    :error     (assert-keys info [:msg :opt])
    :dirs-scanned (assert-keys info [:dirs-scanned :opt])
    :lint-warning (assert-keys info [:warn-data])
    :note      (assert-keys info [:msg :opt])
    :eval-out  (assert-keys info [:msg :opt])
    :eval-err  (assert-keys info [:msg :opt])
    :debug     (assert-keys info [:msg :opt])
    :debug-ast (assert-keys info [:ast :opt])
    :debug-form-read     (assert-debug-form-cb-has-proper-keys info)
    :debug-form-analyzed (assert-debug-form-cb-has-proper-keys info)
    :debug-form-emitted  (assert-debug-form-cb-has-proper-keys info)))


(defn make-eastwood-cb [{:keys [error dirs-scanned lint-warning note
                                eval-out eval-err
                                debug debug-ast
                                debug-form-read debug-form-analyzed
                                debug-form-emitted]}]
  (fn eastwood-cb [info]
    (assert-cb-has-proper-keys info)
    (case (:kind info)
      :error     (error info)
      :dirs-scanned (dirs-scanned info)
      :lint-warning (lint-warning info)
      :note      (note info)
      :eval-out  (eval-out info)
      :eval-err  (eval-err info)
      :debug     (debug info)
      :debug-ast (debug-ast info)
      :debug-form-read     (when debug-form-read
                             (debug-form-read info))
      :debug-form-analyzed (when debug-form-analyzed
                             (debug-form-analyzed info))
      :debug-form-emitted  (when debug-form-emitted
                             (debug-form-emitted info)))))

(defn make-default-cb [opts]
  (let [;;wrtr (io/writer "east-out.txt")   ; see comment above
        wrtr (java.io.PrintWriter. *out* true)
        warn-wrtr (if (contains? opts :out)
                    (io/writer (:out opts))
                    wrtr)
        default-msg-cb (make-default-msg-cb wrtr)
        eval-out-err-msg-cb (make-default-eval-msg-cb wrtr opts)
        default-dirs-scanned-cb (make-default-dirs-scanned-cb wrtr)
        default-lint-warning-cb (make-default-lint-warning-cb warn-wrtr)
        default-debug-ast-cb (make-default-debug-ast-cb wrtr)

        [form-read-cb form-analyzed-cb form-emitted-cb]
        (if (util/debug? :compare-forms opts)
          [ (make-default-form-cb (io/writer "forms-read.txt"))
            (make-default-form-cb (io/writer "forms-analyzed.txt"))
            (make-default-form-cb (io/writer "forms-emitted.txt")) ]
          [])]
    (make-eastwood-cb {:error default-msg-cb
                       :dirs-scanned default-dirs-scanned-cb
                       :lint-warning default-lint-warning-cb
                       :note default-msg-cb
                       :eval-out eval-out-err-msg-cb
                       :eval-err eval-out-err-msg-cb
                       :debug default-msg-cb
                       :debug-ast default-debug-ast-cb
                       :debug-form-read form-read-cb
                       :debug-form-analyzed form-analyzed-cb
                       :debug-form-emitted form-emitted-cb})))

