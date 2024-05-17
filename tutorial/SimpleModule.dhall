-- This file is `./SimpleModule.dhall`.
let UserName = Text
let UserId = Natural
let printUser = λ(name : UserName) → λ(id : UserId) → "User: ${name}[${Natural/show id}]"

let validate : Bool = ./NeedToValidate.dhall -- Import that value from another module.
let test = assert : validate === True   -- Cannot import this module unless `validate` is `True`.

in {
  UserName,
  UserId,
  printUser,
}
