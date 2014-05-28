(ns eastwood.copieddeps.dep6.leinjacker.utils
  "Useful utilities for plugins supporting lein 1 and 2."
  {:author "Daniel Solano Gómez"}
  (:require [clojure.set :as set])
  (:use eastwood.copieddeps.dep6.leinjacker.defconstrainedfn))

(defconstrainedfn try-resolve
  "Attempts to resolve the given namespace-qualified symbol.  If successful,
  returns the resolved symbol.  Otherwise, returns nil."
  [sym]
  [symbol? namespace]
  (let [ns-sym (symbol (namespace sym))]
    (try (require ns-sym)
      (resolve sym)
      (catch java.io.FileNotFoundException _))))

(defconstrainedfn try-resolve-any
  "Attempts to resolve the given namespace-qualified symbols. Returns the
   first successfully resolved symbol, or throws an IllegalArgumentException
   if none of the given symbols resolve."
  [& syms]
  [(every? symbol? syms)]
  (if-let [sym (try-resolve (first syms))]
    sym
    (if-let [tail (seq (rest syms))]
      (apply try-resolve-any tail)
      (throw (IllegalArgumentException.
              "Unable to resolve a valid symbol from the given list.")))))

(defn lein-generation
  "Returns 1 if called under Leiningen 1.x, 2 if called under Leiningen 2.x."
  []
  (if (try-resolve 'leiningen.core.main/-main) 2 1))

(defn lein-home
  "Returns the leiningen home directory (typically ~/.lein/). This function
   abstracts away the differences in calling the leiningen-home function between
   Leiningen 1.x and 2.x."
  []
  ((try-resolve-any
    'leiningen.util.paths/leiningen-home   ;; lein1
    'leiningen.core.user/leiningen-home))) ;; lein2

(let [read-project-fn (try-resolve-any
                       'leiningen.core/read-project   ;; lein1
                       'leiningen.core.project/read)] ;; lein2
  (defn read-lein-project
    "Read Leiningen project map out of file, which defaults to project.clj.
     This function abstracts away the differences in calling project read
     function between Leiningen 1.x and 2.x. The profiles argument is ignored
     under Leiningen 1.x."
    ([file profiles]
       (if (= 1 (lein-generation))
         (read-lein-project file)
         (read-project-fn file profiles)))
    ([file]
       (read-project-fn file))
    ([]
       (read-project-fn))))

(defn get-classpath
  "Gets the classpath for a given project."
  [project]
  (try-resolve-any 'leiningen.core.classpath/get-classpath
                   'leiningen.classpath/get-classpath) project)

(defn abort
  "Signal a fatal error and print msg to stderr."
  [& msg]
  (let [abort (try-resolve-any 'leiningen.core/abort        ; lein1
                               'leiningen.core.main/abort)] ; lein2
    (apply abort msg)))

(defn- profile-key-merge
  [result latter]
  (cond (and (map? result) (map? latter))
        (merge-with profile-key-merge result latter)
        (and (set? result) (set? latter))
        (set/union result latter)
        (and (coll? result) (coll? latter))
        (concat result latter)
        (= (class result) (class latter))
        latter
        :else
        (doto latter (println "has a type mismatch merging profiles."))))

(defn- lein2-merge-profile
  [project other]
  (let [profile-name (-> (gensym) name keyword)
        add-profiles (try-resolve 'leiningen.core.project/add-profiles)
        merge-profiles (try-resolve 'leiningen.core.project/merge-profiles)
        added-profile (add-profiles project
                                    {profile-name other})
        merged-profile (merge-profiles added-profile [profile-name])]
    merged-profile))


(defn merge-projects
  "Takes an existing project map and another map and merges
  the new map into the project map in such a way that it will
  be preserved through Lein2's profile munging, and also work
  in Lein1. This also allows for Lein2's profile merging logic to be used in
  Lein1."
  [project other]
  (let [generation (lein-generation)
        merge-fn (if (= 1 generation)
                   #(merge-with profile-key-merge %1 %2)
                   lein2-merge-profile)]
    (merge-fn project other)))
