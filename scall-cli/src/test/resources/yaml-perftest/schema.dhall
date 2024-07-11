let Common =
      ./common.dhall

let L = < C | E >

let M = < T | E >

let Params =
      { objective : Text
      , metric : Text
      , max_value : Text
      , unit : Text
      , strategy : M
      , extra : Text
      }

let OtherParams =
      { Type =
          { error_q : Text
          , total_q : Text
          , es : Text
          , flag : Bool
          }
      , default =
        { error_q = "", total_q = "", es = "", flag = False }
      }

let emptyParams
    : Params
    = { strategy = M.T
      , objective = ""
      , metric = ""
      , max_value = "0.1"
      , unit = "s"
      , extra = ""
      }

let Item =
      { l : L
      , name : Text
      , p10 : Text
      , param1 : Params
      , other : OtherParams.Type
      }

let emptyItem =
      { l = L.C
      , name = "undefined"
      , p10 = "undefined"
      , param1 = emptyParams
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
