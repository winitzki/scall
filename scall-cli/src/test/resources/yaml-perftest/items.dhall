let S = ./schema.dhall

let c1 = S.L.C

let c2 = S.L.E

in    [ { name = "1"
        , p10 = "2"
        , l = c1
        , latency =
          { strategy = S.M.T
          , objective = "3"
          , metric = "4"
          , max_value = "0.005"
          , unit = "s"
          , extra_selectors = ",6\"/7\""
          }
        , other = S.OtherParams::{ error_q = "8", total_q = "9" }
        }
      , { name = "1"
        , p10 = "2"
        , l = c1
        , latency =
          { strategy = S.M.T
          , objective = "3"
          , metric = "4"
          , max_value = "0.1"
          , unit = "s"
          , extra_selectors = ",5"
          }
        , other = S.OtherParams::{ error_q = "6", total_q = "7" }
        }
      , { name = "0"
        , p10 = "1"
        , l = c1
        , latency =
          { strategy = S.M.T
          , objective = "2"
          , metric = "1"
          , max_value = "1200"
          , unit = "s"
          , extra_selectors = ",app=\"3\""
          }
        , other = S.OtherParams::{
          , error_q = "2"
          , total_q = "2"
          , error_selectors = ",code=~\"^5.*\""
          }
        }
      , { name = "2"
        , p10 = "3"
        , l = c1
        , latency =
          { strategy = S.M.T
          , objective = "2"
          , metric = "1"
          , max_value = "0.1"
          , unit = "s"
          , extra_selectors = ",2"
          }
        , other = S.OtherParams::{ error_q = "3", total_q = "2" }
        }
      , { name = "1"
        , p10 = "2"
        , l = c1
        , latency =
          { strategy = S.M.T
          , metric = "1"
          , objective = "2"
          , max_value = "0.5"
          , unit = "s"
          , extra_selectors = ",path=\"/path\""
          }
        , other = S.OtherParams::{ error_q = "3", total_q = "2" }
        }
      , { name = "a"
        , p10 = "b"
        , l = c2
        , latency =
          { strategy = S.M.E
          , objective = "2"
          , metric = "c"
          , max_value = "500"
          , unit = "ms"
          , extra_selectors = "d"
          }
        , other = S.OtherParams::{
          , error_q = "e"
          , total_q = "f"
          , error_selectors = ",response=~\"Error\""
          }
        }
      , { name = "b"
        , p10 = "c"
        , l = c2
        , latency =
          { strategy = S.M.E
          , objective = "2"
          , metric = "a"
          , max_value = "100"
          , unit = "ms"
          , extra_selectors = "url_path=\"/c\""
          }
        , other = S.OtherParams::{
          , error_q = "d"
          , total_q = "d"
          , error_selectors = ",code=~\"^5.*\""
          }
        }
      , { name = "b"
        , p10 = "b.*"
        , l = c1
        , latency.strategy = S.M.E
        , latency.objective = "d"
        , latency.metric = "c"
        , latency.max_value = "3600"
        , latency.extra_selectors = "e"
        , latency.unit = "s"
        , other = S.OtherParams::{
          , error_q = "f"
          , total_q = "g"
          , error_selectors = ",pod=~\"h-.*\""
          , flag = True
          }
        }
      , { name = "q"
        , p10 = "w"
        , l = c1
        , latency.strategy = S.M.E
        , latency.objective = "2"
        , latency.metric = "e"
        , latency.max_value = "200"
        , latency.extra_selectors = "url=\"r\""
        , latency.unit = "ms"
        , other = S.OtherParams::{ error_q = "t", total_q = "u" }
        }
      ]
    : List S.Item
