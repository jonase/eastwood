First create clones of many Clojure code repositories from Github:

    % ./clone.sh

This will also copy project.clj files from the directory
project-clj-files into some of the cloned repositories, in order to
make running Eastwood on them possible.  It will also overwrite some
existing project.clj files, so that they should all consistently use
Clojure 1.5.1 by default, and have profiles named 1.5, 1.6, 1.7, 1.8,
1.9, and 1.10, where 1.10 uses Clojure 1.10.0-master-SNAPSHOT.

To build and install a copy of Clojure x.y.0-master-SNAPSHOT in your
local $HOME/.m2 Maven repository, do this:

    % git clone https://github.com/clojure/clojure.git
    % cd clojure
    % git checkout <tag-or-commit-id>
    % mvn clean install

To run Eastwood on many of these repos, make sure you have edited your
$HOME/.lein/profiles.clj file to include the line recommended in
Eastwood's README.md file.  Then run:

    % ./lint.sh

To save all of the output in a file:

    % ./lint.sh >& out.txt

You can see the progress with 'less out.txt' or 'tail -f out.txt' in
another terminal window.

To change the options used for Eastwood, the easiest way right now is
simply to edit lint.sh and change the command lines that invoke
Eastwood.
