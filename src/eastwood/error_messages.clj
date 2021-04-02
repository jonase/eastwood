(ns eastwood.error-messages
  (:require
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [eastwood.util :as util]))

(def eastwood-url "https://github.com/jonase/eastwood")

(defn maybe-unqualified-java-class-name? [x]
  (if-not (or (symbol? x) (string? x))
    false
    (let [^String x (if (symbol? x) (str x) x)]
      (and (>= (count x) 1)
           (== (.indexOf x ".") -1)   ; no dots
           (Character/isJavaIdentifierStart ^Character (nth x 0))
           (= (subs x 0 1)
              (str/upper-case (subs x 0 1)))))))

(defn misplaced-primitive-tag? [x]
  (condp = x
    clojure.core/byte    {:prim-name "byte",    :supported-as-ret-hint false}
    clojure.core/short   {:prim-name "short",   :supported-as-ret-hint false}
    clojure.core/int     {:prim-name "int",     :supported-as-ret-hint false}
    clojure.core/long    {:prim-name "long",    :supported-as-ret-hint true}
    clojure.core/boolean {:prim-name "boolean", :supported-as-ret-hint false}
    clojure.core/char    {:prim-name "char",    :supported-as-ret-hint false}
    clojure.core/float   {:prim-name "float",   :supported-as-ret-hint false}
    clojure.core/double  {:prim-name "double",  :supported-as-ret-hint true}
    nil))

;; this is a terrible hack to
;; stop everyting from printing all over the place
(defn string-builder []
  (let [strings (atom [])]
    [strings (fn [str]
               (swap! strings conj str))]))

(defn print-ex-data-details [ns-sym ^Throwable exc]
  (let [[strings error-cb] (string-builder)
        dat (ex-data exc)
        msg (.getMessage exc)]
    ;; Print useful info about the exception so we might more
    ;; quickly learn how to enhance it.
    (error-cb (format "Got exception with extra ex-data:"))
    (error-cb (format "    msg='%s'" msg))
    (error-cb (format "    (keys dat)=%s" (keys dat)))
    (when (contains? dat :ast)
      (error-cb (format "     (:op ast)=%s" (-> dat :ast :op)))
      (when (contains? (:ast dat) :form)
        (error-cb (format "    (class (-> dat :ast :form))=%s (-> dat :ast :form)="
                          (class (-> dat :ast :form))))
        (error-cb (with-out-str (util/pprint-form (-> dat :ast :form)))))
      (error-cb (with-out-str (util/pprint-form (-> dat :ast)))))
    (when (contains? dat :form)
      (error-cb (format "    (:form dat)="))
      (error-cb (with-out-str (util/pprint-form (:form dat)))))
    (error-cb (with-out-str (util/pst exc nil)))
    @strings))

(defn handle-values-of-env [ns-sym ^Throwable exc]
  (let [[strings error-cb] (string-builder)
        dat (ex-data exc)
        {:keys [form]} dat]
    (error-cb (format "Eastwood cannot analyze code that uses the values of &env in a macro expansion."))
    (error-cb (format "See https://github.com/jonase/eastwood#explicit-use-of-clojure-environment-env"))
    {:info :show-more-details
     :msgs @strings}))

(defn handle-bad-dot-form [ns-sym  ^Throwable exc]
  (let [[strings error-cb] (string-builder)
        dat (ex-data exc)
        {:keys [form]} dat]
    (error-cb (format "Java interop calls should be of the form TBD, but found this instead (line %s):"
                      (-> form first meta :line)))
    (error-cb (with-out-str
                ;; TBD: Replace this binding with util/pprint-form call?
                (binding [*print-level* 7
                          *print-length* 50]
                  (pp/pprint form))))
    {:info :no-more-details-needed
     :msgs @strings}))

(defn handle-bad-tag [ns-sym ^Throwable exc]
  (let [[strings error-cb] (string-builder)
        dat (ex-data exc)
        ast (:ast dat)]
    (cond
      (#{:var :invoke :const} (:op ast))
      (let [form (:form ast)
            form (if (= (:op ast) :invoke)
                   (first form)
                   form)
            tag (or (-> form meta :tag)
                    (:tag ast))]
        (error-cb (format "A function, macro, protocol method, var, etc. named %s has been used here:"
                          form))
        (error-cb (with-out-str (util/pprint-form (meta form))))
        (error-cb (format "Wherever it is defined, or where it is called, it has a type of %s"
                          tag))
        (cond
          (maybe-unqualified-java-class-name? tag)
          (do
            (error-cb (format
                       "This appears to be a Java class name with no package path.
Library tools.analyzer, on which Eastwood relies, cannot analyze such files.
If this definition is easy for you to change, we recommend you prepend it with
a full package path name, e.g. java.net.URI
Otherwise import the class by adding a line like this to your ns statement:
    (:import (java.net URI))"))
            {:info :no-more-details-needed
             :msgs @strings})

          (misplaced-primitive-tag? tag)
          (let [{:keys [prim-name supported-as-ret-hint]} (misplaced-primitive-tag? tag)
                form (if (var? form)
                       (name (.sym ^clojure.lang.Var form))
                       form)
                good-prim-name (if supported-as-ret-hint
                                 prim-name
                                 "long")]
            (error-cb (format
                       "It has probably been defined with a primitive return type tag on the var name,
like this:
    (defn ^%s %s [args] ...)" prim-name form))
            (error-cb (format
                       "Clojure 1.5.1 and 1.6.0 do not handle such tags as you probably expect.
They silently treat this as a tag of the *function* named clojure.core/%s"
                       prim-name))
            (when-not supported-as-ret-hint
              (error-cb (format
                         "Also, it only supports return type hints of long and double, not %s" prim-name)))
            (error-cb (format
                       "Library tools.analyzer, on which Eastwood relies, cannot analyze such files.
If you wish the function to have a primitive return type, this is only
supported for types long and double, and the type tag must be given just
before the argument vector, like this:
    (defn %s ^%s [args] ...)" form good-prim-name))
            (error-cb (format
                       "or if there are multiple arities defined, like this:
    (defn %s (^%s [arg1] ...) (^%s [arg1 arg2] ...))" form good-prim-name good-prim-name))
            (error-cb (format
                       "If you wish to use a primitive type tag on the Var name, Clojure will
only use that if the function is called and its return value is used
as an argument in a Java interop call.  In such situations, the type
tag can help choose a Java method and often avoid reflection.  If that
is what you want, you must specify the tag like so:
    (defn ^{:tag '%s} %s [args] ...)" prim-name form))
            {:info :no-more-details-needed
             :msgs @strings})
          :else
          (do
            (error-cb (format "dbgx tag=%s (class tag)=%s (str tag)='%s' boolean?=%s long?=%s"
                              tag
                              (class tag)
                              (str tag)
                              (= tag clojure.core/boolean)
                              (= tag clojure.core/long)))
            {:info :show-more-details
             :msgs @strings})))
      (#{:local :binding} (:op ast))
      (let [form (:form ast)
            tag (-> form meta :tag)]
        (error-cb (format "Local name '%s' has been given a type tag '%s' here:"
                          form tag))
        (error-cb (with-out-str (util/pprint-form (meta tag))))
        (cond
          (maybe-unqualified-java-class-name? tag)
          (do
            (error-cb (format
                       "This appears to be a Java class name with no package path.
Library tools.analyzer, on which Eastwood relies, cannot analyze such files.
Either prepend it with a full package path name, e.g. java.net.URI
Otherwise, import the Java class, e.g. add a line like this to the ns statement:
    (:import (java.net URI))"))
            {:info :no-more-details-needed
             :msgs @strings})

          (symbol? tag)
          (do
            (error-cb (format
                       "This is a symbol, but does not appear to be a Java class.  Whatever it
is, library tools.analyzer, on which Eastwood relies, cannot analyze
such files.

Cases like this have been seen in some Clojure code that used the
library test.generative.  That library uses tag metadata in an unusual
way that might be changed to avoid this.  See
http://dev.clojure.org/jira/browse/TGEN-5 for details if you are
curious.

If you are not using test.generative, and are able to provide the code
that you used that gives this error to the Eastwood developers for
further investigation, please file an issue on the Eastwood Github
page at %s"
                       eastwood-url))
            {:info :no-more-details-needed
             :msgs @strings})

          (sequential? tag)
          (do
            (error-cb (format
                       "This appears to be a Clojure form to be evaluated.
Library tools.analyzer, on which Eastwood relies, cannot analyze such files.

If you have this expression in your source code, it is recommended to
replace it with a constant type tag if you can, or create an issue on
the Eastwood project Github page with details of your situation for
possible future enhancement to Eastwood: %s

If you do not see any expression like this in your source code, cases
like this have been seen in programs that used the library
test.generative.  That library uses tag metadata in an unusual way
that might be changed to avoid this.  See
http://dev.clojure.org/jira/browse/TGEN-5 for details if you are
curious." eastwood-url))
            {:info :no-more-details-needed
             :msgs @strings})

          :else
          (do
            (error-cb (format "dbgx for case :op %s tag=%s (class form)=%s (sequential? form)=%s form="
                              (:op ast) tag (class form) (sequential? form)))
            (error-cb (with-out-str (util/pprint-form form)))
            {:info :show-more-details
             :msgs @strings})))

      :else
      (do
        {:info :show-more-details
         :msgs (print-ex-data-details ns-sym exc)}))))

(defn format-exception [ns-sym ^Throwable exc]
  (let [dat (ex-data exc)
        msg (or (.getMessage exc) "")]
    (cond
      (re-find #" cannot be cast to clojure\.lang\.Compiler\$LocalBinding" msg)
      (handle-values-of-env ns-sym exc)

      (and (re-find #"method name must be a symbol, had:" msg)
           (contains? dat :form))
      (handle-bad-dot-form ns-sym exc)

      (re-find #"Class not found: " msg)
      (handle-bad-tag ns-sym exc)

      :else
      {:msgs (if dat
               (print-ex-data-details ns-sym exc)
               [(with-out-str (util/pst exc nil))])
       :info :show-more-details})))

(defn report-analyzer-exception [exception exception-phase exception-form ns-sym]
  (let [[strings error-cb] (string-builder)]
    (error-cb (str "Exception thrown during phase " exception-phase
                   " of linting namespace " ns-sym))
    (let [{:keys [msgs info]} (format-exception ns-sym exception)]
      (swap! strings into msgs)
      (when (= info :show-more-details)
        (error-cb "\nThe following form was being processed during the exception:")
        ;; TBD: Replace this binding with util/pprint-form variation
        ;; that does not print metadata?
        (error-cb (with-out-str (binding [*print-level* 7
                                          *print-length* 50]
                                  (pp/pprint exception-form))))
        (error-cb "\nShown again with metadata for debugging (some metadata elided for brevity):")
        (error-cb (with-out-str (util/pprint-form exception-form)))))
    (error-cb
     (str "\nAn exception was thrown while analyzing namespace " ns-sym "
Lint results may be incomplete.  If there are compilation errors in
your code, try fixing those.  If not, check above for info on the
exception."))
    {:exception exception
     :msgs @strings}))

(defmulti error-msg
  "Given a map describing an Eastwood error result, which should
always have at least the keys :err and :err-data, return a string
describing the error."
  :err)

(defmethod error-msg :default [err-info]
  (str "Unknown error: " err-info))

(defmethod error-msg :unknown-ns-keywords [err-info]
  (let [{:keys [for-option unknown-ns-keywords allowed-ns-keywords]}
        (:err-data err-info)]
    (with-out-str
      (println (format "The following keywords appeared in the namespaces specified after %s :"
                       for-option))
      (println (format "    %s" (seq unknown-ns-keywords)))
      (println (format "The only keywords allowed in this list of namespaces are: %s"
                       (seq allowed-ns-keywords))))))

(defmethod error-msg :namespace-filename-mismatch [err-info]
  (let [{:keys [mismatches]} (:err-data err-info)]
    (with-out-str
      (println "The following file(s) contain ns forms with namespaces that do not correspond
with their file names:")
      (doseq [[fname {:keys [dir namespace recommended-fnames recommended-namespace]}]
              mismatches]
        (println (format "Directory: %s" dir))
        (println (format "    File                   : %s" fname))
        (println (format "    has namespace          : %s" namespace))
        (if (= namespace recommended-namespace)
          ;; Give somewhat clearer message in this case
          (println (format "    should be in file(s)   : %s"
                           (str/join "\n                             "
                                     recommended-fnames)))
          (do
            (println (format "    should have namespace  : %s"
                             recommended-namespace))
            (println (format "    or should be in file(s): %s"
                             (str/join "\n                             "
                                       recommended-fnames))))))
      (println "
No other linting checks will be performed until these problems have
been corrected.

The 'should have namespace' and 'should be in file' messages above are
merely suggestions.  It may be better in your case to rename both the
file and namespace to avoid name collisions."))))

(defmethod error-msg :unknown-linter [err-info]
  (let [{:keys [unknown-linters known-linters]} (:err-data err-info)]
    (with-out-str
      (println (format "The following requested or excluded linters are unknown: %s"
                       (seq unknown-linters)))
      (println (format "Known linters are: %s"
                       (seq (sort known-linters)))))))

(defmethod error-msg :exception-thrown [err-info]
  (let [{:keys [namespaces-left last-namespace]} (:err-data err-info)]
    ;; Don't report that we stopped analyzing early if we stop on the
    ;; last namespace (it is especially bad form to print the long
    ;; message if only one namespace was being linted).
    (if (< 0 namespaces-left)
      (format "
Stopped analyzing namespaces after %s
due to exception thrown.  %d namespaces left unanalyzed.

If you wish to force continuation of linting after an exception in one
namespace, make the option map key :continue-on-exception have the
value true.

WARNING: This can cause exceptions to be thrown while analyzing later
namespaces that would not otherwise occur.  For example, if a function
is defined in the namespace where the first exception occurs, after
the exception, it will never be evaluated.  If the function is then
used in namespaces analyzed later, it will be undefined, causing
error.
"
              last-namespace
              namespaces-left)

      "
Exception thrown while analyzing last namespace.
")))
