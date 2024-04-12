name=programming_dhall
scala-cli convertMd.sc < $name.md  > generated.tex
pdflatex --interaction=batchmode $name.tex
test -s $name.pdf || {
  echo "Error: no PDF file, see errors in $name.log"
  exit 1
}
makeindex $name.idx
pdflatex --interaction=batchmode $name.tex
fgrep "Output written on $name.pdf" $name.log
