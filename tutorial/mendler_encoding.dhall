let C =
      ./church_large_natural.dhall
sha256:f0840a3da6b02187f6dacbce5d7aa5c9433ef307cfc97b81abf8cbaec5736257

let F = C.F

let LFix = C.LFix

let big_church_natural = C.large_natural

let a =
-- This takes about 1 second.
Natural/fold 1000 Natural (\(i : Natural) -> if C.church_is_zero big_church_natural then i else i + 1) 0

let b =
-- This takes about 6 seconds.
Natural/fold 1000 Natural (\(i : Natural) -> if C.church_is_zero (C.church_predecessor big_church_natural) then i else i + 1) 0

in  {a, b}
