
```dhall
-- Symbolic derivation.
outE r (inE r k) t pt
-- Symbolic derivation. Substitute the definition of outE:
  === (λ(ep : Exists P) → ep r k) (pack P t pt)
```
