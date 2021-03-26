(ns eastwood.linters.implicit-dependencies
  (:require [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.parse :as ns-parse]
            [eastwood.util :as util]))


(defn var->ns-symbol [var]
  (if (util/clojure-1-10-or-later)
    ;; Use the most accurate method:
    (symbol (namespace (symbol var)))
    (let [^clojure.lang.Namespace ns (-> var meta :ns)]
      (.-name ns))))


(defn within-other-ns-macro?
  [ast ns-sym]
  (let [macro-namespace-syms (->> ast
                                  util/enclosing-macros
                                  (keep #(some-> % :macro namespace symbol))
                                  set)]

    (not (empty? (disj macro-namespace-syms
                       ns-sym
                       'clojure.core)))))


(defn implicit-dependencies [{:keys [asts forms] :as x} _]
  (let [ns-ast (first (util/ns-form-asts asts))
        ns-decl (first (:raw-forms ns-ast))
        ns-sym (ns-parse/name-from-ns-decl ns-decl)
        namespace-dependency? (conj (ns-parse/deps-from-ns-decl ns-decl)
                                    ;;consider namespace as part of itself
                                    ns-sym
                                    ;;clojure core is always included in every namespace, no need to warn about it
                                    'clojure.core)]


    (->> asts
         (mapcat ast/nodes)
         (keep (fn [expr]
                 (when (and (= (:op expr) :var)
                            (not (within-other-ns-macro? expr ns-sym)))
                   (let [implicit-ns-sym (var->ns-symbol (:var expr))]
                     (when (not (namespace-dependency? implicit-ns-sym))
;                       (println "META " (util/enclosing-macros expr))
                       {:linter :implicit-dependencies
                        :loc (:env expr)
                        :implicit-namespace-sym implicit-ns-sym
                        :msg (format "Var %s refers to namespace %s that isn't explicitly required."
                                     (:form expr) implicit-ns-sym)}))))))))
