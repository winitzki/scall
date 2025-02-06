all: check_dhall_code programming_dhall.pdf

check_dhall_code: generated.dhall
	dhall text --file generated.dhall --explain >& generated.log
	fgrep -i "example code from the book" generated.log | tee check_dhall_code || echo "Errors in Dhall code, see generated.log"

programming_dhall.pdf: generated.tex programming_dhall.tex
	pdflatex --interaction=batchmode programming_dhall.tex >& /dev/null || true
	test -s programming_dhall.pdf || { echo "Error building PDF file, see programming_dhall.log"; cat programming_dhall.log; exit 1; }
	( makeindex programming_dhall.idx || true ; pdflatex --interaction=batchmode programming_dhall.tex || true) >& /dev/null
	fgrep "Output written on programming_dhall.pdf" programming_dhall.log

generated.tex: programming_dhall.md convertMd.sh convertMd.scala
	bash convertMd.sh tex

generated.dhall: programming_dhall.md convertMd.sh convertMd.scala
	bash convertMd.sh dhall

clean:
	rm -f programming_dhall.{pdf,log,out,aux,idx,ind,ilg,toc} generated.{log,dhall,tex} check_dhall_code

convertMd.graal: convertMd.scala
	scala-cli --power package convertMd.scala -o convertMd.graal --native-image --force

convertMd.jar: convertMd.scala
	scala-cli --power package convertMd.scala -o convertMd.jar --assembly --force
