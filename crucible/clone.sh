#! /bin/bash

set -e

# Clone git repositories containing Clojure code useful for testing
# Eastwood with.

CLONE="git clone"
CHECKOUT="git checkout"

# Create a subdirectory for the pulled repos.  This makes it easier to
# delete them all if you later want to clone the latest versions again
# all from scratch.
mkdir -p repos
cd repos

REPOS_DIR="${PWD}"

for script_relative_dir in ../project-clj-files/*
do
    clone_relative_dir=`basename ${script_relative_dir}`
    clone_fullpath_dir="${REPOS_DIR}/${clone_relative_dir}"
    cd "${REPOS_DIR}"
    cd "${script_relative_dir}"
    script_fullpath_dir="${PWD}"
    cd "${REPOS_DIR}"
    #echo ${clone_fullpath_dir}
    if [ -x "${script_fullpath_dir}/install.sh" ]
    then
	"${script_fullpath_dir}/install.sh" "${script_fullpath_dir}" "${clone_fullpath_dir}"
    elif [ -r "${script_fullpath_dir}/git-url.txt" -a -r "${script_fullpath_dir}/git-root-dir-name.txt" -a -r "${script_fullpath_dir}/git-commit.txt" ]
    then
	GIT_URL=`cat ${script_fullpath_dir}/git-url.txt`
	GIT_ROOT_DIR=`cat ${script_fullpath_dir}/git-root-dir-name.txt`
	GIT_COMMIT=`cat ${script_fullpath_dir}/git-commit.txt`
	echo ${clone_fullpath_dir} ${GIT_URL} ${GIT_ROOT_DIR} ${GIT_COMMIT}
	set -x
	${CLONE} ${GIT_URL}
	mv ${GIT_ROOT_DIR} ${clone_fullpath_dir}
	cd ${clone_fullpath_dir}
	${CHECKOUT} ${GIT_COMMIT}
	if [ -r "${script_fullpath_dir}/project.clj" ]
	then
	    cp "${script_fullpath_dir}/project.clj" "${clone_fullpath_dir}/project.clj"
	fi
	if [ -r "${script_fullpath_dir}/lint.sh" ]
	then
	    cp "${script_fullpath_dir}/lint.sh" "${clone_fullpath_dir}/lint.sh"
	fi
	if [ -x "${script_fullpath_dir}/modify.sh" ]
	then
	    "${script_fullpath_dir}/modify.sh" "${script_fullpath_dir}" "${clone_fullpath_dir}"
	fi
	cd "${REPOS_DIR}"
	set +x
    else
	echo "Skipping dir -- no git-url.txt found: ${clone_fullpath_dir}"
    fi
done

cd ..
