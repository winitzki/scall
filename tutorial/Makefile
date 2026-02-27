all: check_dhall_code programming_dhall.pdf 3page-cover.pdf

check_dhall_code: generated.dhall
	dhall text --file generated.dhall --explain >& generated.log
	fgrep -i "example code from the book" generated.log | tee check_dhall_code || echo "Errors in Dhall code, see generated.log"

programming_dhall.pdf: generated.tex programming_dhall.tex
	pdflatex --interaction=batchmode programming_dhall.tex >& /dev/null || true
	test -s programming_dhall.pdf || { echo "Error building PDF file, see programming_dhall.log"; cat programming_dhall.log; exit 1; }
	( makeindex programming_dhall.idx || true ; pdflatex --interaction=batchmode programming_dhall.tex || true) >& /dev/null
	fgrep 'Output written on programming_dhall.pdf' programming_dhall.log

titleback.tex: titleback.tex.src
	LC_ALL=C sed -E -e "s/INSERTGITCOMMIT/$$(git rev-parse HEAD)/; s/INSERTPDFBUILDINFO/$$(date -R) by $$(pdflatex --version | fgrep pdfTeX\ 3)/;" < titleback.tex.src > titleback.tex

generated.tex: programming_dhall.md titleback.tex convertMd.sh convertMd.scala
	bash convertMd.sh tex ../dhall-lang/Prelude

generated.dhall: programming_dhall.md convertMd.sh convertMd.scala
	bash convertMd.sh dhall ../dhall-lang/Prelude

clean:
	rm -f programming_dhall.{pdf,log,out,aux,idx,ind,ilg,toc} generated.{log,dhall,tex} check_dhall_code convertMd.graal convertMd.jar

convertMd.graal: convertMd.scala
	scala-cli --power package convertMd.scala -o convertMd.graal --native-image --force

convertMd.jar: convertMd.scala
	scala-cli --power package convertMd.scala -o convertMd.jar --assembly --force

3page-cover.pdf: cover/monads_good_face.jpg cover/cover-background-2.jpg cover-parameters.tex cover/3page-cover.tex spine.pdf front-cover.pdf back-cover.pdf
	pdflatex --interaction=batchmode cover/3page-cover.tex
	pdflatex --interaction=batchmode cover/3page-cover.tex

spine.pdf: cover/spine.tex cover-parameters.tex
	pdflatex --interaction=batchmode $<

front-cover.pdf: cover/front-cover.tex cover/front-cover-no-bg.tex cover-parameters.tex
	pdflatex --interaction=batchmode $<

back-cover.pdf: cover/back-cover.tex cover/back-cover-no-bg.tex cover-parameters.tex
	pdflatex --interaction=batchmode $<

cover-parameters.tex: cover/cover-parameters.tex.src programming_dhall.pdf
	LC_ALL=C sed -e "s/TOTALPAGES/$$(fgrep 'Output written on programming_dhall.pdf' programming_dhall.log | sed -e 's/^.*(\([0-9]*\) pages.*/\1/')/" < cover/cover-parameters.tex.src > cover-parameters.tex
