;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns ^:skip-wiki eastwood.copieddeps.dep10.clojure.tools.reader.impl.utils
  (:require eastwood.copieddeps.dep10.clojure.tools.reader.impl.ExceptionInfo) ;; force loading
  (:refer-clojure :exclude [char]))

(defn char [x]
  (when x
    (clojure.core/char x)))

;; getColumnNumber and *default-data-reader-fn* are available only since clojure-1.5.0-beta1
(def >=clojure-1-5-alpha*?
  (let [{:keys [minor qualifier]} *clojure-version*]
    (or (and (= minor 5)
             (not= "alpha"
                   (when qualifier
                     (subs qualifier 0 (dec (count qualifier))))))
        (> minor 5))))

(defmacro compile-if [cond then else]
  (if (eval cond)
    then
    else))

(compile-if (= 3 (:minor *clojure-version*))
  (do
    (defn ex-info
      ([msg map]
         (eastwood.copieddeps.dep10.clojure.tools.reader.impl.ExceptionInfo. msg map))
      ([msg map cause]
         (eastwood.copieddeps.dep10.clojure.tools.reader.impl.ExceptionInfo. msg map cause)))
    (defn ex-data
      [^eastwood.copieddeps.dep10.clojure.tools.reader.impl.ExceptionInfo ex]
      (.getData ex))
    (defn ex-info? [ex]
      (instance? eastwood.copieddeps.dep10.clojure.tools.reader.impl.ExceptionInfo ex)))

  (defn ex-info? [ex]
    (instance? clojure.lang.ExceptionInfo ex)))

(defn whitespace?
  "Checks whether a given character is whitespace"
  [ch]
  (when ch
    (or (Character/isWhitespace ^Character ch)
        (identical? \,  ch))))

(defn numeric?
  "Checks whether a given character is numeric"
  [^Character ch]
  (when ch
    (Character/isDigit ch)))

(defn newline?
  "Checks whether the character is a newline"
  [c]
  (or (identical? \newline c)
      (nil? c)))

(defn desugar-meta
  [f]
  (cond
    (keyword? f) {f true}
    (symbol? f)  {:tag f}
    (string? f)  {:tag f}
    :else        f))

(defn make-var
  "Returns an anonymous unbound Var"
  []
  (with-local-vars [x nil] x))
