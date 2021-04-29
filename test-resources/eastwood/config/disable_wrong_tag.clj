(disable-warning
 {:linter :wrong-tag
  :if-inside-macroexpansion-of '#{testcases.wrong-tag-example/the-macro}
  :reason "Support a deftest"})
