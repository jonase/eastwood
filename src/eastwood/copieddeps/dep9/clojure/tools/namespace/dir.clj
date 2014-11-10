;; Copyright (c) Stuart Sierra, 2012. All rights reserved. The use and
;; distribution terms for this software are covered by the Eclipse
;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this
;; distribution. By using this software in any fashion, you are
;; agreeing to be bound by the terms of this license. You must not
;; remove this notice, or any other, from this software.

(ns ^{:author "Stuart Sierra"
      :doc "Track namespace dependencies and changes by monitoring
  file-modification timestamps"}
  eastwood.copieddeps.dep9.clojure.tools.namespace.dir
  (:require [eastwood.copieddeps.dep9.clojure.tools.namespace.file :as file]
            [eastwood.copieddeps.dep9.clojure.tools.namespace.track :as track]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as string])
  (:import (java.io File) (java.util.regex Pattern)))

(defn- find-files [dirs]
  (let [all-files (->> dirs
                       (map io/file)
                       (filter #(.exists ^File %))
                       (mapcat file-seq)
                       (remove #(.isDirectory ^File %)))
        clojure-files (->> all-files
                           (filter file/clojure-file?)
                           (map #(.getCanonicalFile ^File %)))
        non-clojure-files (->> all-files
                               (remove file/clojure-file?)
                               (map #(.getCanonicalFile ^File %)))]
    {:clojure-files clojure-files
     :non-clojure-files non-clojure-files}))

(defn- modified-files [tracker files]
  (filter #(< (::time tracker 0) (.lastModified ^File %)) files))

(defn- deleted-files [tracker files]
  (set/difference (::files tracker #{}) (set files)))

(defn- update-files [tracker deleted modified]
  (let [now (System/currentTimeMillis)]
    (-> tracker
        (update-in [::files] #(if % (apply disj % deleted) #{}))
        (file/remove-files deleted)
        (update-in [::files] into modified)
        (file/add-files modified)
        (assoc ::time now))))

(defn- dirs-on-classpath []
  (filter #(.isDirectory ^File %)
          (map #(File. ^String %)
               (string/split
                (System/getProperty "java.class.path")
                (Pattern/compile (Pattern/quote File/pathSeparator))))))

(defn scan
  "Scans directories for files which have changed since the last time
  'scan' was run; update the dependency tracker with
  new/changed/deleted files.

  If no dirs given, defaults to all directories on the classpath."
  [tracker & dirs]
  (let [ds (or (seq dirs) (dirs-on-classpath))
        {:keys [clojure-files non-clojure-files]} (find-files ds)
        files clojure-files
        deleted (seq (deleted-files tracker files))
        modified (seq (modified-files tracker files))]
    (assoc (if (or deleted modified)
             (update-files tracker deleted modified)
             tracker)
      ::non-clojure-files non-clojure-files)))

(defn scan-all
  "Scans directories for all Clojure source files and updates the
  dependency tracker to reload files. If no dirs given, defaults to
  all directories on the classpath."
  [tracker & dirs]
  (let [ds (or (seq dirs) (dirs-on-classpath))
        {:keys [clojure-files non-clojure-files]} (find-files ds)
        files clojure-files
        deleted (seq (deleted-files tracker files))]
    (assoc (update-files tracker deleted files)
      ::non-clojure-files non-clojure-files)))
