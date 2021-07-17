(ns eastwood.linters.reflection
  (:require
   [clojure.string :as string]))

(defn linter [{:keys [reflection-warnings]} _]
  (->> reflection-warnings
       (map (fn [{:keys [post uri line column]}]
              {:linter           :reflection
               :uri-or-file-name uri
               :loc              {:line   line
                                  :column column
                                  :file   uri}
               :msg              (-> post
                                     (string/replace #"^ - " "")
                                     (string/replace " (target class is unknown)" ""))}))))
