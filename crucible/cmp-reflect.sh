#! /bin/bash

# Steps:

# (1) Run Eastwood on the project with whatever options work, and save
# the output in a file.  The choice of linters should not matter, as
# long as the following line is in the project.clj file to enable
# reflection warnings to be printed.

#  :global-vars {*warn-on-reflection* true}

# The Eastwood default is to include :source-paths and :test-paths,
# but for some projects where :test-paths has side effects I limit
# Eastwood to :source-paths only.

# e.g. below the examples will call this file eastwood-out.txt

# (2) Create a file that contains the output of 'lein check' with
# whatever options are desired.  The version of Clojure and the JDK
# used between steps (1) and (2) should be the same.  If step (1)
# included :test-paths, then the project.clj file should temporarily
# be modified to set :source-paths as the union of :source-paths and
# :test-paths, but the project.clj file should not be left like that.

# below examples will call this file lein-check-out.txt

# The order of reflection warnings in these two files often differs,
# usually because the order that namespaces are processed is
# different.

# It seems that 'lein check' will often repeat the same reflection
# warning for a dependency, if it is a dependency of more than one
# namespace, whereas usually the Eastwood output does not have such
# duplicates.  I am not sure why yet, and sometimes I believe there
# are duplicates in the Eastwood output, too.

# Another common difference are the line/col numbers.  I think the
# 'lein check' output is usually more useful in helping a developer
# locate the best line/col for the source of the problem, in cases
# where they differ.

# Goal: Extract all reflection warnings from both files, and compare
# them 'as a set', although duplicates in the Eastwood output will be
# considered to have occurred multiple times, while 'lein check'
# output will be processed to eliminate duplicates.  Move the lein/col
# numbers to the end of the line, so that Unix sort will put warnings
# for the same file name and symbol together, even if their line/col
# numbers are different between the two output files.

# The do a diff on the resulting files.


# I found this bit of trickery at the StackOverflow link below.  I
# have tested that if the script is referenced through a symbolic link
# (1 level of linking testd), it gets the directory where the symbolic
# link is, not the directory where the linked-to file is located.  I
# believe that is what we want if this script is checked out as a
# linked tree: the directory of the checked-out version, which might
# have changes to other files in the checked-out tree in that same
# directory, and we want to see those.
# http://stackoverflow.com/questions/421772/how-can-a-bash-script-know-the-directory-it-is-installed-in-when-it-is-sourced-w

INSTALL_DIR=$(dirname "$BASH_SOURCE")
#echo "INSTALL_DIR=${INSTALL_DIR}"
#exit 0
EXTRACT=${INSTALL_DIR}/reflection-warnings.pl


if [ $# -ne 2 ]
then
    1>&2 echo "usage: `basename $0` <lein-check-out.txt> <eastwood-out.txt>"
    exit 1
fi

LCO="$1"
EASTO="$2"

LCO2="${LCO}.sorted"
EASTO2="${EASTO}.sorted"
EASTO3="${EASTO}.sorted.uniq"

${EXTRACT} < ${LCO}   | sort | uniq > ${LCO2}
${EXTRACT} < ${EASTO} | sort        > ${EASTO2}
${EXTRACT} < ${EASTO} | sort | uniq > ${EASTO3}

diff ${LCO2} ${EASTO3}
