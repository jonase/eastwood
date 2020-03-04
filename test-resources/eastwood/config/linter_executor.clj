(defn custom-map [f & colls]
  (swap! @(resolve 'eastwood.linter-executor-test/proof)
         conj
         :hello)
  (apply map f colls))

(set-linter-executor! custom-map)
