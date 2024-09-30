 test -d tutorial && cd tutorial
 scala-cli convertMd.scala --   true < programming_dhall.md  > generated.dhall 2> /dev/null
 dhall text --file generated.dhall --explain >& generated.log
 fgrep -i "example code from the book" generated.log || echo "Errors in Dhall code, see generated.log"

