name=programming_dhall
scala-cli convertMd.scala --   false < $name.md  > generated.tex
(
 scala-cli convertMd.scala --   true < $name.md  > generated.dhall 2> /dev/null
 dhall --explain --file generated.dhall >& generated.log
) &
pdflatex --interaction=batchmode $name.tex
test -s $name.pdf || {
  echo "Error: no PDF file, see errors in $name.log"
  wait
  exit 1
}
makeindex $name.idx
pdflatex --interaction=batchmode $name.tex
fgrep "Output written on $name.pdf" $name.log
wait

