args="123456 1234567 12345678"
  for n in $args; do
    for p in $args; do
for a in Sq SqRev; do
      echo -n powerNat$a $n $p
      time dhall type <<< "let f = ./power_nat.dhall sha256:3b7201bfc3d01c5b5a5cfa2b424e7c074165920161b53cbf7318a30787b31568 in assert : Natural/isZero (f.powerNat$a $n $p) === False" > /dev/null
      echo
      echo
    done
  done
done

