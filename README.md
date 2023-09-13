# msviper.service
service of msviper analysis

## build
`mvn package`

## deploy
`cp target/msviper-service.war ${CATALINA_HOME}/webapps/.`

## R script

`msviper_starter.r` should be placed in a subdirectory called `scripts` under the directory specified by the value of `viper.root` in `application.properties`.

That script depends on `viper` package, which must be installed by BiocManager, `BiocManager::install("viper")`.
