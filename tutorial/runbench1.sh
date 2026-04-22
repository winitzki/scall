a=$1
echo "let b = ./BenchNat.dhall in assert : b.test$a (env:N ? 3) (env:S ? 3)" > xbench.dhall ; cat xbench.dhall; nu benchmark_sortnat.nu xbench.dhall $2 $3


