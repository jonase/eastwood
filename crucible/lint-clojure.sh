#! /bin/bash

# (0) You need a Clojure jar file.  Edit as needed for your situation.

# "mvn package" creates target/clojure-<version>.jar
CLOJURE_JAR="target/clojure-1.7.0-master-SNAPSHOT.jar"

# "./antsetup.sh ; ant jar" creates clojure.jar in Clojure source tree
# root directory.
#CLOJURE_JAR="clojure.jar"


# (1) The file maven-classpath can be created in a Clojure source tree
# using the command "./antsetup.sh" (you do not need ant to run this
# script, only mvn).

MVN_CP=`cat maven-classpath`

# (2) These Eastwood dependency versions match those used by Eastwood
# version 0.2.1.  A quick way to get them all in your $HOME/.m2
# directory is to run 'lein eastwood' in a Leiningen project.

M2="$HOME/.m2/repository"
A="${M2}/org/ow2/asm/asm-all/4.2/asm-all-4.2.jar"
G="${M2}/org/clojars/brenton/google-diff-match-patch/0.1/google-diff-match-patch-0.1.jar"
E="${M2}/jonase/eastwood/0.2.1/eastwood-0.2.1.jar"

# (3) After running this script in the root of the Clojure source tree
# to start the REPL, eval the expressions below.  The exc-nss is a
# list of namespaces that cause exceptions to be thrown with Eastwood
# 0.2.1 and Clojure 1.7.0-alpha6.  I haven't dug into the root causes
# of all of them yet.  In some cases they are test namespaces that
# exist only for the purpose of checking that Clojure throws a
# particular exception when it tries to load them.

# See https://github.com/jonase/eastwood#editor-support for
# instructions on using the file "eastwood-warnings.txt" to jump to
# the file, line, and column of each warning using Vim or Emacs.

# (require '[eastwood.lint :as e])
# (def exc-nss '[clojure.test-clojure.try-catch clojure.test-clojure.compilation.line-number-examples clojure.test-clojure.compilation clojure.test-clojure.genclass clojure.test-clojure.ns-libs clojure.test-clojure.protocols])
# (e/eastwood {:out "eastwood-warnings.txt" :source-paths [] :test-paths ["test"] :exclude-linters [:unlimited-use] :exclude-namespaces exc-nss})

rlwrap java -cp "${MVN_CP}:${CLOJURE_JAR}:${A}:${G}:${E}:test" clojure.main


######################################################################
# Details of exceptions thrown by some namespaces, found iteratively
# by excluding one namespace at a time until there were no more
# exceptions..
######################################################################

# This namespace: # clojure.test-clojure.try-catch

# throws an exception about the class
# clojure.test.ReflectorTryCatchFixture not being found.  It probably
# needs another directory added to the classpath to find it, but I
# don't yet know which one.


# (e/eastwood {:source-paths [] :test-paths ["test"] :exclude-namespaces '[clojure.test-clojure.try-catch]})

# This namespace:
# clojure.test-clojure.compilation.line-number-examples

# causes an exception about 'Cannot set! on a non-assignable target'.
# The purpose of this file seems to be to try to compile it and make
# the compiler throw particular exceptions.  It should be skipped for
# linting.

# (e/eastwood {:source-paths [] :test-paths ["test"] :exclude-namespaces '[clojure.test-clojure.try-catch clojure.test-clojure.compilation.line-number-examples]})

# This namespace: clojure.test-clojure.compilation

# goes fine for a while, then throws an exception java.io.IOException:
# No such file or directory, caused by IOException No such file or
# directory.  I won't try to figure out whether I can work around this
# yet.  Just skip the namespace for now.

# The following form was being processed during the exception:
# (binding
#  [*compile-path* "target/test-classes"]
#  (compile 'clojure.test-clojure.compilation.examples))

# (e/eastwood {:source-paths [] :test-paths ["test"] :exclude-linters [:unlimited-use] :exclude-namespaces '[clojure.test-clojure.try-catch clojure.test-clojure.compilation.line-number-examples clojure.test-clojure.compilation]})

# This namespace: clojure.test-clojure.genclass

# throws this exception:
# ClassNotFoundException clojure.test_clojure.genclass.examples.ExampleClass
# 	java.net.URLClassLoader$1.run (URLClassLoader.java:366)
# 
# The following form was being processed during the exception:
# (clojure.core/with-loading-context
#  (clojure.core/refer 'clojure.core)
#  (clojure.core/use 'clojure.test 'clojure.test-helper)
#  (clojure.core/require 'clojure.test_clojure.genclass.examples)
#  (clojure.core/import
#   '[clojure.test_clojure.genclass.examples
#     ExampleClass
#     ExampleAnnotationClass
#     ProtectedFinalTester
#     ArrayDefInterface
#     ArrayGenInterface]
#   '[java.lang.annotation ElementType Retention RetentionPolicy Target]))


# (e/eastwood {:source-paths [] :test-paths ["test"] :exclude-linters [:unlimited-use] :exclude-namespaces '[clojure.test-clojure.try-catch clojure.test-clojure.compilation.line-number-examples clojure.test-clojure.compilation clojure.test-clojure.genclass]})

# == Linting clojure.test-clojure.ns-libs ==
# Exception thrown during phase :analyze+eval of linting namespace clojure.test-clojure.ns-libs
# IllegalStateException StringBuffer already refers to: class java.lang.StringBuffer in namespace: clojure.test-clojure.ns-libs
# 	clojure.lang.Namespace.referenceClass (Namespace.java:140)
# 	clojure.lang.Namespace.importClass (Namespace.java:158)
# 
# The following form was being processed during the exception:
# (deftest
#  naming-types
#  (testing
#   "you cannot use a name already referred from another namespace"
#   (is
#    (thrown?
#     IllegalStateException
#     #"String already refers to: class java.lang.String"
#     (definterface String)))
#   (is
#    (thrown?
#     IllegalStateException
#     #"StringBuffer already refers to: class java.lang.StringBuffer"
#     (deftype StringBuffer [])))
#   (is
#    (thrown?
#     IllegalStateException
#     #"Integer already refers to: class java.lang.Integer"
#     (defrecord Integer [])))))


# (e/eastwood {:source-paths [] :test-paths ["test"] :exclude-linters [:unlimited-use] :exclude-namespaces '[clojure.test-clojure.try-catch clojure.test-clojure.compilation.line-number-examples clojure.test-clojure.compilation clojure.test-clojure.genclass clojure.test-clojure.ns-libs]})


# I do not understand why the exception below occurs at all.  The only
# recur is in the test that does reify on a java.util.List.  Could
# this be a bug in tools.analyzer?

# I get the same exception even if I lint only this one namespace.

# I have tried to reproduce the problem with a smaller source file in
# ~/clj/andy-forks/eastwood/cases/testcases/methodrecur.clj, but have
# not been able to get an exception.  Note that attempt uses the
# latest version of Eastwood in that directory, not version 0.2.1
# where this exception is occurring.

# I was able to reproduce it with this file:
# ~/clj/useclj/useclj16/src/useclj16/methodrecur.clj, using Eastwood
# 0.2.1.  Perhaps this is a bug in tools.analyzer that has been fixed
# since Eastwood 0.2.1's version of it?

# (e/eastwood {:source-paths [] :test-paths ["test"] :exclude-linters [:unlimited-use] :namespaces '[clojure.test-clojure.protocols]})

# == Linting clojure.test-clojure.protocols ==
# Exception thrown during phase :analyze+eval of linting namespace clojure.test-clojure.protocols
# Got exception with extra ex-data:
#     msg='Cannot recur across try'
#     (keys dat)=(:exprs :form :file :end-column :column :line :end-line)
#     (:form dat)=
# ^{:line 630}
# (^{:line 630} recur ^{:line 630} (^{:line 630} dec ^{:line 630} index))
# 
# ExceptionInfo Cannot recur across try
# 	clojure.core/ex-info (core.clj:4593)



# Finally!  The form below evaluates with no exceptions.

# (e/eastwood {:source-paths [] :test-paths ["test"] :exclude-linters [:unlimited-use] :exclude-namespaces '[clojure.test-clojure.try-catch clojure.test-clojure.compilation.line-number-examples clojure.test-clojure.compilation clojure.test-clojure.genclass clojure.test-clojure.ns-libs clojure.test-clojure.protocols]})
