(ns eastwood.linters.implicit-dependencies
  (:require
   [clojure.walk :as walk]
   [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.parse :as parse]
   [eastwood.util :as util])
  (:import
   (clojure.lang Namespace)))

(defn var->ns-symbol [var]
  (if (util/clojure-1-10-or-later)
    ;; Use the most accurate method:
    (-> var symbol namespace symbol)
    (let [^Namespace ns (-> var meta :ns)]
      (-> ns .-name))))

(defn within-other-ns-macro?
  [ast ns-sym]
  (let [macro-namespace-syms (->> ast
                                  util/enclosing-macros
                                  (keep #(some-> % :macro namespace symbol))
                                  set)]
    (-> macro-namespace-syms
        (disj ns-sym 'clojure.core)
        seq
        boolean)))

(defn implicit-dependencies [{:keys [asts forms]} _]
  (let [explicit-requires (atom [])
        _ (when-let [s (some->> asts (keep :eastwood/ns-source) first)]
            (when-let [n (some->> asts (keep :eastwood/ns-sym) first find-ns)]
              (let [require-forms #{'require `require}
                    all (util/string->forms s n false)]
                (->> all
                     (run! (fn [form]
                             (->> form
                                  (walk/postwalk (fn [x]
                                                   (when (and (list? x)
                                                              (-> x first require-forms)
                                                              (and (-> x second sequential?)
                                                                   (-> x second first #{'quote})))
                                                     (let [require-forms (->> x
                                                                              (rest)
                                                                              (remove keyword?)
                                                                              (mapv (fn [thing]
                                                                                      (cond-> thing
                                                                                        (and (sequential? thing)
                                                                                             (-> thing first #{'quote}))
                                                                                        last))))
                                                           ns-form (list 'ns '_ (apply list :require require-forms))
                                                           ns-names (parse/deps-from-ns-decl ns-form)]
                                                       (->> ns-names
                                                            (run! (partial swap! explicit-requires conj)))))
                                                   x)))))))))
        ns-ast                (first (util/ns-form-asts asts))
        ns-decl               (first (:raw-forms ns-ast))
        ns-sym                (parse/name-from-ns-decl ns-decl)
        namespace-dependency? (-> ns-decl
                                  parse/deps-from-ns-decl
                                  (into @explicit-requires)
                                  (conj ns-sym ;; consider namespace as part of itself
                                        ;; clojure core is always included in every namespace, no need to warn about it:
                                        'clojure.core))]
    (->> asts
         (mapcat ast/nodes)
         (keep (fn [{:keys [op env form]
                     var-ref :var
                     :as expr}]
                 (when (and (= op :var)
                            (not (within-other-ns-macro? expr ns-sym)))
                   (let [implicit-ns-sym (var->ns-symbol var-ref)]
                     (when-not (namespace-dependency? implicit-ns-sym)
                       {:linter                 :implicit-dependencies
                        :loc                    env
                        :implicit-namespace-sym implicit-ns-sym
                        :msg                    (format "Var %s refers to namespace %s that isn't explicitly required."
                                                        form
                                                        implicit-ns-sym)}))))))))
