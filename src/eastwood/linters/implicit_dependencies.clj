(ns eastwood.linters.implicit-dependencies
  (:require [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.parse :as ns-parse]
            [eastwood.util :as util]))


(defn var->ns-symbol [var]
  (let [^clojure.lang.Namespace ns (-> var meta :ns)]
    (.-name ns)))


(defn implicit-dependencies [{:keys [asts forms] :as x} _]
  (let [ns-ast (first (util/ns-form-asts asts))
        ns-decl (first (:raw-forms ns-ast))
        namespace-dependency? (conj (ns-parse/deps-from-ns-decl ns-decl)
                                    ;;consider namespace as part of itself
                                    (ns-parse/name-from-ns-decl ns-decl)
                                    ;;clojure core is always included in every namespace, no need to warn about it
                                    'clojure.core)]

    (->> asts
         (mapcat ast/nodes)
         (keep (fn [expr]
                 (when (= (:op expr) :var)
                   (let [implicit-ns-sym (var->ns-symbol (:var expr))]
                     (when (not (namespace-dependency? implicit-ns-sym))
                       {:linter :implicit-dependencies
                        :loc (:env expr)
                        :implicit-namespace-sym implicit-ns-sym
                        :msg (format "Var %s refers to namespace %s that isn't explicitly required."
                                     (:form expr) implicit-ns-sym)}))))))))
