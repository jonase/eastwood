(ns eastwood.linters.performance
  (:require
   [clojure.string :as string]))

(defn linter [{:keys [performance-warnings]} _]
  (->> performance-warnings
       (map (fn [{:keys [post uri line column kind]}]
              {:linter           :performance
               :kind             kind
               :uri-or-file-name uri
               :loc              {:line   line
                                  :column column
                                  :file   uri}
               :msg              (-> post
                                     (string/replace #"^ - " "")
                                     (string/replace #"^  " " "))}))))
