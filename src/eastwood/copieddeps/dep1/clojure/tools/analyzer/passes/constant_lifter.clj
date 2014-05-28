;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns eastwood.copieddeps.dep1.clojure.tools.analyzer.passes.constant-lifter
  (:require [eastwood.copieddeps.dep1.clojure.tools.analyzer :refer [-analyze]]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.utils :refer [const-val classify]]))

(defmulti constant-lift
  "If the node represents a collection with no metadata, and every item of that
   collection is a literal, transform the node to an equivalent :const node."
  :op)

(defmethod constant-lift :vector
  [{:keys [items form env] :as ast}]
  (if (and (every? :literal? items)
           (not (meta form)))
    (assoc (-analyze :const (mapv const-val items) env :vector)
      :form form)
    ast))

(defmethod constant-lift :map
  [{:keys [keys vals form env] :as ast}]
  (if (and (every? :literal? keys)
           (every? :literal? vals)
           (not (meta form)))
    (let [c (into (empty form)
                  (zipmap (mapv const-val keys)
                          (mapv const-val vals)))
          c (if (= (class c) (class form))
              c
              (apply array-map (mapcat identity c)))]
      (assoc (-analyze :const c env :map)
        :form form))
    ast))

(defmethod constant-lift :set
  [{:keys [items form env] :as ast}]
  (if (and (every? :literal? items)
           (not (meta form)))
    (assoc (-analyze :const (into (empty form)
                                  (set (mapv const-val items))) env :set)
      :form form)
    ast))

(defmethod constant-lift :default [ast] ast)
