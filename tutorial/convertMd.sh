command="scala-cli convertMd.scala --"
if test -s convertMd.graal; then
  command="./convertMd.graal"
fi

if [ x"$1" == xtex ] ; then
  $command false < programming_dhall.md  > generated.tex
elif [ x"$1" == xdhall ] ; then
  $command true < programming_dhall.md  > generated.dhall 2> /dev/null
else
  echo "Invalid usage: $0 tex or $0 dhall"
  exit 1
fi
