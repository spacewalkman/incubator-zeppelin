#!/bin/bash
mvn clean 
cd zeppelin-web
bower install --allow-root
grunt build
cd ..
mvn  install -Pbuild-distr -Pspark-1.6 -Dspark.version=1.6.2 -Phadoop-2.7 -Dhadoop.version=2.7.2 -Psparkr -Ppyspark -Dmaven.findbugs.enable=false -Drat.skip=true -Dcheckstyle.skip=true -Denforcer.skip=true -Dcobertura.skip=true -DskipTests -X
