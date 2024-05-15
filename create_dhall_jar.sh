sbt scall_cli/assembly
rm -f dhall-cli.jar
cp scall-cli/target/scala-2.13/dhall-cli.jar .
if test -s dhall-cli.jar; then
	echo "Command-line utility created:"
	ls -l dhall-cli.jar
else
	echo "Failed to create command-line utility dhall-cli.jar"
	exit 1
fi

