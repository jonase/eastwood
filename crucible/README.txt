First create clones of many Clojure code repositories from Github:

    % ./clone.sh

This will also copy project.clj files from the directory
project-clj-files into some of the cloned repositories, in order to
make running Eastwood on them possible.

To run Eastwood on them, make sure you have edited your
~/.lein/profiles.clj file to include the line recommended in
Eastwood's README.md file.  Then run:

    % ./lint.sh

To save all of the output in a file:

    % ./lint.sh >& out.txt

You can see the progress with 'less out.txt' or 'tail -f out.txt' in
another terminal window.
