
```dhall
outE r (inE r k) t pt
  === (λ(ep : Exists P) → ep r k) (pack P t pt)
  === (pack P t pt) r k
  -- Use the definition of `pack`.
  === (λ(r : Type) → λ(pack_ : ∀(t_ : Type) → P t_ → r) → pack_ t pt) r k
  === k t pt
```
