scala-cli convertMd.sc < programming_dhall.md  > generated.tex
pdflatex --interaction=batchmode programming_dhall.tex
makeindex programming_dhall.idx
pdflatex --interaction=batchmode programming_dhall.tex
test -s programming_dhall.pdf || echo "Error: no PDF file"

