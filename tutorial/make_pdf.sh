if [ x"$1" == xdryrun ]; then exit 0; fi
test -d tutorial && cd tutorial
name=programming_dhall
scala-cli convertMd.scala --   false < $name.md  > generated.tex
(
 scala-cli convertMd.scala --   true < $name.md  > generated.dhall 2> /dev/null
 dhall text --file generated.dhall --explain >& generated.log
 fgrep -i "example code from the book" generated.log || echo "Errors in Dhall code, see generated.log"
) &
pdflatex --interaction=batchmode $name.tex >& /dev/null
test -s $name.pdf || {
  echo "Error building PDF file, see $name.log"
	cat $name.log
  wait
  exit 1
}
(
makeindex $name.idx
pdflatex --interaction=batchmode $name.tex
) >& /dev/null
wait
fgrep "Output written on $name.pdf" $name.log
