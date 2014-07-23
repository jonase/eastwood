# Methods of annotating Clojure code for analysis tools


## Goal

Allow Clojure programmers to annotate source code in a way that
modifies the behavior of tools that analyze the source code, but
without modifying the behavior of the Clojure compiler (or at least
without significantly affecting the code it generates).


## Side goals

Document the alternatives with pros and cons, as a reference for
others who may wish to annotate Clojure source code in the future.  If
we can devise an annotation method that works for multiple Clojure
tools, that would be good.


## Annotation methods

Here we list the methods, giving them a name and a brief description.
Later we discuss their pros and cons in more detail.


### Specially formatted comments

This is a popular method for lint tools.  The programmer inserts
comments that are completely ignored by the compiler, but their
contents are formatted in a way that is recognized by the analysis
tool.  The comments often have an effect on the following lexical
element, line, or sometimes a group of lines bounded by starting and
ending comments.

[Example][SplintAnnotationExample] for the lint tool Splint for C/C++
programs.

[Example][IntelliJAnnotationExample] for the Java IDE IntelliJ.

[SplintAnnotationExample]: http://www.splint.org/manual/manual.html#null
[IntelliJAnnotationExample]: https://groups.google.com/d/msg/clojure-dev/5_dlGSNR6xQ/hqXNZE9-RLcJ

Clojure example, where the comment line is intended to disable
`:def-in-def` lint warnings for the following expression, `(def bar (*
x c))`:

```clojure
(defn my-fn [a b c]
  (let [x (+ a b)]
    ;; EASTWOOD: {:disable-linters [:def-in-def]}
    (def bar (* x c))
    (- bar b)))
```


### Metadata

Example:

```clojure
(defn my-fn [a b c]
  (let [x (+ a b)]
    ^{:eastwood {:disable-linters [:def-in-def]}}
    (def bar (* x c))
    (- bar b)))
```

The metadata is associated with the following form `(def bar (* x
c))`.


### Marker macro

A marker macro is a macro defined for the purpose of wrapping around
expressions to be annotated, with additional arguments describing the
desired annotation.  An example, where the marker macro is called
`eastwood/ann`:

```clojure
(defn my-fn [a b c]
  (let [x (+ a b)]
    (eastwood/ann
      {:disable-linters [:def-in-def]}
      (def bar (* x c)))
    (- bar b)))
```

The macro simply expands to the expression that is wrapped, without
the additional arguments, e.g.:

```clojure
(defmacro ann [opts expr]
  expr)
```

An analysis tool using this method would need to examine the form
before it is macroexpanded to use the extra arguments.


### do form

This is similar to the marker macro idea, but does not require
defining any new macros.  It uses the existing Clojure special form
`do`, where the extra arguments are not the last argument, and thus as
long as they have no side effects, they should not alter the behavior
of the program.

```clojure
(defn my-fn [a b c]
  (let [x (+ a b)]
    (do
      {:disable-linters [:def-in-def]}
      (def bar (* x c)))
    (- bar b)))
```


## Analysis tools where this is helpful

[`core.typed`][CoreTyped] has been described by its author Ambrose
Bonnaire-Sergeant as a lint tool that gives warnings about type errors
in annotated Clojure programs (TBD: reference?).  It uses annotation
expressions written by the programmer to describe the types that Vars
can contain.

[Eastwood][Eastwood] is a lint tool that warns about expressions in
your program that may be bugs.  Like most other lint tools, it
sometimes warns about things that are not bugs.  The original
motivation for writing this document was to determine a good way to
disable these warnings for _individual expressions_ in a Clojure
program.

[Cursive][Cursive] is a Clojure IDE based on [IntelliJ][IntelliJ] for
Java.  For Java, IntelliJ already allows 'inspections' to be disabled
globally or at various granularities using either Java annotations or
specially formatted coments.


## Granularity of annotation

By granularity here I mean the kinds of things in Clojure source code
that can be individually annotated, e.g. top level forms only?
Arbitrary expressions?  Something else?

For `core.typed`, its `ann` macro is used to annotate the types of
values that Vars can contain, giving the name of the Var in the macro
invocation.  The `ann` invocation need not even be in the same
namespace as the Var it is annotating, by fully qualifying the name.

TBD: `core.typed` also defines macros `ann-datatype`, `ann-protocol`,
and `ann-form`, and its own versions of `for` and `doseq` that may
contain annotations of binding forms, and for `for` the type of the
body expression.  `ann-form` appears to be a marker macro as described
here, whereas `ann` is restricted to annotate Var names elsewhere in
the code (perhaps the same file, perhaps a different file).

For Eastwood and Cursive, there are good use cases for annotating
arbitrary expressions.

The primary use case for Eastwood annotations is to disable particular
kinds of warnings.  Suppose the only way to disable warnings was for
an entire top-level form.  Then the following could occur:

+ The programmer writes a Clojure function containing many
  expressions, runs Eastwood, and gets a warning for a particular
  expression within that function.

+ The programmer reads the warning and the code, determines that the
  code is correct and the warning is misleading or wrong, and disables
  that kind of warning for the entire function.

+ Later the programmer modifies the function, unintentionally
  introducing a bug that the suppressed Eastwood warning is capable of
  catching.  The new bug is outside of the expression that caused the
  original warning.  Since the warning is suppressed on the entire
  function, the programmer never learns of the bug via Eastwood, so it
  takes more effort to find and fix.

If Eastwood warnings could be suppressed on smaller sections of code,
this scenario can be avoided.


## References

There was an discussion of some of these alternatives in the
[clojure-dev][ClojureDev] group [here][ClojureDevCodeAnnotation]
starting in June 2014.

[CoreTyped]: https://github.com/clojure/core.typed
[CoreTypedRt]: http://mvnrepository.com/artifact/org.clojure/core.typed.rt
[Eastwood]: https://github.com/jonase/eastwood
[Cursive]: https://cursiveclojure.com
[IntelliJ]: http://www.jetbrains.com/idea
[ClojureDev]: https://groups.google.com/forum/#!forum/clojure-dev
[ClojureDevCodeAnnotation]: https://groups.google.com/forum/#!topic/clojure-dev/5_dlGSNR6xQ



Marker macro pros:

As long as they are not used in a way that interacts poorly with other
macros, they can mark (almost) any expressions, and have no effect on
the compiled code.


Marker macros cons:

You must require the definition of the marker macro from some
namespace in a dependency that you would otherwise not have in your
project.  Can mitigate this by creating a tiny project that does
nothing but define the marker macro.  [`core.typed`][CoreTyped] does
this with [`core.typed.rt`][CoreTypedRt].





    [10:37:16] andyf_: In the github issue for Eastwood about selective disabling of warnings, you mention marker macros, "but those have issues"
    [10:38:09] andyf_: I wanted to find out what issues you meant, since I was considering using a marker macro for this purpose
    [10:39:19] hiredman: you couldn't only have a dev dependency on eastwood then
    [10:40:02] andyf_: Meaning you need to require the namespace defining the macro, yes?
    [10:40:10] hiredman: right
    [10:40:31] hiredman: which could be a distinct "slim" depdency or something
    [10:40:39] andyf_: What if that were in a tiny project doing almost nothing but defining that macro?
    
    [10:41:39] technomancy: this sounds like a textbook example for why metadata is useful?
    [10:42:20] andyf_: technomancy: I'm considering that, but you cannot put it on everything
    
    [10:42:48] ambroseb_: andyf_: org.clojure/core.typed.rt is basically that for core.typed
    
    [10:42:51] andyf_: A macro can wrap any expression
    
    [10:43:33] technomancy: andyf_: what's an example of a valid source form that would generate a warning but not be IObj or IMeta?
    [10:43:35] Bronsa: a macro can be a no-op only for functions tohough, if you have (macro1 (macro2 ..)) macro2 will not be invisible to macro1
    
    [10:43:43] Bronsa: technomancy: a keyword
    [10:43:55] Bronsa: technomancy: eastwood has a misspelled-keyword linter
    [10:44:04] technomancy: huh
    [10:44:22] technomancy: ^:eastwood/ignore (identity :not-a-typo) =)
    
    [10:44:45] technomancy: Bronsa: can't you just move the metadata up to the form containing the keyword though?
    [10:45:00] Bronsa: technomancy: that might work, yes
    [10:45:11] andyf_: technomancy: A marker macro can leave compiled byte code unmodified, I believe
    [10:45:17] Bronsa: technomancy: actually no
    [10:45:29] Bronsa: technomancy: it has the same issues as using a macro
    [10:45:40] Bronsa: technomancy: e.g. if your keyword is an argument to a macro that expects a keyword literal
    [10:45:56] technomancy: I see
    
    [10:46:17] technomancy: Bronsa: actually no, I don't
    [10:46:22] technomancy: just attach the metadata to the macro call
    [10:46:27] technomancy: and the linter shouldn't descend into it, right?
    [10:46:36] andyf_: Bronsa: I'm not expecting people will want to disable keyword warnings that way, necessarily, but I wouldn't want to disallow it
    [10:46:36] technomancy: (forget the identity bit)
    [10:46:59] Bronsa: andyf_: I think at this point having evaluated all the possibile solutions metadata seems like the best one. we can use a gloabal table of keywords to ignore
    [10:47:23] Bronsa: technomancy: ^{:eastwood/ignore-keywords #{:foo}} (macro ... :foo ..) ?
    [10:47:39] technomancy: right
    [10:48:03] technomancy: or just ^:eastwood/ignore-keywords if you are lazy and sacrifice granularity
    
    [10:48:06] andyf_: Bronsa: Do you have any concerns about a marker macro other than the need to require its definition?
    [10:48:27] Bronsa: andyf_: yes. it wont work in some cases
    [10:48:32] Bronsa: 18:43:34 <Bronsa> a macro can be a no-op only for functions tohough, if you have (macro1 (macro2 ..)) macro2 will not be invisible to macro1
    [10:48:32] Bronsa: 
    [10:48:36] Bronsa: andyf_: ^
    [10:49:25] Bronsa: andyf_: silly example, (for [x (range) (eastwood-ignore :when) (even? x)] x)
    [10:49:27] Bronsa: that won't work
    [10:49:58] andyf_: Sorry if I am being dense here, but if macro2 is defined to expand only to its 1 arg , what can go wrong there?
    
    [10:50:39] technomancy: andyf_: just saw the eastwood ticket about re-using JVMs; are you aware of :eval-in :nrepl?
    
    [10:51:03] andyf_: Ok, but that example doesn't work equally for metadata, either
    [10:51:13] Bronsa: andyf_: (defmacro x [y] (if (= :y y) 1 2)) (x :y) (x (do :y)) will produce different results
    [10:52:45] Bronsa: andyf_: correct, there's no way to wrap a form in an "invisible" way, if the form is used inside a macro call it will always be problematic
    [10:53:25] Bronsa: andyf_: what technomancy suggested might be the best solution -- attaching the :ignore metadata on a previous form
    
    [10:53:40] andyf_: Back in a few mins
    [11:21:09] andyf_: Sorry to drop out on folks when you were helping me out there.  Work intrudes.  Will read the discussion and bring it up again later if I have more questions.  Thanks!


Bronsa example of a macro that changes behavior when an argument is
wrapped in a marker macro, vs. when it is not (do is used as a marker
macro standin in this example):

```clojure
    (defmacro foo [y]
      (if (= :y y)
        1
        2))

    (foo :y)
    ;; => 1

    (foo (do :y))
    ;; => 2
```


Notes from #typed-clojure IRC channel:

    andyf: Does core.typed use the ann macro to annotate anything
    other than Clojure functions?  If not, do you have any thoughts or
    plans to extend annotations for other purposes?

    yotsov: andyf: you can use ann to annotate things other than
    functions.  For example a set held in a var, or a heterogeneous
    map...

    ambrosebs: andyf: functions are the most common, but there's
    nothing special about the ann macro wrt functions

    ambrosebs: ann is just annotating the type a var can contain
