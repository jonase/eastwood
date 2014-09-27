(ns eastwood.copieddeps.dep1.clojure.tools.analyzer.passes
  (:require [eastwood.copieddeps.dep1.clojure.tools.analyzer.ast :refer [prewalk postwalk]]))

(defn has-deps? [pass]
  (seq (:dependencies pass)))

(defn group-by-walk [passes]
  (reduce-kv (fn [m k v] (assoc m k (set (map :name v))))
             {} (group-by :walk passes)))

(defn indicize [passes]
  (zipmap (map :name passes) passes))

(defn remove-pass [passes pass]
  (indicize (reduce (fn [m p] (conj m (-> p (update-in [:dependencies] disj pass)
                                        (update-in [:dependants] disj pass))))
                    #{} (vals (dissoc passes pass)))))

(defn calc-deps [m k deps passes]
  (if (m k)
    m
    (reduce (fn [m dep]
              (let [m (if (m dep)
                        m
                        (calc-deps m dep (get-in passes [dep :depends]) passes))])
              (update-in m [k] into (conj (or (m dep) #{}) dep)))
            (assoc m k deps) deps)))

(defn calculate-deps [passes]
  (let [dependencies (reduce-kv (fn [deps pname {:keys [depends after]}]
                                  (calc-deps deps pname
                                             (into depends (concat (filter passes after)
                                                                   (mapv key (filter #(get (-> % val :before) pname) passes)))) passes))
                                {} passes)
        dependants   (reduce-kv (fn [m k v] (reduce (fn [m v] (update-in m [v] (fnil conj #{}) k))
                                                   (update-in m [k] (fnil into #{}) nil) v))
                                {} dependencies)]
    (reduce-kv (fn [m k v] (assoc m k (merge (dissoc (passes k) :depends)
                                            {:dependencies (set v) :dependants (set (dependants k))})))
               {} dependencies)))

(defn group [state]
  (loop [w nil group [] [cur & rest :as state] state]
    (if (seq state)
      (cond
       (:affects (last group))
       [w group state]

       w
       (if (#{w :any} (:walk cur))
         (recur w (conj group cur) rest)
         [w group state])

       :else
       (case (:walk cur)
         :any
         (recur nil (conj group cur) rest)
         :none
         [w group state]
         (recur (:walk cur) (conj group cur) rest)))
      [w group state])))

(defn reorder [state]
  (if (and (first state)
           (not= :own (:walk (first state))))
    (let [[g1-w g1 state'] (group state)
          [_    g2 state'] (group state')
          [g3-w g3 state'] (group state')
          g1-nodes         (mapv :name g1)
          g2-nodes         (mapv :name g2)]
      (if (and (seq g1) (seq g2) (seq g3)
               (= g1-w g3-w))
        (cond
         (not-any? #(seq (filter (:dependencies %) g1-nodes)) g2)
         (concat (into (into g2 g1) g3) state')

         (not-any? #(seq (filter (:dependencies %) g2-nodes)) g3)
         (concat (into (into g1 g3) g2) state')

         :else
         state)
        state))
    state))

(def ffilter (comp first filter))

(defn ffilter-walk [f c]
  (ffilter (comp f :walk) c))

(defn schedule* [state passes]
  (let [state                     (reorder state)
        f                         (filter (comp empty? :dependants val) passes)
        [free & frs :as free-all] (vals f)
        [w g _]                   (group state)]
    (if (seq passes)
      (if-let [x (or (ffilter :compiler free-all)
                     (and w (or (ffilter-walk #{w} free-all)
                                (ffilter-walk #{:any} free-all)))
                     (ffilter-walk #{:none} free-all)
                     (ffilter :affects free-all))]
        (recur (cons (assoc x :passes [(:name x)]) state)
               (remove-pass passes (:name x)))
        (recur (cons (assoc free :passes [(:name free)]) state)
               (remove-pass passes (:name free))))
      state)))

(defn collapse [state]
  (loop [[cur & rest :as state] state ret []]
    (if (seq state)
      (if (= :none (:walk cur))
        (recur rest (conj ret cur))
        (let [[w g state] (group state)]
          (recur state (conj ret (merge {:walk (or w :pre) :passes (mapv :name g)}
                                        (when-let [affects (first (filter :affects g))]
                                          (let [passes (set (mapv :name g))]
                                            (when (not-every? passes (:affects affects))
                                              (throw (ex-info (str "looping pass doesn't encompass affected passes: " (:name affects))
                                                              {:pass affects}))))
                                          {:loops true}))))))
      ret)))

(defn schedule-passes
  [passes]
  (let [passes (calculate-deps passes)
        dependencies (set (mapcat :dependencies (vals passes)))]

    (when (every? has-deps? (vals passes))
      (throw (ex-info "Dependency cycle detected" passes)))

    (when (next (filter :compiler (vals passes)))
      (throw (ex-info "Only one compiler pass allowed" passes)))

    (mapv #(select-keys % [:passes :walk :loops])
          (collapse (schedule* () passes)))))

(defn schedule
  "Takes a set of Vars that represent tools.analyzer passes and returns a function
   that takes an AST and applies all the passes and their dependencies to the AST,
   trying to compose together as many passes as possible to reduce the number of
   full tree traversals.

   Each pass must have a :pass-info element in its Var's metadata and it must point
   to a map with the following parameters (:before, :after, :affects and :state are
   optional):
   * :after    a set of Vars, the passes that must be run before this pass
   * :before   a set of Vars, the passes that must be run after this pass
   * :depends  a set of Vars, the passes this pass depends on, implies :after
   * :walk     a keyword, one of:
                 - :none if the pass does its own tree walking and cannot be composed
                         with other passes
                 - :post if the pass requires a postwalk and can be composed with other
                         passes
                 - :pre  if the pass requires a prewalk and can be composed with other
                         passes
                 - :any  if the pass can be composed with other passes in both a prewalk
                         or a postwalk
   * :affects  a set of Vars, this pass must be the last in the same tree traversal that all
               the specified passes must partecipate in
               This pass must take a function as argument and return the actual pass, the
               argument represents the reified tree traversal which the pass can use to
               control a recursive traversal
   * :state    a no-arg function that should return the init value of an atom that will be
               passed as the first argument to the pass (the pass will thus take the ast
               as the second parameter), the atom will be the same for the whole tree traversal
               and thus can be used to preserve state across the traversal
   An opts map might be provided, valid parameters:
   * :debug?   if true, returns a vector of the scheduled passes rather than the concrete
               function"
  [passes & [opts]]
  {:pre [(set? passes)
         (every? var? passes)]}
  (let [info        (indicize (mapv (fn [p] (merge {:name p} (:pass-info (meta p)))) passes))
        passes+deps (into passes (mapcat :depends (vals info)))]
    (if (not= passes passes+deps)
      (recur passes+deps [opts])
      (if (:debug? opts)
        (schedule-passes info)
        (reduce (fn [f {:keys [passes walk loops]}]
                  (-> (if (= walk :none)
                       (first passes)
                       (let [walk (if (= :pre walk) prewalk postwalk)
                             passes (rseq passes)
                             pfns (fn [state analyze]
                                    (let [passes (if loops
                                                   (cons ((first passes) analyze)
                                                         (rest passes))
                                                   passes)]
                                      (mapv (fn [p] (if (:state (info p))
                                                     (partial p (state p))
                                                     p)) passes)))
                             with-state (filter (comp :state info) passes)]
                         (fn analyze [ast]
                           (let [state (zipmap with-state (mapv #((:state (info %))) with-state))]
                             (walk ast (reduce comp (pfns state analyze)))))))
                    (comp f)))
                identity (schedule-passes info))))))
