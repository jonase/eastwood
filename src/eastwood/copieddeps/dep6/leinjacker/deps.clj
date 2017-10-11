(ns eastwood.copieddeps.dep6.leinjacker.deps
  "Utilities for managing the dependencies of a Leiningen project."
  {:author "Daniel Solano Gómez"}
  (:use eastwood.copieddeps.dep6.leinjacker.defconstrainedfn))

(defn modifier?
  "Returns a value that evaluates to true if the argument appears to
  be a valid modifier key+value for a dependency."
  [modifier-pair]
  (and (= 2 (count modifier-pair))
       (keyword? (first modifier-pair))))

(defn dep-spec?
  "Returns a value that evaluates to true if the argument appears to be a valid
  dependency specification. Supports managed dependencies with or without modifiers."
  [dep]
  (when (vector? dep)
    (let [[[dep-name version?] modifiers] (split-with (complement keyword?) dep)
          modifier-pairs (partition-all 2 modifiers)]
      (and (symbol? dep-name)
           (or (string? version?) (nil? version?))
           (every? modifier? modifier-pairs)))))

(defn dep?
  "Returns a value that evaluates to true if the argument appears to be a valid
  dependency, either as a dependency name (a symbol) or as a full dependency
  specification."
  [dep]
  (or (symbol? dep)
      (dep-spec? dep)))

(defconstrainedfn dep-name
  "Returns the name of the dependency within a dependency specification.  This
  is the first element in the dependency spec, which must be a symbol."
  [dep-spec]
  [dep-spec? => symbol?]
  (first dep-spec))

(defconstrainedfn has-dep?
  "Returns a value that evaluates to true if the project has the given
  dependency.  dep may either be a full dependency spec or a simple dep name as
  a symbol."
  [project dep]
  [(dep? dep)
   (sequential? (:dependencies project []))]
  (when-let [deps (:dependencies project)]
    (let [name (if (dep-spec? dep)
                 (dep-name dep)
                 dep)]
      (some #(= name (dep-name %)) deps))))

(defconstrainedfn add-if-missing
  "Adds the dependency to the project, but only if it doesn't exist already."
  [project dep-spec]
  [(dep-spec? dep-spec)
   (sequential? (:dependencies project []))
   =>
   (has-dep? % dep-spec)]
  (if (has-dep? project dep-spec)
    project
    (-> project
        (update-in [:dependencies] #(or % []))
        (update-in [:dependencies] conj dep-spec))))

(defconstrainedfn without-clojure
  "Creates a new dependency specification that excludes org.clojure/clojure."
  [dep-spec]
  [dep-spec?  => dep-spec?
                 (= (subvec % (- (count %) 2))
                    [:exclusions ['org.clojure/core]])]
  (conj dep-spec :exclusions ['org.clojure/core]))
