if [ x"$1" == xdryrun ]; then exit 0; fi
test -d tutorial && cd tutorial || echo "No directory 'tutorial'"
rm -f generated.*
name=programming_dhall
command="scala-cli convertMd.scala --"
if test -s convertMd.graal; then
  command="./convertMd.graal"
fi
$command false < $name.md  > generated.tex
$command true < programming_dhall.md  > generated.dhall 2> /dev/null
(
	bash check_dhall_code.sh
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
