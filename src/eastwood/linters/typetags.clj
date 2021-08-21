(ns eastwood.linters.typetags
  (:require
   [clojure.string :as string]
   [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :as ast]
   [eastwood.passes :as passes]
   [eastwood.util :as util]))

(def default-classname-mapping
  (ns-map 'eastwood.linters.typetags))

(defn replace-variable-tag-part
  "Wrong tags that were written like (def ^long foo ...) convert to
  strings like clojure.core$long@deadbeef, where the deadbeef part is an
  hex string that changes from one run to the next. It is usually 8
  digits long in my experience, but does not print leading 0s so can be
  shorter.

  Replace these strings with @<somehex>, simply to make them consistent
  from one run to the next, thus easier to check for in unit tests, and
  producing fewer lines of output in 'diff' from one Eastwood run to the
  next. I doubt the exact value of the hex digits has any lasting
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

(defn wrong-tag-from-analyzer [{:keys [asts]} opt]
  (keep identity (for [{:keys [op name form env] :as ast} (->> (mapcat ast/nodes asts)
                                                               (filter has-wrong-tag?))
                       :let [wrong-tag-keys (util/keys-in-map keys-indicating-wrong-tag ast)
                             [typ tag loc]
                             (cond (= wrong-tag-keys #{:eastwood/name-tag})
                                   [:wrong-tag-on-var (-> name meta :tag) env]

                                   (and (= wrong-tag-keys #{:eastwood/tag :eastwood/o-tag})
                                        (= op :fn-method))
                                   (let [m (or (passes/has-code-loc? (meta form))
                                               (passes/code-loc (passes/nearest-ast-with-loc ast)))]
                                     [:fn-method (-> ast :eastwood/tag) m])

                                   ;; This set of wrong-tag-keys sometimes occurs for
                                   ;; op :local, but since those can be multiple
                                   ;; times, one for each use, I am hoping I can make
                                   ;; the warnings less redundant by restricting them
                                   ;; to the :binding ops (checked for below), and
                                   ;; still not lose any important warnings.
                                   ;; This can also occur for op :quote, but in the
                                   ;; case that I have seen this occur so far
                                   ;; (Prismatic's Schema library), the wrong tag was
                                   ;; also detected on other 'nearby' ASTs, so
                                   ;; ignoring the op :quote one seems to avoid
                                   ;; duplicate warnings while still reporting the
                                   ;; issue.
                                   (and (= wrong-tag-keys #{:eastwood/tag :eastwood/o-tag})
                                        (#{:local :quote} op))
                                   [nil nil nil]

                                   ;; This started appearing with Clojure 1.8.0 due
                                   ;; to the :rettag changes. See
                                   ;; eastwood.util/get-fn-in-def for some more
                                   ;; details of this change.
                                   (and (= wrong-tag-keys #{:eastwood/return-tag})
                                        (#{:with-meta} op))
                                   [nil nil nil]

                                   (and (= wrong-tag-keys #{:eastwood/tag :eastwood/o-tag})
                                        (= op :invoke))
                                   [:invoke (-> ast :tag) (meta form)]

                                   (or (and (= wrong-tag-keys #{:eastwood/tag :eastwood/o-tag})
                                            (#{:binding :do} op))
                                       (and (= wrong-tag-keys #{:eastwood/tag})
                                            (#{:local :const :var} op)))
                                   [:tag (get ast :tag)
                                    (or (passes/has-code-loc? (meta form))
                                        (passes/code-loc (passes/nearest-ast-with-loc ast)))]

                                   ;; So far I have only seen this case in ztellman's
                                   ;; byte-streams library, and the warnings from a
                                   ;; different case were more clear and closer to
                                   ;; the source of the problem. At least for now,
                                   ;; don't issue warnings for this case.
                                   (and (= wrong-tag-keys #{:eastwood/o-tag})
                                        (#{:local} op))
                                   [nil nil nil]

                                   (and (= wrong-tag-keys #{:eastwood/return-tag})
                                        (= op :var))
                                   [:var (get ast :return-tag) env]

                                   ;; I have seen this case for this form:
                                   ;; (def avlf1 (fn ^{:tag 'LinkedList} [coll] (java.util.LinkedList. coll)))
                                   ;; Without warning about this case, I believe one
                                   ;; of the other cases already issues a warning for
                                   ;; this.
                                   (and (= wrong-tag-keys #{:eastwood/return-tag})
                                        (or (#{:def :fn} op)
                                            (and (= :do op)
                                                 (= [] (:statements ast))
                                                 (= :fn (-> ast :ret :op)))))
                                   [nil nil nil]

                                   :else
                                   [nil nil nil])
                             #_ _ #_ (when (and typ (not (util/has-keys? loc #{:file :line :column})))
                                       ;; XXX look into why this would print something when analyzing the clojurescript project:
                                       (util/pprint-ast-node ast))]
                       :when (and typ
                                  (if-not tag
                                    true
                                    (if-not (sequential? tag)
                                      true
                                      (not (-> tag first #{'Class/forName 'java.lang.Class/forName
                                                           'class `class})))))]
                   (let [warning {:loc loc
                                  :linter :wrong-tag
                                  :kind typ
                                  :wrong-tag {:ast ast}
                                  :msg
                                  (case typ
                                    :wrong-tag-on-var (format "Wrong tag: %s in def of Var: %s."
                                                              (replace-variable-tag-part (eval tag))
                                                              name)
                                    :tag (format "Wrong tag: %s on form: %s."
                                                 (replace-variable-tag-part tag)
                                                 form)
                                    :var (format "Wrong tag: %s for form: %s, probably where the Var %s was def'd in namespace %s."
                                                 (replace-variable-tag-part tag)
                                                 form
                                                 (-> ast :var meta :name)
                                                 (-> ast :var meta :ns))
                                    :invoke (format "Tag: %s for return type of function %s should be Java class name (fully qualified if not in java.lang package). It may be defined in another namespace."
                                                    (replace-variable-tag-part tag)
                                                    (-> form first))
                                    :fn-method (format "Tag: %s for return type of function method: %s should be Java class name (fully qualified if not in java.lang package)."
                                                       (replace-variable-tag-part tag)
                                                       form))}]
                     (when (util/allow-warning warning opt)
                       warning)))))

(defn fq-classname-to-class [cname-str]
  (try
    (Class/forName cname-str)
    (catch ClassNotFoundException _
      nil)))

;; These tags are ok, and the Clojure compiler uses them in a way that
;; can avoid reflection.

(def ok-return-tags '#{bytes shorts ints longs
                       booleans chars
                       floats doubles
                       objects})

(defn wrong-tag-clj-1232 [{:keys [asts]} _opt]
  (for [{:keys [op form] :as ast} (mapcat ast/nodes asts)
        :when (= op :fn-method)
        :let [tag (-> form first meta :tag)]
        :when (and tag
                   (symbol? tag)
                   (not (contains? ok-return-tags tag)))
        :let [loc (-> form first meta)
              ;; *If* this :fn-method is part of a defn, then the
              ;; 'parent' ast should be the one with :op :fn, and its
              ;; parent ast should be the one with :op :def. That
              ;; 'grandparent' ast's :meta key should have the
              ;; metadata on the Var whose value is being made equal
              ;; to the fn being defined. That is where the {:private
              ;; true} key/value pair should be if the Var is marked
              ;; private. Recent changes in t.a(.j) libs made it
              ;; possible it could also be the node one more level up
              ;; the tree that is the :def node, so check both.
              gp-and-ggp-asts (let [ancestors (:eastwood/ancestors ast)]
                                (->> (reverse ancestors)
                                     (take 3)
                                     (drop 1)))
              private-var? (some #(and %
                                       (= :def (:op %))
                                       (-> % :meta :val :private))
                                 gp-and-ggp-asts)]
        :when (and tag
                   (not private-var?)
                   (symbol? tag)
                   (not (contains? default-classname-mapping tag))
                   (nil? (fq-classname-to-class (str tag))))]
    {:loc loc
     :linter :wrong-tag
     :kind :clj-1232
     :msg (format "Tag: %s for return type of function on arg vector: %s should be fully qualified Java class name, or else it may cause exception if used from another namespace. This is only an issue for Clojure 1.7 and earlier. Clojure 1.8 fixes it (CLJ-1232 https://dev.clojure.org/jira/browse/CLJ-1232)."
                  tag (-> form first))}))

(defn wrong-tag [& args]
  (concat (apply wrong-tag-from-analyzer args)
          ;; CLJ-1232 was fixed in Clojure 1.8.0
          (if (util/clojure-1-8-or-later)
            []
            (apply wrong-tag-clj-1232 args))))
