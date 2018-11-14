# Running Eastwood on all projects in the 'crucible'

First create clones of many Clojure code repositories from Github:

```bash
% ./clone.sh
```

This will also copy Leiningen project.clj files from the directory
project-clj-files into some of the cloned repositories, which makes it
more straightforward to run Eastwood using a Leiningen command line on
those projects.  It will also overwrite some existing project.clj
files, so that they should all consistently use Clojure 1.5.1 by
default, and have profiles named 1.5, 1.6, up through the latest
unreleased version of Clojure, with uses Clojure
1.x.0-master-SNAPSHOT.

To build and install a copy of Clojure 1.x.0-master-SNAPSHOT in your
local $HOME/.m2 Maven repository, do this:

```bash
% git clone https://github.com/clojure/clojure.git
% cd clojure
% git checkout <tag-or-commit-id>
% mvn clean install
```

To run Eastwood on many of these repos, make sure you have edited your
`$HOME/.lein/profiles.clj` file to include the line recommended in
Eastwood's README.md file.  Then run:

```bash
% ./lint.sh
```

To save all of the output in a file:

```bash
% ./lint.sh >& out.txt
```

You can see the progress with `less out.txt` or `tail -f out.txt` in
another terminal window.

To change the options used for Eastwood, the easiest way right now is
simply to edit lint.sh and change the command lines that invoke
Eastwood.



## Scripted and unattended running of multiple (Eastwood, Clojure, JDK) version combinations

The `lint.sh` bash script described above is fine for running Eastwood
on all projects in the crucible for a single version of Eastwood, and
a single version of Clojure, and a single version of the JDK, but it
requires manual editing of one or more files to switch which
combination of versions are tested.

Suppose you want to run Eastwood on all projects in the crucible, for
all of these eight combinations of versions:

```
Eastwood  Clojure       JDK
version   version       version
--------  ------------  -------
  0.3.2   1.9.0         1.8.0
  0.3.2   1.9.0         11
  0.3.2   1.10.0-beta5  1.8.0
  0.3.2   1.10.0-beta5  11

  0.3.3   1.9.0         1.8.0
  0.3.3   1.9.0         11
  0.3.3   1.10.0-beta5  1.8.0
  0.3.3   1.10.0-beta5  11
```

This can be useful sometimes whe you want to see how the Eastwood
output varies across versions, e.g. to discover whether a particular
warning is no longer caught with a new version of Eastwood, or a new
warning _is_ caught with a new version of Eastwood.

```bash
% clj -Acrucible --versions version-combos.edn
```
