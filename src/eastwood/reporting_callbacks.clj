(ns eastwood.reporting-callbacks
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [eastwood.util :as util]
            [clojure.string :as str]))

(def last-cwd-shown (atom nil))

(defn print-warning [{:keys [warn-data] :as info} cwd]
  (when (not= cwd @last-cwd-shown)
    (reset! last-cwd-shown cwd)
    (println (format "Entering directory `%s'" cwd)))
  (println (format "%s:%s:%s: %s: %s"
                   (-> warn-data :uri-or-file-name str)
                   ;; Emacs compilation-mode default regex's
                   ;; do not recognize warning lines with
                   ;; nil instead of decimal numbers for
                   ;; line/col number.  Make up values if we
                   ;; don't know them.
                   (or (-> warn-data :line) "1")
                   (or (-> warn-data :column) "1")
                   (name (-> warn-data :linter))
                   (-> warn-data :msg))))

(defrecord PrintingReporter [opts warnings analyzer-exceptions errors warn-writer])

(defrecord SilentReporter [opts warnings analyzer-exceptions errors])

(defn printing-reporter [opts]
  (let [wrtr (java.io.PrintWriter. *out* true)
        warn-wrtr (if-let [out (:out opts)]
                    (io/writer out)
                    wrtr)]
    (->PrintingReporter opts (atom []) (atom []) (atom []) warn-wrtr)))

(defn silent-reporter [opts]
  (->SilentReporter opts (atom []) (atom []) (atom [])))

(defn lint-error [reporter error]
  (swap! (:errors reporter) conj error))

(defn debug [reporter thing msg]
  (when (util/debug? thing (:opts reporter))
    (println msg)))

(defn error [reporter e]
  (println "Linting failed:")
  (util/pst e nil)
  (flush)
  e)

(defn warnings [reporter]
  @(:warnings reporter))

(defn analyzer-exceptions [reporter]
  @(:analyzer-exceptions reporter))

(defn dispatch-fn [record & _]
  (type record))

(defmulti lint-warning dispatch-fn)
(defmulti analyzer-exception dispatch-fn)
(defmulti note dispatch-fn)

(defmethod lint-warning PrintingReporter [reporter warning]
  (swap! (:warnings reporter) conj warning)
  (binding [*out* (:warn-writer reporter)]
    (print-warning warning (-> reporter :opts :cwd))
    (flush)))

(defmethod analyzer-exception PrintingReporter [reporter exception]
  (swap! (:analyzer-exceptions reporter) conj exception)
  (println (str/join "\n" (:msgs exception)))
  (flush))

(defmethod note PrintingReporter [reporter msg]
  (println msg)
  (flush))

(defmethod note SilentReporter [reporter msg] )

(defmethod lint-warning SilentReporter [reporter warning]
  (swap! (:warnings reporter) conj warning))

(defmethod analyzer-exception SilentReporter [reporter exception]
  (swap! (:analyzer-exceptions reporter) conj exception))

(defn add-warnings [reporter warnings]
  (doseq [warning warnings]
    (lint-warning reporter warning)))

(defn add-errors [reporter errors]
  (doseq [error errors]
    (lint-error reporter error)))

(defn add-exceptions [reporter exceptions]
  (doseq [exception exceptions]
    (lint-error reporter exception)))
