-- nonk8s
let S = ./schema.dhall

let items = ./items.dhall

let item = S.get_item 2 items

in  ./yaml_record.dhall item
