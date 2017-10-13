(ns eastwood.copieddeps.dep6.leinjacker.lein-runner
  "Functions to locate and run the 'lein' command for either lein generation."
  (:require [clojure.java.io    :as io]
            [clojure.java.shell :as shell]))

(defn- clean-lein-env
  "Gets a copy of the system environment tha removes Leiningen environment variables."
  []
  (dissoc (into {} (System/getenv))
          "CLASSPATH" "LEIN_JVM_OPTS"))

(defn- memoize-to-file
  "Memoizes the result if not-found-fn under key in filename."
  [filename key not-found-fn]
  (let [cache-file (io/file filename)
        cache (if (.exists cache-file) (read-string (slurp cache-file)) {})]
    (or (cache key)
        (let [cmd (not-found-fn key)]
          (spit cache-file (prn-str (assoc cache key cmd)))
          cmd))))

(defn- find-lein-cmd*
  "Does the work of looking up the lein command without any memoization options."
  [generation]
  (if-let [cmd (System/getenv (str "LEIN" generation "_CMD"))]
    cmd
    (if-let [cmd (some
                  (fn [cmd]
                    (try
                      (if (.contains (:out (shell/sh cmd "version" :env (clean-lein-env)))
                                     (str "Leiningen " generation "."))
                        cmd)
                      (catch java.io.IOException _)))
                  ["lein" (str "lein" generation)])]
      cmd
      (throw (IllegalStateException.
              (format "Unable to find Leiningen %s in the path as lein or lein %s. Please make sure it is installed and in your path under one of those names, or set LEIN%s_CMD."
                      generation generation generation))))))

(defn find-lein-cmd
  "Attempts to locate the lein command for the given command on the path.
Looks for: lein, lein<generation>. If memoize? isn't provided or is true,
the results are cached in ./.lein-commands."
  ([generation]
     (find-lein-cmd generation true))
  ([generation memoize?]
     (if memoize?
       (memoize-to-file ".lein-commands" generation find-lein-cmd*)
       (find-lein-cmd* generation))))

(defn run-lein
  "Runs lein for the given generation with args.
:dir and :env options may be specified with kwargs. Returns the result from sh."
  [generation & args]
  (let [[real-args {:as opts}] (split-with string? args)]
    (apply shell/sh (find-lein-cmd generation)
                      (conj (vec real-args)
                            :dir (:dir opts)
                            :env (assoc (merge (clean-lein-env) (:env opts))
                                   "LEIN_GENERATION" generation)))))

