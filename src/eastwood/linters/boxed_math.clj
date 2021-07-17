(ns eastwood.linters.boxed-math
  (:require [clojure.string :as string]))

(defn linter [{:keys [boxed-math-warnings]} _]
  (->> boxed-math-warnings
       (map (fn [{:keys [post uri line column]}]
              {:linter           :boxed-math
               :uri-or-file-name uri
               :loc              {:line   line
                                  :column column
                                  :file   uri}
               :msg              (-> post
                                     (string/replace #"^ - call: " ""))}))))
