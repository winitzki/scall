scala-cli convertMd.sc < programming_dhall.md  > generated.tex
pdflatex --interaction=batchmode programming_dhall.tex
test -s programming_dhall.pdf || {
  echo "Error: no PDF file, see log in programming_dhall.log"
  exit 1
}
makeindex programming_dhall.idx
pdflatex --interaction=batchmode programming_dhall.tex

