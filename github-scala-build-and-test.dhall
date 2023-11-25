let GithubActions =
      https://regadas.dev/github-actions-dhall/package.dhall
        sha256:71df44892a17abca817cfb35e2612d117f7fceec55114a6eb76b65a7eea4e6f4

let matrix =
      toMap { java = [ 
--"8.0.382", 
--"11.0.21", 
"17.0.9",
 ], scala = [ 
"2.13.11",
 ] }

let setup =
      [ GithubActions.steps.actions/checkout // { `with` = Some (toMap { submodules = "true" }) }
      , GithubActions.steps.actions/cache
          { path =
              ''
              ~/.sbt
              "~/.cache/coursier"
              ''
          , key = "sbt"
          , hashFiles =
            [ "build.sbt"
            , "project/plugins.sbt"
            , "project/build.properties"
            ]
          }
      ]

in  GithubActions.Workflow::{
    , name = "scall_build_and_test"
    , on = GithubActions.On::{
      , push = Some GithubActions.Push::{=}
      --, pull_request = Some GithubActions.PullRequest::{=} -- disabling this will disable builds in PRs submitted from other people's forks
      }
    , jobs = toMap
        { checks = GithubActions.Job::{
          , name = Some "Check formatting"
          , runs-on = GithubActions.types.RunsOn.ubuntu-latest
          , steps =
                setup
              # [ GithubActions.steps.actions/setup-java { java-version = "17" }
                , GithubActions.steps.run
                    { run = "sbt scalafmtCheckAll scalafmtSbtCheck" }
                ]
          }
        , build = GithubActions.Job::{
          , name = Some "Build"
          , needs = Some [ "checks" ]
          , strategy = Some GithubActions.Strategy::{ matrix }
          , runs-on = GithubActions.types.RunsOn.ubuntu-latest
          , steps =
                setup
              # [
                , GithubActions.steps.actions/setup-java
                    { java-version = "\${{ matrix.java}}" }
                , GithubActions.steps.run
                    { run = "sbt -DJDK_VERSION=\${{ matrix.java}} \"++\${{ matrix.scala}} test\"" }
                , GithubActions.Step::{
                                name = Some "Report test results",
                                uses = Some "dorny/test-reporter@v1",
                                --`if` = Some "always",
                                `with` = Some (toMap {
                                    name = "SBT tests",
                                    path = "target/test-reports/*.xml",
                                    reporter = "java-junit",
                                    --fail-on-error = "true",
                                  }),
                 }
                ]
          }
        }
    }
