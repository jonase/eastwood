### Check consistency of namespace and file names

When doing `require` or `use` on a namespace like `foo.bar.baz-tests`,
it is searched for in the Java classpath in a file named
`foo/bar/baz_tests.clj` (on Unix-like systems) or
`foo\bar\baz_tests.clj` (on Windows).  Dots become path separator
characters and dashes become underscores.

Such a file will normally have an `ns` form with the specified
namespace.  If it does not match the file it is in, then undesirable
things can happen.  For example, `require` could fail to find the
namespace, or Leiningen could fail to run the tests defined in a test
namespace.

Eastwood checks all Clojure files in `:source-paths` and `:test-paths`
when starting up (or only one of them, if only one is specified in the
`:namespaces` sequence in the command options).  If there are any
mismatches between file names and the namespace names in the `ns`
forms, an error message will be printed and no linting will be done at
all.  This helps avoid some cases of printing error messages that make
it difficult to determine what went wrong.  Fix the problems indicated
and try again.


### `:bad-arglists` - arglists that does not match the actual number of arguments that a function/macro was defined to take

TBD: Give examples of function/macro definitions from Clojure and
other libraries that will cause this warning, and some that will not,
even though they explicitly specify a value for :arglists.

TBD: Perhaps also give this example.  Is it possible for Eastwood to
ever warn about a function defined in this way?  I am guessing it
would not be practical to do so, and whatever :arglists is specified
will never cause a :bad-arglists warning, but verify that.

```clojure
(def
 ^{:tag Boolean
   :doc "Returns false if (pred x) is logical true for every x in
  coll, else true."
   :arglists '([pred coll])
   :added "1.0"}
 not-every? (comp not every?))
```

This linter was created because of the belief that it is better if
the value of `:arglists` for vars accurately represents the number of
arguments that can be used to call the function/macro, as opposed
to some other thing used purely for documentation purposes.

It is true that even Clojure itself does not conform to this
restriction.  For example, the arglists of `defn`, `defmacro`, and
several other macros override `:arglists` for purposes of clearer
documentation.  However, all but these few exceptional macros 

Other facts supporting this belief are:

The value of metadata key :arglists is set automatically by macros
like defn and defmacro.

The Clojure compiler uses these arglists to determine things like
the type of the return value of a function call.

It would be nice if Eastwood (in particular its :wrong-arity
linter) and other Clojure development tools could rely upon
:arglists matching the actual arities of the function or macro that
have been defined.
