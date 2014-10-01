(ns eastwood.linters.typetags
  (:require [clojure.string :as string]
            [eastwood.util :as util]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]))

(def default-classname-mapping
  (.getMappings (the-ns 'eastwood.linters.typetags)))

(defn replace-variable-tag-part
  "Wrong tags that were written like (def ^long foo ...) convert to
strings like clojure.core$long@deadbeef, where the deadbeef part is an
8-digit hex string that changes from one run to the next.  Replace
these strings with @<somehex>, simply to make them consistent from one
run to the next, thus easier to check for in unit tests, and producing
fewer lines of output in 'diff' from one Eastwood run to the next.  I
doubt the exact value of the hex digits has any lasting significance
needed by the user."
  [tag]
  (string/replace (str tag)
                  #"@[0-9a-fA-F]{8}"
                  (string/re-quote-replacement "@<somehex>")))

(defn wrong-tag-from-analyzer [{:keys [asts]}]
  (for [{:keys [op name form env] :as ast} (->> (mapcat ast/nodes asts)
                                                (filter :eastwood/wrong-tag))
        :let [kind (:eastwood/wrong-tag ast)
              [typ tag loc]
              (cond (= kind :eastwood/wrong-tag-on-var)
                    [:wrong-tag-on-var (-> name meta :tag) env]
                    
                    (= op :var)
                    [:var (get ast kind) env]
                    
                    (= op :fn-method)
                    [:fn-method
                     (-> form first meta :tag)
                     (-> form first meta)]
                    
                    :else
                    [nil nil nil])]
        :when typ]
    (merge {:linter :wrong-tag
            :msg
            (case typ
              :wrong-tag-on-var (format "Wrong tag: %s in def of Var: %s"
                                        (replace-variable-tag-part (eval tag))
                                        name)
              :var (format "Wrong tag: %s for form: %s, probably where the Var %s was def'd in namespace %s"
                           (replace-variable-tag-part tag)
                           form
                           (-> ast :var meta :name)
                           (-> ast :var meta :ns))
              :fn-method (format "Tag: %s for return type of fn on arg vector: %s should be Java class name (fully qualified if not in java.lang package)"
                                 (replace-variable-tag-part tag)
                                 (-> form first)))}
           (select-keys loc #{:file :line :column}))))

(defn fq-classname-to-class [cname-str]
  (try
    (Class/forName cname-str)
    (catch ClassNotFoundException e
      nil)))

(defn wrong-tag-clj-1232 [{:keys [asts]}]
  (for [{:keys [op name form env] :as ast} (mapcat ast/nodes asts)
        :when (= op :fn-method)
        :let [tag (-> form first meta :tag)
              loc (-> form first meta)
              ;; *If* this :fn-method is part of a defn, then the
              ;; *'parent' ast should be the one with :op :fn, and its
              ;; *parent ast should be the one with :op :def, and that
              ;; *ast's :meta key should have the metadata on the Var
              ;; *whose value is being made equal to the fn being
              ;; *defined.  That is where the {:private true}
              ;; *key/value pair should be if the Var is marked
              ;; *private.
              grandparent-ast (let [ancestors (:eastwood/ancestors ast)
                                    n (count ancestors)]
                                (if (>= n 2)
                                  (ancestors (- n 2))))
              private-var? (and grandparent-ast
                                (= :def (:op grandparent-ast))
                                (-> grandparent-ast :meta :val :private))]
        :when (and tag
                   (not private-var?)
                   (symbol? tag)
                   (not (contains? default-classname-mapping tag))
                   (nil? (fq-classname-to-class (str tag))))]
    (merge {:linter :wrong-tag
            :msg (format "Tag: %s for return type of fn on arg vector: %s should be fully qualified Java class name, or else it may cause exception if used from another namespace (see CLJ-1232)"
                         tag (-> form first))}
           (select-keys loc #{:file :line :column}))))

(defn wrong-tag [& args]
  (concat (apply wrong-tag-from-analyzer args)
          (apply wrong-tag-clj-1232 args)))
