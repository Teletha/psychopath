#!bin/bash
bee=$JAVA_HOME/lib/bee-0.17.0.jar
if [ ! -e $bee ]; then
  echo $bee is not found, try to download it from network.
  curl -#L -o $bee https://github.com/teletha/bee/raw/master/bee-0.10.0.jar
fi
java -javaagent:$bee -cp $bee bee.Bee "$@"
