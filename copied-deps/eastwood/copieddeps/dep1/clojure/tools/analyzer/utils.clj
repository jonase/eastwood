;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns eastwood.copieddeps.dep1.clojure.tools.analyzer.utils
  (:refer-clojure :exclude [record? boolean?])
  (:require [eastwood.copieddeps.dep1.clojure.tools.analyzer.env :as env])
  (:import (clojure.lang IRecord IType IObj
                         IReference Var)))

(defn into!
  "Like into, but for transients"
  [to from]
  (reduce conj! to from))

(defn rseqv
  "Same as (comp vec rseq)"
  [v]
  (vec (rseq v)))

(defn ctx
  "Returns a copy of the passed environment with :context set to ctx"
  [env ctx]
  (assoc env :context ctx))

(defn dissoc-env
  "Dissocs :env from the ast"
  [ast]
  (dissoc ast :env))

(defn butlast+last
  "Returns same value as (juxt butlast last), but slightly more
   efficient since it only traverses the input sequence s once, not
   twice."
  [s]
  (loop [butlast (transient [])
         s s]
    (if-let [xs (next s)]
      (recur (conj! butlast (first s)) xs)
      [(seq (persistent! butlast)) (first s)])))

(defn update-vals
  "Applies f to all the vals in the map"
  [m f]
  (reduce-kv (fn [m k v] (assoc m k (f v))) {} (or m {})))

(defn update-keys
  "Applies f to all the keys in the map"
  [m f]
  (reduce-kv (fn [m k v] (assoc m (f k) v)) {} (or m {})))

(defn update-kv
  "Applies f to all the keys and vals in the map"
  [m f]
  (reduce-kv (fn [m k v] (assoc m (f k) (f v))) {} (or m {})))

(defn record?
  "Returns true if x is a record"
  [x]
  (instance? IRecord x))

(defn type?
  "Returns true if x is a type"
  [x]
  (instance? IType x))

(defn obj?
  "Returns true if x implements IObj"
  [x]
  (instance? IObj x))

(defn reference?
  "Returns true if x implements IReference"
  [x]
  (instance? IReference x))

(defmacro compile-if
  [exp then & else]
  (if (try (eval exp)
           (catch Exception _ false))
    `(do ~then)
    `(do ~@else)))

(defn regex?
  "Returns true if x is a regex"
  [x]
  (instance? (compile-if (Class/forName "java.util.regex.Pattern")
               java.util.regex.Pattern
               System.Text.RegularExpressions.Regex)
             x))

(defn boolean?
  "Returns true if x is a boolean"
  [x]
  (or (true? x) (false? x)))

(defn classify
  "Returns a keyword describing the form type"
  [form]
  (cond
   (nil? form)     :nil
   (boolean? form) :bool
   (keyword? form) :keyword
   (symbol? form)  :symbol
   (string? form)  :string
   (number? form)  :number
   (type? form)    :type
   (record? form)  :record
   (map? form)     :map
   (vector? form)  :vector
   (set? form)     :set
   (seq? form)     :seq
   (char? form)    :char
   (regex? form)   :regex
   (class? form)   :class
   (var? form)     :var
   :else           :unknown))

(defn private?
  "Returns true if the var is private"
  ([var] (private? var nil))
  ([var m]
     (:private (or m (meta var)))))

(defn macro?
  "Returns true if the var maps to a macro"
  ([var] (macro? var nil))
  ([var m]
     (:macro (or m (meta var)))))

(defn constant?
  "Returns true if the var is a const"
  ([var] (constant? var nil))
  ([var m]
     (:const (or m (meta var)))))

(defn dynamic?
  "Returns true if the var is dynamic"
  ([var] (dynamic? var nil))
  ([var m]
     (or (:dynamic (or m (meta var)))
         (when (var? var) ;; workaround needed since Clojure doesn't always propagate :dynamic
           (.isDynamic ^Var var)))))

(defn protocol-node?
  "Returns true if the var maps to a protocol function"
  ([var] (protocol-node? var nil))
  ([var m]
     (boolean (:protocol (or m (meta var)))))) ;; conveniently this is true in both clojure and clojurescript

(defn resolve-ns
  "Resolves the ns mapped by the given sym in the global env"
  [ns-sym {:keys [ns]}]
  (when ns-sym
    (let [namespaces (:namespaces (env/deref-env))]
      (or (get-in namespaces [ns :aliases ns-sym])
          (:ns (namespaces ns-sym))))))

(defn resolve-sym
  "Resolves the value mapped by the given sym in the global env"
  [sym {:keys [ns] :as env}]
  (when (symbol? sym)
    (let [sym-ns (when-let [ns (namespace sym)]
                   (symbol ns))
          full-ns (resolve-ns sym-ns env)]
      (when (or (not sym-ns) full-ns)
        (let [name (if sym-ns (-> sym name symbol) sym)]
          (-> (env/deref-env) :namespaces (get (or full-ns ns)) :mappings (get name)))))))

(defn arglist-for-arity
  "Takes a fn node and an argc and returns the matching arglist"
  [fn argc]
  (let [arglists (->> fn :arglists (sort-by count))
        arglist (->> arglists (filter #(= argc (count %))) first)
        last-arglist (last arglists)]
    (or arglist
        (when (and (some '#{&} last-arglist)
                   (>= argc (- (count last-arglist) 2)))
          last-arglist))))

(defn select-keys'
  "Like clojure.core/select-keys, but uses transients and doesn't preserve meta"
  [map keyseq]
  (loop [ret (transient {}) keys (seq keyseq)]
    (if keys
      (let [entry (find map (first keys))]
        (recur (if entry
                 (conj! ret entry)
                 ret)
               (next keys)))
      (persistent! ret))))

(defn merge'
  "Like merge, but uses transients"
  [m & mms]
  (persistent! (reduce conj! (transient (or m {})) mms)))

(defn mapv'
  "Like mapv, but short-circuits on reduced"
  [f v]
  (let [c (count v)]
    (loop [ret (transient []) i 0]
      (if (> c i)
        (let [val (f (nth v i))]
          (if (reduced? val)
            (reduced (persistent! (reduce conj! (conj! ret @val) (subvec v (inc i)))))
            (recur (conj! ret val) (inc i))))
        (persistent! ret)))))

(defn source-info
  "Returns the available source-info keys from a map"
  [m]
  (when (:line m)
    (select-keys' m #{:file :line :column :end-line :end-column :source-span})))

(defn -source-info
  "Returns the source-info of x"
  [x env]
  (merge' (source-info env)
          (source-info (meta x))
          (when-let [file (and (not= *file* "NO_SOURCE_FILE")
                               *file*)]
            {:file file})))

(defn const-val
  "Returns the value of a constant node (either :quote or :const)"
  [{:keys [form val]}]
  (or val form))

(def mmerge
  "Same as (fn [m1 m2] (merge-with merge m2 m1))"
  #(merge-with merge' %2 %1))
