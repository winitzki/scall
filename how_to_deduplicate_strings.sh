java -Xlog:stringdedup*=debug -XX:+UseG1GC -XX:+UseStringDeduplication -jar dhall.jar --file scall-cli/src/test/resources/yaml-perftest/create_yaml.dhall yaml
