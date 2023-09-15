# msviper.service
service of msviper analysis

## build
`mvn package`

## deploy
`cp target/msviper-service.war ${CATALINA_HOME}/webapps/.`

## dependencies

### R

R needs to be installed in the directory specified by `r.installation` in `application.properties`. version 4.2.2 tested.

### R script

`msviper_starter.r` should be placed in a subdirectory called `scripts` under the directory specified by the value of `viper.root` in `application.properties`.

That script depends on R packages installed in a subdirectory called `R/hpc` under `viper.root`. You may need to reinstall some package using BiocManager, e.g. `BiocManager::install("BiocGenerics")` for that libPath.
