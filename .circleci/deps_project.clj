;; A generic project.clj able to parse most deps.edn files out there.

(require '[clojure.string :as string])

(defn deep-merge
  {:license "Copyright © 2019 James Reeves

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version."}
  ([])
  ([a] a)
  ([a b]
   (when (or a b)
     (letfn [(merge-entry [m e]
               (let [k  (key e)
                     v' (val e)]
                 (if (contains? m k)
                   (assoc m k (let [v (get m k)]
                                (cond
                                  (and (map? v) (map? v'))   (deep-merge v v')
                                  (and (coll? v) (coll? v')) (into (empty v)
                                                                   (distinct (into v v')))
                                  true                       (do
                                                               (assert (not (and v v')))
                                                               (or v v')))))
                   (assoc m k v'))))]
       (->> b
            seq
            (reduce merge-entry (or a {}))))))
  ([a b & more]
   (reduce deep-merge (or a {}) (cons b more))))

(def repo-mapping (atom {}))

(def git-hosts (atom #{}))

(defn process-dep-entry! [{:keys [jars-atom subprojects-atom]}
                          [k {:keys [mvn/version sha exclusions git/url local/root]}]]
  {:pre [@jars-atom @subprojects-atom]}
  (when url
    (let [{:keys [host path]} (-> url java.net.URI. bean)
          [gh-org gh-project] (-> path
                                  (string/replace #"^/" "")
                                  (string/replace #"\.git$" "")
                                  (string/split #"/"))]
      (swap! repo-mapping assoc k {:coordinates (symbol (str gh-org "/" gh-project))})
      (swap! git-hosts conj host)))
  (if-not root
    [k (or version sha) :exclusions exclusions]
    (do
      (if (string/ends-with? root ".jar")
        (swap! jars-atom conj root)
        (let [f (java.io.File. root "deps.edn")]
          (assert (-> f .exists)
                  (str "Expected " root " to denote an existing deps.edn file"))
          (swap! subprojects-atom conj (.getCanonicalPath f))))
      nil)))

(defn prefix [filename item]
  (-> filename java.io.File. .getParent (java.io.File. item) .getCanonicalPath))

(defn add-jars [m jars-atom sub? deps-edn-filename]
  {:pre [@jars-atom]}
  (cond-> m
    (seq @jars-atom) (update :resource-paths (fn [v]
                                               (cond->> @jars-atom
                                                 true (into (or v []))
                                                 sub? (mapv (partial prefix deps-edn-filename)))))))

(declare add-subprojects)

(defn process-profiles [aliases deps-edn-filename root-deps-edn-filename]
  (->> (for [[k {:keys [override-deps extra-deps extra-paths replace-paths]}] aliases
             :let [jars-atom (atom #{})
                   subprojects-atom (atom #{} :validator (fn [v]
                                                           (not (contains? v deps-edn-filename))))
                   sub? (not= deps-edn-filename root-deps-edn-filename)
                   sot (cond->> [[] extra-paths replace-paths]
                         true (reduce into)
                         true distinct
                         sub? (map (partial prefix deps-edn-filename))
                         true vec)]]
         [k (-> {:dependencies (->> [override-deps extra-deps]
                                    (keep (partial map (partial process-dep-entry! {:jars-atom        jars-atom
                                                                                    :subprojects-atom subprojects-atom})))
                                    (apply concat)
                                    distinct
                                    vec)
                 :source-paths sot
                 :test-paths   sot}
                (add-jars jars-atom sub? deps-edn-filename)
                (add-subprojects subprojects-atom deps-edn-filename))])
       (into {})))

(defn parse-deps-edn [deps-edn-filename root-deps-edn-filename]
  (let [{:keys [aliases deps paths]} (clojure.edn/read-string (slurp deps-edn-filename))
        profiles (process-profiles aliases deps-edn-filename root-deps-edn-filename)
        jars-atom (atom #{})
        subprojects-atom (atom #{}
                               :validator (fn [v]
                                            (not (contains? v root-deps-edn-filename))))
        dependencies (->> deps
                          (keep (partial process-dep-entry! {:jars-atom        jars-atom
                                                             :subprojects-atom subprojects-atom}))
                          (vec))
        sub? (not= deps-edn-filename root-deps-edn-filename)
        sot (cond->> paths
              sub? (mapv (partial prefix deps-edn-filename)))]
    (-> {:dependencies   dependencies
         :profiles       (clojure.set/rename-keys profiles {:test-common :test})
         :source-paths   sot
         :test-paths     sot
         :resource-paths (cond->> @jars-atom
                           sub? (map (partial prefix deps-edn-filename))
                           true vec)}
        (add-subprojects subprojects-atom root-deps-edn-filename))))

(defn add-subprojects [m subprojects-atom root-deps-edn-filename]
  (->> @subprojects-atom
       (reduce (fn [v subproject]
                 (deep-merge v
                             (parse-deps-edn subproject root-deps-edn-filename)))
               m)))

(let [f (-> "deps.edn" java.io.File. .getCanonicalPath)
      {:keys [profiles dependencies resource-paths source-paths]} (parse-deps-edn f f)]
  (defproject _ "0.0.1"
    :source-paths ~source-paths
    :resource-paths ~resource-paths
    :dependencies ~dependencies
    :profiles ~profiles
    :plugins [[reifyhealth/lein-git-down "0.4.0"]]
    :middleware [lein-git-down.plugin/inject-properties]
    :git-down ~(deref repo-mapping)
    :repositories ~(->> @git-hosts
                        (map (fn [r]
                               (let [n (-> r (string/split #"\.") first)
                                     u (str "git://" r)]
                                 [[(str "public-" n) {:url u}]
                                  [(str "private-" n) {:url u :protocol :ssh}]])))
                        (apply concat)
                        vec)))
