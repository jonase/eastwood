(ns eastwood.linters.typetags
  (:require [clojure.string :as string]
            [eastwood.util :as util]
            [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]))

(def default-classname-mapping
  (ns-map 'eastwood.linters.typetags))

(defn replace-variable-tag-part
  "Wrong tags that were written like (def ^long foo ...) convert to
strings like clojure.core$long@deadbeef, where the deadbeef part is an
hex string that changes from one run to the next.  It is usually 8
digits long in my experience, but does not print leading 0s so can be
shorter.

Replace these strings with @<somehex>, simply to make them consistent
from one run to the next, thus easier to check for in unit tests, and
producing fewer lines of output in 'diff' from one Eastwood run to the
next.  I doubt the exact value of the hex digits has any lasting
significance needed by the user."
  [tag]
  (string/replace (str tag)
                  #"@[0-9a-fA-F]+"
                  (string/re-quote-replacement "@<somehex>")))


(def keys-indicating-wrong-tag #{:eastwood/name-tag
                                 :eastwood/tag
                                 :eastwood/o-tag
                                 :eastwood/return-tag})

(defn has-wrong-tag? [ast]
  (some #(contains? ast %) keys-indicating-wrong-tag))


(defn wrong-tag-from-analyzer [{:keys [asts]}]
  (for [{:keys [op name form env] :as ast} (->> (mapcat ast/nodes asts)
                                                (filter has-wrong-tag?))
        :let [wrong-tag-keys (util/keys-in-map keys-indicating-wrong-tag ast)
;;              _ (do
;;                  (when wrong-tag-keys
;;                    (println (format "jafinger-dbg1: op=%s name=%s wrong-tag-keys=%s"
;;                                     op name wrong-tag-keys))))
              [typ tag loc]
              (cond (= wrong-tag-keys #{:eastwood/name-tag})
                    [:wrong-tag-on-var (-> name meta :tag) env]
                    
                    (and (= wrong-tag-keys #{:eastwood/tag :eastwood/o-tag})
                         (= op :fn-method))
                    [:fn-method
                     (-> form first meta :tag)
                     (-> form first meta)]
                    
                    ;; This set of wrong-tag-keys sometimes occurs for
                    ;; op :local, but since those can be multiple
                    ;; times, one for each use, I am hoping I can make
                    ;; the warnings less redundant by restricting them
                    ;; to the :binding ops (checked for below), and
                    ;; still not lose any important warnings.
                    (and (= wrong-tag-keys #{:eastwood/tag :eastwood/o-tag})
                         (= op :local))
                    [nil nil nil]

                    (and (= wrong-tag-keys #{:eastwood/tag :eastwood/o-tag})
                         (= op :invoke))
                    [:invoke (-> ast :tag) (meta form)]

                    (or (and (= wrong-tag-keys #{:eastwood/tag :eastwood/o-tag})
                             (= op :binding))
                        (and (= wrong-tag-keys #{:eastwood/tag})
                             (= op :local)))
                    [:tag (get ast :tag) (meta form)]

                    (and (= wrong-tag-keys #{:eastwood/return-tag})
                         (= op :var))
                    [:var (get ast :return-tag) env]
                    
                    ;; I have seen this case for this form:
                    ;; (def avlf1 (fn ^{:tag 'LinkedList} [coll] (java.util.LinkedList. coll)))
                    ;; Without warning about this case, I believe one
                    ;; of the other cases already issues a warning for
                    ;; this.
                    (and (= wrong-tag-keys #{:eastwood/return-tag})
                         (#{:def :fn} op))
                    [nil nil nil]
                    ;;[:var (get ast :return-tag) env]

                    :else
                    (do
                      ;; Use this to help detect wrong-tag cases I may
                      ;; be missing completely.
                      (println (format "eastwood-dbg: wrong-tag-from-analyzer: op=%s name=%s wrong-tag-keys=%s env=%s ast="
                                       op name wrong-tag-keys env))
                      (util/pprint-ast-node ast)
                      (flush)
                      (assert false)
                      [nil nil nil]))
;;              _ (do
;;                  (println (format "jafinger-dbg2: typ=%s tag=%s loc=%s"
;;                                   typ tag loc))
;;                  )
              ]
        :when typ]
    (merge {:linter :wrong-tag
            :msg
            (case typ
              :wrong-tag-on-var (format "Wrong tag: %s in def of Var: %s"
                                        (replace-variable-tag-part (eval tag))
                                        name)
              :tag (format "Wrong tag: %s on form: %s"
                           (replace-variable-tag-part tag)
                           form)
              :var (format "Wrong tag: %s for form: %s, probably where the Var %s was def'd in namespace %s"
                           (replace-variable-tag-part tag)
                           form
                           (-> ast :var meta :name)
                           (-> ast :var meta :ns))
              :invoke (format "Tag: %s for return type of function %s should be Java class name (fully qualified if not in java.lang package).  It may be defined in another namespace."
                              (replace-variable-tag-part tag)
                              (-> form first))
              :fn-method (format "Tag: %s for return type of function on arg vector: %s should be Java class name (fully qualified if not in java.lang package)"
                                 (replace-variable-tag-part tag)
                                 (-> form first)))}
           (select-keys loc #{:file :line :column}))))

(defn fq-classname-to-class [cname-str]
  (try
    (Class/forName cname-str)
    (catch ClassNotFoundException e
      nil)))

(defn wrong-tag-clj-1232 [{:keys [asts]}]
  (for [{:keys [op name form env] :as ast} (mapcat ast/nodes asts)
        :when (= op :fn-method)
        :let [tag (-> form first meta :tag)
              loc (-> form first meta)
              ;; *If* this :fn-method is part of a defn, then the
              ;; 'parent' ast should be the one with :op :fn, and its
              ;; parent ast should be the one with :op :def.  That
              ;; 'grandparent' ast's :meta key should have the
              ;; metadata on the Var whose value is being made equal
              ;; to the fn being defined.  That is where the {:private
              ;; true} key/value pair should be if the Var is marked
              ;; private.
              grandparent-ast (let [ancestors (:eastwood/ancestors ast)
                                    n (count ancestors)]
                                (if (>= n 2)
                                  (ancestors (- n 2))))
              private-var? (and grandparent-ast
                                (= :def (:op grandparent-ast))
                                (-> grandparent-ast :meta :val :private))
;;              _ (when tag
;;                  (println (format "jafinger-dbg3: tag=%s op=%s gp-op=%s loc=%s"
;;                                   tag op (:op grandparent-ast) loc))
;;                  )
              ]
        :when (and tag
                   (not private-var?)
                   (symbol? tag)
                   (not (contains? default-classname-mapping tag))
                   (nil? (fq-classname-to-class (str tag))))]
    (merge {:linter :wrong-tag
            :msg (format "Tag: %s for return type of function on arg vector: %s should be fully qualified Java class name, or else it may cause exception if used from another namespace (see CLJ-1232)"
                         tag (-> form first))}
           (select-keys loc #{:file :line :column}))))

(defn wrong-tag [& args]
  (concat (apply wrong-tag-from-analyzer args)
          (apply wrong-tag-clj-1232 args)))
