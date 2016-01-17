 ;; Copyright (c) Stuart Sierra, 2012. All rights reserved.  The use
 ;; and distribution terms for this software are covered by the Eclipse
 ;; Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 ;; which can be found in the file epl-v10.html at the root of this
 ;; distribution.  By using this software in any fashion, you are
 ;; agreeing to be bound by the terms of this license.  You must not
 ;; remove this notice, or any other, from this software.

(ns 
 ^{:author "Stuart Sierra",
   :doc "This namespace is DEPRECATED; most functions have been moved to
  other namespaces"
   :deprecated "0.2.1"
   :added "0.1.0"}
 eastwood.copieddeps.dep9.clojure.tools.namespace
 (:require [clojure.java.io :as io])
 (:import (java.io File FileReader BufferedReader PushbackReader
                   InputStreamReader)
          (java.util.jar JarFile JarEntry)))


;;; Finding namespaces in a directory tree

(defn clojure-source-file?
  "DEPRECATED; moved to eastwood.copieddeps.dep9.clojure.tools.namespace.file

  Returns true if file is a normal file with a .clj or .cljc extension."
  {:deprecated "0.2.1"
   :added "0.1.0"}
  [^File file]
  (and (.isFile file)
       (or
         (.endsWith (.getName file) ".clj")
         (.endsWith (.getName file) ".cljc"))))

(defn find-clojure-sources-in-dir
  "DEPRECATED; moved to eastwood.copieddeps.dep9.clojure.tools.namespace.find

  Searches recursively under dir for Clojure source files (.clj, .cljc).
  Returns a sequence of File objects, in breadth-first sort order."
  {:deprecated "0.2.1"
   :added "0.1.0"}
  [^File dir]
  ;; Use sort by absolute path to get breadth-first search.
  (sort-by #(.getAbsolutePath ^File %)
           (filter clojure-source-file? (file-seq dir))))

(defn comment?
  "DEPRECATED; moved to eastwood.copieddeps.dep9.clojure.tools.namespace.parse

  Returns true if form is a (comment ...)"
  {:deprecated "0.2.1"
   :added "0.1.0"}
  [form]
  (and (list? form) (= 'comment (first form))))

(defn ns-decl?
  "DEPRECATED; moved to eastwood.copieddeps.dep9.clojure.tools.namespace.parse

  Returns true if form is a (ns ...) declaration."
  {:deprecated "0.2.1"
   :added "0.1.0"}
  [form]
  (and (list? form) (= 'ns (first form))))

(defn read-ns-decl
  "DEPRECATED; moved to eastwood.copieddeps.dep9.clojure.tools.namespace.parse

  Attempts to read a (ns ...) declaration from rdr, and returns the
  unevaluated form.  Returns nil if read fails or if a ns declaration
  cannot be found.  The ns declaration must be the first Clojure form
  in the file, except for (comment ...)  forms."
  {:deprecated "0.2.1"
   :added "0.1.0"}
  [^PushbackReader rdr]
  (try
   (loop [] (let [form (doto (read rdr) str)]
              (cond
               (ns-decl? form) form
               (comment? form) (recur)
               :else nil)))
       (catch Exception e nil)))

(defn read-file-ns-decl
  "DEPRECATED; moved to eastwood.copieddeps.dep9.clojure.tools.namespace.file

  Attempts to read a (ns ...) declaration from file, and returns the
  unevaluated form.  Returns nil if read fails, or if the first form
  is not a ns declaration."
  {:deprecated "0.2.1"
   :added "0.1.0"}
  [^File file]
  (with-open [rdr (PushbackReader. (BufferedReader. (FileReader. file)))]
    (read-ns-decl rdr)))

(defn find-ns-decls-in-dir
  "DEPRECATED; moved to eastwood.copieddeps.dep9.clojure.tools.namespace.find

  Searches dir recursively for (ns ...) declarations in Clojure
  source files; returns the unevaluated ns declarations."
  {:deprecated "0.2.1"
   :added "0.1.0"}
  [^File dir]
  (filter identity (map read-file-ns-decl (find-clojure-sources-in-dir dir))))

(defn find-namespaces-in-dir
  "DEPRECATED; moved to eastwood.copieddeps.dep9.clojure.tools.namespace.find

  Searches dir recursively for (ns ...) declarations in Clojure
  source files; returns the symbol names of the declared namespaces."
  {:deprecated "0.2.1"
   :added "0.1.0"}
  [^File dir]
  (map second (find-ns-decls-in-dir dir)))

;;; copied from eastwood.copieddeps.dep11.clojure.java.classpath to preserve deprecated API
;;; without an explicit dependency

(defn- loader-classpath [loader]
  (when (instance? java.net.URLClassLoader loader)
    (map
     #(java.io.File. (.toURI ^java.net.URL %))
     (.getURLs ^java.net.URLClassLoader loader))))

(defn- classpath
  ([classloader]
     (distinct
      (mapcat
       loader-classpath
       (take-while
        identity
        (iterate #(.getParent ^ClassLoader %) classloader)))))
  ([] (classpath (clojure.lang.RT/baseLoader))))

(defn- classpath-directories []
  (filter #(.isDirectory ^File %) (classpath)))

(defn- jar-file? [f]
  (let [file (io/file f)]
    (and (.isFile file)
         (or (.endsWith (.getName file) ".jar")
             (.endsWith (.getName file) ".JAR")))))

(defn- classpath-jarfiles []
  (map #(JarFile. ^File %) (filter jar-file? (classpath))))

(defn- filenames-in-jar [^JarFile jar-file]
  (map #(.getName ^JarEntry %)
       (filter #(not (.isDirectory ^JarEntry %))
               (enumeration-seq (.entries jar-file)))))

;;; Finding namespaces in JAR files

(defn clojure-sources-in-jar
  "DEPRECATED; moved to eastwood.copieddeps.dep9.clojure.tools.namespace.find

  Returns a sequence of filenames ending in .clj or .cljc found in the JAR file."
  {:deprecated "0.2.1"
   :added "0.1.0"}
  [^JarFile jar-file]
  (filter #(or (.endsWith ^String % ".clj") (.endsWith ^String % ".cljc"))
          (filenames-in-jar jar-file)))

(defn read-ns-decl-from-jarfile-entry
  "DEPRECATED; moved to eastwood.copieddeps.dep9.clojure.tools.namespace.find

  Attempts to read a (ns ...) declaration from the named entry in the
  JAR file, and returns the unevaluated form.  Returns nil if the read
  fails, or if the first form is not a ns declaration."
  {:deprecated "0.2.1"
   :added "0.1.0"}
  [^JarFile jarfile ^String entry-name]
  (with-open [rdr (PushbackReader.
                   (BufferedReader.
                    (InputStreamReader.
                     (.getInputStream jarfile (.getEntry jarfile entry-name)))))]
    (read-ns-decl rdr)))

(defn find-ns-decls-in-jarfile
  "DEPRECATED; moved to eastwood.copieddeps.dep9.clojure.tools.namespace.find

  Searches the JAR file for Clojure source files containing (ns ...)
  declarations; returns the unevaluated ns declarations."
  {:deprecated "0.2.1"
   :added "0.1.0"}
  [^JarFile jarfile]
  (filter identity
          (map #(read-ns-decl-from-jarfile-entry jarfile %)
               (clojure-sources-in-jar jarfile))))

(defn find-namespaces-in-jarfile
  "DEPRECATED; moved to eastwood.copieddeps.dep9.clojure.tools.namespace.find

  Searches the JAR file for Clojure source files containing (ns ...)
  declarations.  Returns a sequence of the symbol names of the
  declared namespaces."
  {:deprecated "0.2.1"
   :added "0.1.0"}
  [^JarFile jarfile]
  (map second (find-ns-decls-in-jarfile jarfile)))

;;; Finding namespaces anywhere on CLASSPATH

(defn find-ns-decls-on-classpath
  "DEPRECATED; use eastwood.copieddeps.dep9.clojure.tools.namespace.find/find-ns-decls
  and eastwood.copieddeps.dep11.clojure.java.classpath/classpath from
  http://github.com/clojure/java.classpath

  Searches CLASSPATH (both directories and JAR files) for Clojure
  source files containing (ns ...) declarations. Returns a sequence of
  the unevaluated ns declaration forms."
  {:deprecated "0.2.1"
   :added "0.1.0"}
  []
  (concat
   (mapcat find-ns-decls-in-dir (classpath-directories))
   (mapcat find-ns-decls-in-jarfile (classpath-jarfiles))))

(defn find-namespaces-on-classpath
  "DEPRECATED; use eastwood.copieddeps.dep9.clojure.tools.namespace.find/find-namespaces
  and eastwood.copieddeps.dep11.clojure.java.classpath/classpath from
  http://github.com/clojure/java.classpath

  Searches CLASSPATH (both directories and JAR files) for Clojure
  source files containing (ns ...) declarations.  Returns a sequence
  of the symbol names of the declared namespaces."
  {:deprecated "0.2.1"
   :added "0.1.0"}
  []
  (map second (find-ns-decls-on-classpath)))
