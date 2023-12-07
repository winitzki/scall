file="github-scala-build-and-test.dhall"
dhall format "$file"
dhall-to-yaml --file "$file" > .github/workflows/build-and-test.yml
