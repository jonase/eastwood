(ns eastwood.linters.typetags
  (:require [clojure.string :as string]
            [eastwood.util :as util]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]))

(defn wrong-tag [{:keys [asts]}]
  (for [{:keys [name form env]} (->> (mapcat ast/nodes asts)
                                     (filter :eastwood/wrong-tag))
        :let [tag (-> name meta :tag)]]
    (merge {:linter :wrong-tag
            :msg (format "Wrong tag: %s in def: %s"
                         (eval tag) name)}
           (select-keys env #{:line :column :file}))))
