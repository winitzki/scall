
let drop = https://prelude.dhall-lang.org/List/drop
let generate = https://prelude.dhall-lang.org/List/generate
let f = \(g : Natural) -> generate g Natural (\(x : Natural) -> x)
in \(g : Natural) -> \(n : Natural) -> drop n Natural (f g)
