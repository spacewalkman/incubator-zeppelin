#!/bin/bash
mvn clean 
cd zeppelin-web
bower install --allow-root
grunt build
cd ..
mvn  install -Pbuild-distr -Pspark-2.0 -Dspark.version=2.0.1 -Dpy4j.version=0.10.3  -Phadoop-2.7 -Dhadoop.version=2.7.2 -Psparkr -Ppyspark -Dmaven.findbugs.enable=false -Drat.skip=true -Dcheckstyle.skip=true -Denforcer.skip=true -Dcobertura.skip=true -DskipTests -Pscala-2.11  -Dscala.version=2.11.8  -X
