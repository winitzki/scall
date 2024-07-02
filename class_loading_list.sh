export JAVA_OPTS="-verbose:class"
log="/tmp/class_loading_test.log"
sbt -J-verbose:class test > "$log"
