(ns eastwood.core
  (:require [clojure.java.io :as io]
            [eastwood.analyze-ns :as analyze]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.repl :as repl]
            [clojure.tools.namespace :as clj-ns]
            [eastwood.linters.misc :as misc]
            [eastwood.linters.deprecated :as deprecated]
            [eastwood.linters.unused :as unused]
            [eastwood.linters.reflection :as reflection]
            [eastwood.linters.typos :as typos])
  (:import [java.io PushbackReader]
           [clojure.lang LineNumberingPushbackReader]))


(def ^:private linters
  {:naked-use misc/naked-use
   :misplaced-docstrings misc/misplaced-docstrings
   :def-in-def misc/def-in-def
   :redefd-vars misc/redefd-vars
   :reflection reflection/reflection
   :deprecations deprecated/deprecations
   :unused-fn-args unused/unused-fn-args
   :unused-private-vars unused/unused-private-vars
   :unused-namespaces unused/unused-namespaces
   :unused-ret-vals unused/unused-ret-vals
   :keyword-typos typos/keyword-typos})

(def ^:private default-linters
  #{;;:naked-use
    :misplaced-docstrings
    :def-in-def
    :redefd-vars
    ;;:reflection
    ;;:deprecations
    :unused-fn-args
    ;;:unused-private-vars
    :unused-namespaces
    :unused-ret-vals
    ;;:keyword-typos
    })

(defn- lint [exprs kw]
  ((linters kw) exprs))

(defn handle-no-matching-arity-for-fn [ns-sym opts dat]
  (let [{:keys [arity fn]} dat
        {:keys [arglists form var]} fn]
    (println (format "Function on var %s called on line %d
with %s args, but it is only known to take one of the following args:"
                     var (-> form meta :line) arity))
    (println (format "    %s"
                     (str/join "\n    " arglists)))))

(defn print-stack-trace-without-ex-data
  "Print the stack trace of exception e, but without the other
information about the exception.  This can be useful for Clojure
data-carrying exceptions where the data is very long and difficult to
read."
  [^Throwable e]
  (let [^Throwable e2 (Throwable. "Stack trace of the original exception:")]
    (. e2 (setStackTrace (.getStackTrace e)))
    (.printStackTrace e2)
    ;; Not sure if this is needed to keep stack traces and messages
    ;; printed to *out* from mingling with each other, but it doesn't
    ;; hurt.
    (. System/err flush)))

(defn handle-ex-data [ns-sym opts ^Throwable exc]
  (let [dat (ex-data exc)
        msg (.getMessage exc)]
    (cond
     (= msg "No matching arity found for function: ")
     (handle-no-matching-arity-for-fn ns-sym opts dat)
     :else
     (do
       ;; Print useful info about the exception so we might more
       ;; quickly learn how to enhance it.
       (println (format "Got exception with extra ex-data:"))
       (println (format "    msg='%s'" msg))
       (println (format "    (keys dat)=%s" (keys dat)))
       (binding [*print-meta* true
                 *print-level* 7
                 *print-length* 50]
         (pp/pprint dat))
       (print-stack-trace-without-ex-data exc)))))

(defn lint-ns [ns-sym linters opts]
  (println "== Linting" ns-sym "==")
  (let [{:keys [analyze-exception analyze-results]}
        (analyze/analyze-ns ns-sym :opt opts)]
    (doseq [linter linters
            result (lint analyze-results linter)]
      (pp/pprint result)
      (println))
    (when analyze-exception
      (println "An exception was thrown during linting of" ns-sym)
      (if (ex-data analyze-exception)
        (handle-ex-data ns-sym opts analyze-exception)
        (repl/pst analyze-exception 100))
      (println
"\nAn exception was thrown while analyzing namespace" ns-sym "
Lint results may be incomplete.  If there are compilation errors in
your code, try fixing those.  If not, check above for info on the
exception."))))

;; TBD: Think about what to do with analyze-exception in this
;; function.  Probably just return it to the caller in a map
;; containing it and the current ret value on different keys.
(defn lint-ns-noprint [ns-sym linters opts]
  (let [{:keys [analyze-exception analyze-results]}
        (analyze/analyze-ns ns-sym :opt opts)]
    (mapcat #(lint analyze-results %) linters)))

(defn run-eastwood [opts]
  ;; The following line is an attempt to avoid stack traces and other
  ;; output being mingled with each other, by making System.err and
  ;; System.out the same output stream.
  ;; http://stackoverflow.com/questions/6121786/java-synchronizing-standard-out-and-standard-error
  (. System (setErr System/out))
  ;; Note: Preserve order of (:namespaces opts) if specified, in case
  ;; it is important.
  (let [namespaces (distinct
                    (or (:namespaces opts)
                        (mapcat #(-> % io/file clj-ns/find-namespaces-in-dir)
                                (concat (:source-paths opts) (:test-paths opts)))))
        excluded-namespaces (set (:exclude-namespaces opts))
        namespaces (remove excluded-namespaces namespaces)
        linters (set (or (:linters opts)
                         default-linters))
        excluded-linters (set (:exclude-linters opts))
        add-linters (set (:add-linters opts))
        linters (-> (set/difference linters excluded-linters)
                    (set/union add-linters))]
    (doseq [namespace namespaces]
      (try
        (lint-ns namespace linters opts)
        (catch RuntimeException e
          (println "Linting failed:")
          (repl/pst e 100))))))
