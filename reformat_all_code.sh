sbt scalafmtAll scalafmtSbt
dhall format github-scala-build-and-test.dhall

# Find the antlr4 formatter.

jarPath="https/repo1.maven.org/maven2/com/khubla/antlr4formatter/antlr4-formatter-standalone/1.2.1/antlr4-formatter-standalone-1.2.1.jar"

for candidate in "$HOME/Library/Caches/Coursier/v1" "$HOME/.cache/coursier/v1" "$LOCALAPPDATA/Coursier/Cache/v1"; do
  if test -s "$candidate/$jarPath"; then
    echo "Found the JAR at $candidate/$jarPath"
    java -jar "$candidate/$jarPath" --input=scall-core/src/main/resources/dhall.g4
    break
  else
    echo "Did not find the JAR at $candidate/$jarPath"
  fi
done
