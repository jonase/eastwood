(ns eastwood.linters.typetags
  (:require [clojure.string :as string]
            [eastwood.util :as util]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]))

(defn wrong-tag [{:keys [asts]}]
  (for [{:keys [name form env] :as ast} (->> (mapcat ast/nodes asts)
                                             (filter :eastwood/wrong-tag))
        :let [kind (:eastwood/wrong-tag ast)
              tag (if (= kind :eastwood/wrong-tag-on-var)
                    (-> name meta :tag)
                    (get ast kind))]
        :when (or (= kind :eastwood/wrong-tag-on-var)
                  (= (:op ast) :var))]
    (merge {:linter :wrong-tag
            :msg (if (= kind :eastwood/wrong-tag-on-var)
                   (format "Wrong tag: %s in def of Var: %s"
                           (eval tag) name)
                   (format "Wrong tag: %s for form: %s, probably where the Var %s was def'd in namespace %s"
                           tag (:form ast) (-> ast :var meta :name)
                           (-> ast :var meta :ns)))}
           (select-keys env #{:line :column :file}))))
