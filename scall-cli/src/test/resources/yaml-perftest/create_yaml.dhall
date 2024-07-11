-- nonk8s
-- Schema: https://raw.githubusercontent.com/OpenS/OpenS/main/schemas/v1/openS.schema.json
let S = ./schema.dhall

let items = ./items.dhall

let item = S.get_item 2 items

in  ./yaml_record.dhall item
