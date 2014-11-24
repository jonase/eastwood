;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^:skip-wiki eastwood.copieddeps.dep10.clojure.tools.reader.impl.commons
  (:refer-clojure :exclude [char read-line])
  (:use eastwood.copieddeps.dep10.clojure.tools.reader.reader-types
        eastwood.copieddeps.dep10.clojure.tools.reader.impl.utils)
  (:import (clojure.lang BigInt Numbers)
           (java.util regex.Pattern regex.Matcher)
           java.lang.reflect.Constructor))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn number-literal?
  "Checks whether the reader is at the start of a number literal"
  [reader initch]
  (or (numeric? initch)
      (and (or (identical? \+ initch) (identical?  \- initch))
           (numeric? (peek-char reader)))))

(defn read-past
  "Read until first character that doesn't match pred, returning
   char."
  [pred rdr]
  (loop [ch (read-char rdr)]
    (if (pred ch)
      (recur (read-char rdr))
      ch)))

(defn skip-line
  "Advances the reader to the end of a line. Returns the reader"
  [reader]
  (loop []
    (when-not (newline? (read-char reader))
      (recur)))
  reader)

(def ^Pattern int-pattern #"([-+]?)(?:(0)|([1-9][0-9]*)|0[xX]([0-9A-Fa-f]+)|0([0-7]+)|([1-9][0-9]?)[rR]([0-9A-Za-z]+)|0[0-9]+)(N)?")
(def ^Pattern ratio-pattern #"([-+]?[0-9]+)/([0-9]+)")
(def ^Pattern float-pattern #"([-+]?[0-9]+(\.[0-9]*)?([eE][-+]?[0-9]+)?)(M)?")

(defn- match-int
  [^Matcher m]
  (if (.group m 2)
    (if (.group m 8) 0N 0)
    (let [negate? (= "-" (.group m 1))
          a (cond
             (.group m 3) [(.group m 3) 10]
             (.group m 4) [(.group m 4) 16]
             (.group m 5) [(.group m 5) 8]
             (.group m 7) [(.group m 7) (Integer/parseInt (.group m 6))]
             :else        [nil nil])
          ^String n (a 0)
          radix (int (a 1))]
      (when n
        (let [bn (BigInteger. n radix)
              bn (if negate? (.negate bn) bn)]
          (if (.group m 8)
            (BigInt/fromBigInteger bn)
            (if (< (.bitLength bn) 64)
              (.longValue bn)
              (BigInt/fromBigInteger bn))))))))

(defn- match-ratio
  [^Matcher m]
  (let [^String numerator (.group m 1)
        ^String denominator (.group m 2)
        numerator (if (.startsWith numerator "+")
                    (subs numerator 1)
                    numerator)]
    (/ (-> numerator   BigInteger. BigInt/fromBigInteger Numbers/reduceBigInt)
       (-> denominator BigInteger. BigInt/fromBigInteger Numbers/reduceBigInt))))

(defn- match-float
  [^String s ^Matcher m]
  (if (.group m 4)
    (BigDecimal. ^String (.group m 1))
    (Double/parseDouble s)))

(defn match-number [^String s]
  (let [int-matcher (.matcher int-pattern s)]
    (if (.matches int-matcher)
      (match-int int-matcher)
      (let [float-matcher (.matcher float-pattern s)]
        (if (.matches float-matcher)
          (match-float s float-matcher)
          (let [ratio-matcher (.matcher ratio-pattern s)]
            (when (.matches ratio-matcher)
              (match-ratio ratio-matcher))))))))

(defn parse-symbol [^String token]
  (when-not (or (= "" token)
                (not= -1 (.indexOf token "::")))
    (let [ns-idx (.indexOf token "/")]
      (if-let [ns (and (pos? ns-idx)
                       (subs token 0 ns-idx))]
        (let [ns-idx (inc ns-idx)]
          (when-not (== ns-idx (count token))
            (let [sym (subs token ns-idx)]
              (when (and (not (numeric? (nth sym 0)))
                         (not (= "" sym))
                         (or (= sym "/")
                             (== -1 (.indexOf sym "/"))))
                [ns sym]))))
        (when (or (= token "/")
                  (== -1 (.indexOf token "/")))
          [nil token])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; readers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn read-comment
  [rdr & _]
  (skip-line rdr))

(def debug-comments false)

(def saved-comments-atom (atom [[]]))

(defn get-saved-comments-stack []
  @saved-comments-atom)

(defn push-saved-comments-stack []
  (when debug-comments
    (println (format "scs: push []")))
  (swap! saved-comments-atom conj [])
  @saved-comments-atom)

(defn pop-saved-comments-stack []
  (let [old-stack @saved-comments-atom
        top (peek old-stack)]
    (when debug-comments
      (println (format "scs: pop top=%s" top)))
    (swap! saved-comments-atom pop)
    old-stack))

(defn top-saved-comments []
  (let [c (peek @saved-comments-atom)
        c (if (= c []) nil c)]
    (when debug-comments
      (println (format "scs: top-saved-comments returned %d comments"
                       (count c))))
    c))

(defn clear-top-saved-comments []
  (let [c (top-saved-comments)]
    (swap! saved-comments-atom
           (fn [stack-of-vecs & args]
             (conj (pop stack-of-vecs) [])))
    (when debug-comments
      (println (format "scs: clear-top-saved-comments returned %d comments"
                       (count c))))
    c))

(defn read-comment-remembering-contents
  "Patterned after function read-string*.  Like read-comment and
skip-line, returns the reader."
  [reader & _]
  (loop [sb (StringBuilder.)
         ch (read-char reader)]
    (if (newline? ch)
      (do
        (when debug-comments
          (println (format "jafinger-dbg: Remember comment '%s'"
                           (str sb))))
        ;;(swap! saved-comments-atom conj (str sb))
        (swap! saved-comments-atom
               (fn [stack-of-vecs & args]
                 (conj (pop stack-of-vecs)
                       (apply conj (peek stack-of-vecs) args)))
               (str sb)))
      (recur (.append sb ch) (read-char reader))))
  reader)

(defn throwing-reader
  [msg]
  (fn [rdr & _]
    (reader-error rdr msg)))

(defn read-regex
  [rdr ch]
  (let [sb (StringBuilder.)]
    (loop [ch (read-char rdr)]
      (if (identical? \" ch)
        (Pattern/compile (str sb))
        (if (nil? ch)
          (reader-error rdr "EOF while reading regex")
          (do
            (.append sb ch )
            (when (identical? \\ ch)
              (let [ch (read-char rdr)]
                (if (nil? ch)
                  (reader-error rdr "EOF while reading regex"))
                (.append sb ch)))
            (recur (read-char rdr))))))))
