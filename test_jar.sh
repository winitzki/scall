set -e
d="dhall.jar"

if [ x"$1" == xverbose ]; then
  jopt="-verbose:class"
else
  jopt=""
fi

log=/tmp/test_jar.log

j="java $jopt -jar $d"

$j --help | tee -a $log | grep -- 'Path to the input Dhall file'

echo '1 + 2 + 3' | $j | tee -a $log | grep '^6$'

echo '1 + 2 + 3' | $j type | tee -a $log | grep '^Natural$'

echo '1 + 2 + 3' | $j hash | tee -a $log | grep '^sha256:a979378a73b6c13da3e4e3c89cc1589220e71c107b051af1e145ea5c26db0d69'

echo '"1 + 2 + 3"' | $j | tee -a $log | grep '^"1 + 2 + 3"$'

echo '"1 + 2 + 3"' | $j text | tee -a $log | grep '^1 + 2 + 3$'

if [ x"$1" != xverbose ]; then

echo '1 + 2 + 3' | $j encode | tee -a $log | base64 | grep '^gg8G$'

echo '1 + 2 + 3' | $j encode | tee -a $log | $j decode | grep '^6$'

fi

$j --file scall-cli/src/test/resources/jar-tests/2.dhall | tee -a $log | grep '^2$'

$j --file scall-cli/src/test/resources/jar-tests/4.dhall text | tee -a $log | grep '^abc$'

$j --file scall-cli/src/test/resources/jar-tests/4.dhall | tee -a $log | grep '^"abc"$'

$j --file scall-cli/src/test/resources/jar-tests/3.dhall --output /tmp/3.yaml  yaml | tee -a $log
diff scall-cli/src/test/resources/jar-tests/3.yaml /tmp/3.yaml

$j --file /tmp/nonexisting_file_12345 2>&1 | tee -a $log | grep 'FileNotFound'

echo "Tests successful."
