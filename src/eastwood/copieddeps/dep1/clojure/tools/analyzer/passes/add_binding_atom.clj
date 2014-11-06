;;   Copyright (c) Nicola Mometto, Rich Hickey & contributors.
;;   The use and distribution terms for this software are covered by the
;;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;   which can be found in the file epl-v10.html at the root of this distribution.
;;   By using this software in any fashion, you are agreeing to be bound by
;;   the terms of this license.
;;   You must not remove this notice, or any other, from this software.

(ns eastwood.copieddeps.dep1.clojure.tools.analyzer.passes.add-binding-atom
  (:require [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :refer [prewalk]]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.passes.uniquify :refer [uniquify-locals]]))

(defn add-binding-atom
  "Adds an atom-backed-map to every local binding,the same
   atom will be shared between all occurences of that local.

   The atom is put in the :atom field of the node."
  {:pass-info {:walk :pre :depends #{#'uniquify-locals} :state (fn [] (atom {}))}}
  ([ast] (prewalk ast (partial add-binding-atom (atom {}))))
  ([state ast]
     (case (:op ast)
       :binding
       (let [a (atom {})]
         (swap! state assoc (:name ast) a)
         (assoc ast :atom a))
       :local
       (assoc ast :atom (or (@state (:name ast))
                            (atom {})))
       ast)))
