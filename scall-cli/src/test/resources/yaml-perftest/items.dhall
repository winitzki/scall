let S = ./schema.dhall

let c1 = S.L.C

let c2 = S.L.E

in    [ { name = "1"
        , p10 = "2"
        , l = c1
        , param1 =
          { strategy = S.M.T
          , objective = "3"
          , metric = "4"
          , max_value = "0.005"
          , unit = "s"
          , extra = ",6\"/7\""
          }
        , other = S.OtherParams::{ error_q = "8", total_q = "9" }
        }
      , { name = "1"
        , p10 = "2"
        , l = c1
        , param1 =
          { strategy = S.M.T
          , objective = "3"
          , metric = "4"
          , max_value = "0.1"
          , unit = "s"
          , extra = ",5"
          }
        , other = S.OtherParams::{ error_q = "6", total_q = "7" }
        }
      , { name = "0"
        , p10 = "1"
        , l = c1
        , param1 =
          { strategy = S.M.T
          , objective = "2"
          , metric = "1"
          , max_value = "1200"
          , unit = "s"
          , extra = ",app=\"3\""
          }
        , other = S.OtherParams::{
          , error_q = "2"
          , total_q = "2"
          , es = ",code=~\"^5.*\""
          }
        }
      , { name = "2"
        , p10 = "3"
        , l = c1
        , param1 =
          { strategy = S.M.T
          , objective = "2"
          , metric = "1"
          , max_value = "0.1"
          , unit = "s"
          , extra = ",2"
          }
        , other = S.OtherParams::{ error_q = "3", total_q = "2" }
        }
      , { name = "1"
        , p10 = "2"
        , l = c1
        , param1 =
          { strategy = S.M.T
          , metric = "1"
          , objective = "2"
          , max_value = "0.5"
          , unit = "s"
          , extra = ",path=\"/path\""
          }
        , other = S.OtherParams::{ error_q = "3", total_q = "2" }
        }
      , { name = "a"
        , p10 = "b"
        , l = c2
        , param1 =
          { strategy = S.M.E
          , objective = "2"
          , metric = "c"
          , max_value = "500"
          , unit = "ms"
          , extra = "d"
          }
        , other = S.OtherParams::{
          , error_q = "e"
          , total_q = "f"
          , es = ",response=~\"Error\""
          }
        }
      , { name = "b"
        , p10 = "c"
        , l = c2
        , param1 =
          { strategy = S.M.E
          , objective = "2"
          , metric = "a"
          , max_value = "100"
          , unit = "ms"
          , extra = "url_path=\"/c\""
          }
        , other = S.OtherParams::{
          , error_q = "d"
          , total_q = "d"
          , es = ",code=~\"^5.*\""
          }
        }
      , { name = "b"
        , p10 = "b.*"
        , l = c1
        , param1.strategy = S.M.E
        , param1.objective = "d"
        , param1.metric = "c"
        , param1.max_value = "3600"
        , param1.extra = "e"
        , param1.unit = "s"
        , other = S.OtherParams::{
          , error_q = "f"
          , total_q = "g"
          , es = ",pod=~\"h-.*\""
          , flag = True
          }
        }
      , { name = "q"
        , p10 = "w"
        , l = c1
        , param1.strategy = S.M.E
        , param1.objective = "2"
        , param1.metric = "e"
        , param1.max_value = "200"
        , param1.extra = "url=\"r\""
        , param1.unit = "ms"
        , other = S.OtherParams::{ error_q = "t", total_q = "u" }
        }
      ]
    : List S.Item
