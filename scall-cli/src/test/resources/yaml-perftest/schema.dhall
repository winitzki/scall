let Common =
      ./common.dhall
        sha256:1263d3625c08893d5391986c1b560342d4d2f87c7486524ffbe335b0710422f1

let L = < C | E >

let M = < T | E >

let Params =
      { objective : Text
      , metric : Text
      , max_value : Text
      , unit : Text
      , strategy : M
      , extra_selectors : Text
      }

let OtherParams =
      { Type =
          { error_q : Text
          , total_q : Text
          , error_selectors : Text
          , flag : Bool
          }
      , default =
        { error_q = "", total_q = "", error_selectors = "", flag = False }
      }

let emptyParams
    : Params
    = { strategy = M.T
      , objective = ""
      , metric = ""
      , max_value = "0.1"
      , unit = "s"
      , extra_selectors = ""
      }

let Item =
      { l : L
      , name : Text
      , p10 : Text
      , latency : Params
      , other : OtherParams.Type
      }

let emptyItem =
      { l = L.C
      , name = "undefined"
      , p10 = "undefined"
      , latency = emptyParams
      , other = OtherParams.default
      }

let get_item =
      λ(index : Natural) →
      λ(items : List Item) →
        Common.Optional/getOrElse
          Item
          emptyItem
          (Common.List/index index Item items)

let aggregator_for =
      λ(item : Item) → merge { C = "cluster1", E = "env1" } item.l

let aggregator_label_for =
      λ(item : Item) → merge { C = "cluster2", E = "env2" } item.l

let uppercased =
      λ(item : Item) → Text/replace "-" " " (Common.toUppercase item.name)

let S_record_type =
      { apiVersion : Text
      , kind : Text
      , metadata :
          { displayName : Text
          , labels :
              { alert_enabled : Bool
              , authors : List Text
              , item_type : Text
              , qwerty_type : Text
              , version : Text
              }
          , name : Text
          }
      , spec :
          { b10 : Text
          , description : Text
          , indicator :
              { metadata : { name : Text }
              , spec :
                  { ratioMetric :
                      { counter : Bool
                      , good :
                          { metricSource :
                              { spec :
                                  { query : Text
                                  , queryType : Text
                                  , source : Text
                                  }
                              , type : Text
                              }
                          }
                      , total :
                          { metricSource :
                              { spec :
                                  { query : Text
                                  , queryType : Text
                                  , source : Text
                                  }
                              , type : Text
                              }
                          }
                      }
                  }
              }
          , objectives : List { displayName : Text, target : Double }
          , item : Text
          , timeWindow : List { duration : Text, isRolling : Bool }
          }
      }

let E_type =
      { apiVersion : Text
      , kind : Text
      , metadata : { labels : { owner : Text, item : Text }, name : Text }
      , spec :
          { envs : List Text
          , s : List Text
          , variables :
              List { label : Text, name : Text, options : Text, type : Text }
          }
      }

in  { L
    , Item
    , M
    , Params
    , OtherParams
    , emptyParams
    , get_item
    , aggregator_for
    , aggregator_label_for
    , uppercased
    , S_record_type
    , E_type
    }
