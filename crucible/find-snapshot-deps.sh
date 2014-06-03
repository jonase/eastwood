#! /bin/bash

grep -i snapshot project-clj-files/*/project.clj | grep -v 'clojure \"1.7.0-master-SNAPSHOT\"' | grep -v '(defproject ' | grep -v '\"sonatype-snapshots\"' | grep -v ':snapshots false' | grep -v ':snapshots true' | grep -v ':description '
