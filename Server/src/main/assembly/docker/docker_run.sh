#! /bin/bash

echo "							 "
echo "            _  _____ _____ "  
echo "  _ __ ___ | |/ /_ _|_   _|" 
echo " |\'_\` _ \|\' / | |  | |  "   
echo " | | | | | | . \ | |  | |  "
echo " |_| |_| |_|_|\_\___| |_|  "   
echo "							 "

if [ "$ROQUITO_HOME" = "" ]
then
   echo "ROQUITO_HOME not set. Please set the ROQUITO_HOME value to your roquito directory."
else
   echo "ROQUITO_HOME:".$ROQUITO_HOME
fi
export JAVA_HOME=$(/usr/libexec/java_home -v 1.7)

DIR=$(cd `dirname $0` && pwd)
cd $DIR

echo "Starting docker build for image Roquito at"
echo $DIR
docker build -t roquito .

echo "Running docker build for image Roquito"
docker run -d -p 8080:8080 -e SPRING_CONFIG_LOCATION=$ROQUITO_HOME/config/application.properties roquito
echo "Running Roquito"