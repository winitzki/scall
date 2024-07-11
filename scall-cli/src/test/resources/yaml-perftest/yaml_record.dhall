let S = ./schema.dhall

in  λ(item : S.Item) →
      let aggr = S.aggregator_for item

      let author = env:USER as Text

      in    { apiVersion = "v1"
            , kind = "k"
            , metadata =
              { displayName = "${S.uppercased item} name1"
              , labels =
                { alert_enabled = False
                , authors = [ author ]
                , item_type = "type1"
                , qwerty_type = "type1"
                , version = "0.0.0"
                }
              , name = "${item.name}-name1"
              }
            , spec =
              { b10 = "method1"
              , description = "Description of ${S.uppercased item}"
              , indicator =
                { metadata.name = "${item.name}-name1"
                , spec.ratioMetric
                  =
                  { counter = True
                  , good.metricSource
                    =
                    { spec =
                      { query = "begin ${aggr} continue \"${item.p10}\" end"
                      , queryType = "type2"
                      , source = "source2"
                      }
                    , type = "type3"
                    }
                  , total.metricSource
                    =
                    { spec =
                      { query = "begin ${aggr} continue \"${item.p10}\" end"
                      , queryType = "type4"
                      , source = "source3"
                      }
                    , type = "type5"
                    }
                  }
                }
              , objectives =
                [ { displayName = "${S.uppercased item} name2", target = 0.0 } ]
              , item = "${item.name}"
              , timeWindow = [ { duration = "0", isRolling = True } ]
              }
            }
          : S.S_record_type
