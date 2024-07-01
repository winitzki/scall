set -e
d="dhall.jar"

java -jar "$d" --help | grep -- 'Path to the input Dhall file'

echo '1 + 2 + 3' | java -jar "$d" | grep '^6$'

echo '1 + 2 + 3' | java -jar "$d" type | grep '^Natural$'

echo '1 + 2 + 3' | java -jar "$d" hash | grep '^sha256:a979378a73b6c13da3e4e3c89cc1589220e71c107b051af1e145ea5c26db0d69'

echo '"1 + 2 + 3"' | java -jar "$d" | grep '^"1 + 2 + 3"$'

echo '"1 + 2 + 3"' | java -jar "$d" text | grep '^1 + 2 + 3$'

echo '1 + 2 + 3' | java -jar "$d" encode | base64 | grep '^gg8G$'

echo '1 + 2 + 3' | java -jar "$d" encode | java -jar "$d" decode | grep '^6$'

java -jar "$d" --file scall-cli/src/test/resources/jar-tests/2.dhall | grep '^2$'

java -jar "$d" --file scall-cli/src/test/resources/jar-tests/4.dhall text | grep '^abc$'

java -jar "$d" --file scall-cli/src/test/resources/jar-tests/4.dhall | grep '^"abc"$'

java -jar "$d" --file scall-cli/src/test/resources/jar-tests/3.dhall yaml > /tmp/3.yaml
diff scall-cli/src/test/resources/jar-tests/3.yaml /tmp/3.yaml

java -jar "$d" --file /tmp/nonexisting_file_12345 2>&1 | grep 'FileNotFound'

echo "Tests successful."
