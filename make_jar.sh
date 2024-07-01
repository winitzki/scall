sbt scall_cli/assembly
rm -f dhall.jar
cp scall-cli/target/scala-*/dhall-cli.jar dhall.jar
java -jar dhall.jar --help
