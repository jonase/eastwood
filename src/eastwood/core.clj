(ns eastwood.core
  (:require [clojure.java.io :as io]
            [eastwood.analyze-ns :as analyze]
            [clojure.set :as set]
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clojure.repl :as repl]
            [clojure.tools.namespace :as clj-ns]
            [eastwood.linters :as linters])
  (:import [java.io PushbackReader]
           [clojure.lang LineNumberingPushbackReader]))


(def ^:dynamic *eastwood-version*
  {:major 0, :minor 0, :incremental 3, :qualifier ""})

(defn eastwood-version []
  (let [{:keys [major minor incremental qualifier]} *eastwood-version*]
    (str major "." minor "." incremental
         (if (and qualifier (not= qualifier ""))
           (str "-" qualifier)
           ""))))

(def ^:private default-linters
  #{linters/misplaced-docstrings
    linters/def-in-def
    linters/redefd-vars
    linters/deprecations
    linters/unused-namespaces
    linters/unused-ret-vals})

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
            result (linter analyze-results)]
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
    (mapcat #(% analyze-results) linters)))

(defn resolve-symbol 
  "Attempts to resolve the fully qualified sym to its var."
  [sym]
  (let [ns (symbol (namespace sym))
        s (symbol (name sym))]
    (require ns)
    (if-let [v (ns-resolve ns s)]
      v
      (throw (ex-info (str "Cannot resolve symbol: " sym) {:sym sym})))))

(defn resolve-linters [linters]
  (let [linters (mapv resolve-symbol linters)]
    (when-not (empty? linters)
      linters)))

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
        linters (set (or (resolve-linters (:linters opts))
                         default-linters))
        excluded-linters (set (resolve-linters (:exclude-linters opts)))
        add-linters (set (resolve-linters (:add-linters opts)))
        linters (-> (set/difference linters excluded-linters)
                    (set/union add-linters))]
    (println (format "== Eastwood %s Clojure %s JVM %s"
                     (eastwood-version)
                     (clojure-version)
                     (get (System/getProperties) "java.version")))
    (doseq [namespace namespaces]
      (try
        (lint-ns namespace linters opts)
        (catch RuntimeException e
          (println "Linting failed:")
          (repl/pst e 100))))))
