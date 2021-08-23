(ns eastwood.util.ns
  (:require
   [clojure.string :as string]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.dependency :as dep]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.file :as file]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.parse :as parse]
   [eastwood.copieddeps.dep9.clojure.tools.namespace.track :as track]))

(defn ns-name->resource-path [ns-name extension]
  (-> ns-name
      str
      munge
      (string/replace "." "/")
      (str extension)))

(defn resource-path->filenames [resource-path]
  (->> (-> (Thread/currentThread)
           (.getContextClassLoader)
           (.getResources resource-path))
       (enumeration-seq)
       (distinct)
       (mapv str)))

(defn files-and-deps [project-namespaces]
  (->> project-namespaces
       (reduce (fn [m n]
                 (let [file (or (some-> (ns-name->resource-path n ".clj")
                                        resource-path->filenames
                                        first)
                                (some-> (ns-name->resource-path n ".cljc")
                                        resource-path->filenames
                                        first))]
                   (if-let [decl (some-> file
                                         (file/*read-file-ns-decl* nil))]
                     (let [deps (parse/deps-from-ns-decl decl)
                           name (parse/name-from-ns-decl decl)]
                       (-> m
                           (assoc-in [:depmap name] deps)))
                     m)))
               {})))

(defn add-files [tracker project-namespaces]
  (let [{:keys [depmap]} (files-and-deps project-namespaces)]
    (-> tracker
        (track/add depmap))))

(defn build-tracker [project-namespaces]
  (add-files (track/tracker) project-namespaces))

;; Sort namespaces topographically. This increases the likelihood of evaluating defprotocols in the right order.
(defn topo-sort [project-namespaces namespaces]
  {:post [(= (set namespaces)
             (set %))]}
  (let [inside-project (->> (build-tracker project-namespaces)
                            ::track/deps
                            dep/topo-sort
                            (filterv (set namespaces)))
        outside-project (->> namespaces
                             (remove (set inside-project))
                             vec)]
    (into inside-project outside-project)))
