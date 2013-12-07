First create clones of many Clojure code repositories from Github:

    % ./clone.sh

This will also copy project.clj files from the directory
project-clj-files into some of the cloned repositories, in order to
make running Eastwood on them possible.

At least some of those project.clj files specify 1.6.0-master-SNAPSHOT
as the version of Clojure to use.  To build and install a copy in your
local $HOME/.m2 Maven repository, do this:

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
