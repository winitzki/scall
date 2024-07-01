let GithubActions =
      https://regadas.dev/github-actions-dhall/package.dhall
        sha256:71df44892a17abca817cfb35e2612d117f7fceec55114a6eb76b65a7eea4e6f4

let matrix =
    -- { java = [ "8.0.382", "11.0.21", "17.0.9" ], scala = [ "2.13.13" ] }
      toMap { java = [ "8.0.382", "22.0.1" ], scala = [ "2.12.19", "2.13.13" ] }

let checkout_and_cache =
      [     GithubActions.steps.actions/checkout
        //  { `with` = Some (toMap { submodules = "true" }) }
      , GithubActions.steps.actions/cache
          { path =
              ''
              ~/.sbt
              "~/.cache/coursier"
              ''
          , key = "sbt"
          , hashFiles =
            [ "build.sbt", "project/plugins.sbt", "project/build.properties" ]
          }
      ]

in  GithubActions.Workflow::{
    , name = "scall_build_and_test"
    , on = GithubActions.On::{ push = Some GithubActions.Push::{=} }
    , jobs = toMap
        { checks = GithubActions.Job::{
          , name = Some "Check formatting"
          , runs-on = GithubActions.types.RunsOn.ubuntu-latest
          , steps =
                checkout_and_cache
              # [ GithubActions.steps.actions/setup-java { java-version = "17" }
                , GithubActions.steps.run
                    { run = "sbt scalafmtCheckAll scalafmtSbtCheck" }
                ]
          }
        , pdftutorial = GithubActions.Job::{
          , name = Some "Build and validate the tutorial"
          , runs-on = GithubActions.types.RunsOn.ubuntu-latest
          , steps =
                checkout_and_cache
              # [ GithubActions.steps.actions/setup-java { java-version = "17" }
                , GithubActions.Step::{
                  , name = Some "Setup scala-cli"
                  , uses = Some "VirtusLab/scala-cli-setup@main"
                  }
                , GithubActions.Step::{
                  , name = Some "Setup dhall executable"
                  , uses = Some "dhall-lang/setup-dhall@v4"
                  , `with` = Some (toMap { version = "1.42.0" })
                  }
                , GithubActions.Step::{
                  , name = Some "Setup latex"
                  , uses = Some "zauguin/install-texlive@v3"
                  , `with` = Some (toMap { packages = "scheme-full" })
                  }
                , GithubActions.steps.run
                    { run = "bash tutorial/make_pdf.sh dryrunx" }
                , GithubActions.Step::{
                  , name = Some "Upload tutorial PDF"
                  , uses = Some "actions/upload-artifact@v2"
                  , `with` = Some
                      ( toMap
                          { name = "Tutorial PDF file and logs"
                          , if-no-files-found = "error"
                          , path =
                              ''
                              ./tutorial/programming_dhall.pdf
                              ./tutorial/generated.log
                              ./tutorial/programming_dhall.log''
                          }
                      )
                  }
                ]
          }
        , build = GithubActions.Job::{
          , name = Some "Build"
          , needs = Some [ "checks" ]
          , strategy = Some GithubActions.Strategy::{ matrix }
          , runs-on = GithubActions.types.RunsOn.ubuntu-latest
          , permissions =
              let Permission = GithubActions.types.Permission

              in  let read = GithubActions.types.PermissionAccess.read

                  in  let write = GithubActions.types.PermissionAccess.write

                      in  Some
                            [ { mapKey = Permission.actions, mapValue = read }
                            , { mapKey = Permission.checks, mapValue = write }
                            , { mapKey = Permission.contents, mapValue = read }
                            ]
          , steps =
                checkout_and_cache
              # [ GithubActions.steps.actions/setup-java
                    { java-version = "\${{ matrix.java }}" }
                , GithubActions.steps.run
                    { run =
                        "sbt -DJDK_VERSION=\${{ matrix.java }} ++\${{ matrix.scala }} coverage test coverageReport"
                    }
                , GithubActions.Step::{
                  , name = Some "Report test results"
                  , uses = Some "dorny/test-reporter@v1.7.0"
                  , `if` = Some "success() || failure()"
                  , `with` = Some
                      ( toMap
                          { name = "Test results"
                          , path = "*/target/test-reports/*.xml"
                          , reporter = "java-junit"
                          , fail-on-error = "true"
                          }
                      )
                  }
                , GithubActions.Step::{
                  , name = Some "Upload coverage reports to Codecov"
                  , uses = Some "codecov/codecov-action@v3"
                  , env = Some
                      ( toMap
                          { CODECOV_TOKEN = "\${{ secrets.CODECOV_TOKEN }}" }
                      )
                  }
                ]
          }
        }
    }
