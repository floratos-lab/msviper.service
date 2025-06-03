# Use the official Tomcat image as the base image
# use java 8 to match the msviper war file I have
#FROM tomcat:latest
FROM tomcat:9.0.89-jre8-temurin-jammy

# Install necessary dependencies and R
RUN apt-get update && apt-get install -y \
    r-base \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# this is necessary to install R package httr
RUN apt-get update
RUN apt-get install -y libcurl4-openssl-dev \
    && apt-get install -y libssl-dev

# Copy your web application to the Tomcat webapps directory
COPY target/msviper-service.war /usr/local/tomcat/webapps/

# Copy your R script to a specific directory
ARG VIPER_ROOT=/viper-root
COPY ./msviper_starter.r ${VIPER_ROOT}/scripts/
# Copy supporting R packages (>3000s files, 120 MB)
COPY ./R/hpc ${VIPER_ROOT}/R/hpc
RUN Rscript -e 'install.packages("glue")'
RUN Rscript -e 'install.packages("lifecycle")'
RUN Rscript -e 'install.packages("R6")'
RUN Rscript -e 'install.packages("pkgconfig")'
RUN Rscript -e 'install.packages("fastmap")'
RUN Rscript -e 'install.packages("httr")'

# Expose the default Tomcat port
EXPOSE 8080

# Start Tomcat
CMD ["catalina.sh", "run"]
