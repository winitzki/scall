scala-cli convertMd.sc < programming_dhall.md  > generated.tex
pdflatex --interaction=batchmode programming_dhall.tex
makeindex programming_dhall.idx
pdflatex --interaction=batchmode programming_dhall.tex
