test -d tutorial && cd tutorial
name=programming_dhall
scala-cli convertMd.scala --   false < $name.md  > generated.tex
(
 scala-cli convertMd.scala --   true < $name.md  > generated.dhall 2> /dev/null
 dhall --explain --file generated.dhall >& generated.log
 fgrep -q  "Example code from the book was evaluated successfully." generated.log || echo "Errors in Dhall code, see generated.log"
) &
pdflatex --interaction=batchmode $name.tex >& /dev/null
test -s $name.pdf || {
  echo "Error building PDF file, see $name.log"
  wait
  exit 1
}
(
makeindex $name.idx
pdflatex --interaction=batchmode $name.tex
) >& /dev/null
wait
fgrep "Output written on $name.pdf" $name.log
