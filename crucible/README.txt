First create clones of many Clojure code repositories from Github:

    % ./clone.sh

This will also copy project.clj files from the directory
project-clj-files into some of the cloned repositories, in order to
make running Eastwood on them possible.  It will also overwrite some
existing project.clj files, so that they should all consistently use
Clojure 1.5.1 by default, and have profiles named 1.4, 1.5, and 1.6,
where 1.6 uses Clojure 1.6.0-master-SNAPSHOT.

To build and install a copy of 1.6.0-master-SNAPSHOT in your local
$HOME/.m2 Maven repository, do this:

    % cd repos/clojure
    % mvn install

To run Eastwood on many of these repos, make sure you have edited your
$HOME/.lein/profiles.clj file to include the line recommended in
Eastwood's README.md file.  Then run:

    % ./lint.sh

To save all of the output in a file:

    % ./lint.sh >& out.txt

You can see the progress with 'less out.txt' or 'tail -f out.txt' in
another terminal window.

To change the options used for eastwood, the easiest way right now is
simply to edit lint.sh and change the command lines that invoke
eastwood.
