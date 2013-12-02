;; This file was copied from tools.analyzer.jvm library's namespace
;; clojure.tools.analyze.jvm, and then modified to try to remove some
;; of the validation that it normally does.  This was done in hopes of
;; allowing the analyzer to work without exceptions on more Clojure
;; source files.

(ns eastwood.jvm
  (:refer-clojure :exclude [macroexpand-1 macroexpand])
  (:require [clojure.tools.analyzer
             :as ana
             :refer [analyze analyze-fn-method]
             :rename {analyze -analyze}]
            [clojure.tools.analyzer.passes :refer [walk prewalk postwalk cycling]]
            [clojure.tools.analyzer.jvm :as analyze-jvm]
            [clojure.tools.analyzer.jvm.utils :refer :all :exclude [box]]
            [clojure.tools.analyzer.passes.source-info :refer [source-info]]
            [clojure.tools.analyzer.passes.cleanup :refer [cleanup1 cleanup2]]
            [clojure.tools.analyzer.passes.elide-meta :refer [elide-meta]]
            [clojure.tools.analyzer.passes.constant-lifter :refer [constant-lift]]
            [clojure.tools.analyzer.passes.warn-earmuff :refer [warn-earmuff]]
            [clojure.tools.analyzer.passes.collect :refer [collect]]
            [clojure.tools.analyzer.passes.add-binding-atom :refer [add-binding-atom]]
            [clojure.tools.analyzer.passes.uniquify :refer [uniquify-locals]]
            [clojure.tools.analyzer.passes.jvm.box :refer [box]]
            [clojure.tools.analyzer.passes.jvm.annotate-branch :refer [annotate-branch]]
            [clojure.tools.analyzer.passes.jvm.annotate-methods :refer [annotate-methods]]
            [clojure.tools.analyzer.passes.jvm.fix-case-test :refer [fix-case-test]]
            [clojure.tools.analyzer.passes.jvm.clear-locals :refer [clear-locals]]
            [clojure.tools.analyzer.passes.jvm.classify-invoke :refer [classify-invoke]]
            ;;[clojure.tools.analyzer.passes.jvm.validate :refer [validate]]
            [clojure.tools.analyzer.passes.jvm.infer-tag :refer [infer-tag]]
            [clojure.tools.analyzer.passes.jvm.annotate-tag :refer [annotate-literal-tag annotate-binding-tag]]
            [clojure.tools.analyzer.passes.jvm.validate-loop-locals :refer [validate-loop-locals]]
            ;;[clojure.tools.analyzer.passes.jvm.analyze-host-expr :refer [analyze-host-expr]]
            ))


(defn analyze
  "Given an environment, a map containing
   -  :locals (mapping of names to lexical bindings),
   -  :context (one of :statement, :expr or :return
 and form, returns an expression object (a map containing at least :form, :op and :env keys)."
  [form env]
  (binding [ana/macroexpand-1 analyze-jvm/macroexpand-1
            ana/create-var    analyze-jvm/create-var
            ana/parse         analyze-jvm/parse]
    (-> (-analyze form env)

      uniquify-locals
      add-binding-atom

      (walk (fn [ast]
              (-> ast
                cleanup1
                warn-earmuff
                annotate-branch
                source-info
                elide-meta
                annotate-methods
                fix-case-test))
            constant-lift)

      ((fn analyze [ast]
         (-> ast
           (postwalk
            (comp (cycling
                   infer-tag
                   ;;analyze-host-expr
                   annotate-binding-tag
                   ;;validate
                   classify-invoke
                   )
               annotate-literal-tag)) ;; not necesary, select on v-l-l
           (prewalk
            (comp box
               (validate-loop-locals analyze)))))) ;; empty binding atom

      (prewalk
       (comp cleanup2
          (collect :constants
                   :callsites
                   :closed-overs)))

      clear-locals)))
