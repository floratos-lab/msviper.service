# Use the official Tomcat image as the base image
# use java 8 to match the msviper war file I have
#FROM tomcat:latest
FROM tomcat:9.0.89-jre8-temurin-jammy

# Install necessary dependencies and R
RUN apt-get update && apt-get install -y \
    r-base \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Copy your web application to the Tomcat webapps directory
COPY target/msviper-service.war /usr/local/tomcat/webapps/

# Copy your R script to a specific directory
COPY ./msviper_starter.r /usr/local/bin/msviper_starter.r

# Expose the default Tomcat port
EXPOSE 8080

# Start Tomcat
CMD ["catalina.sh", "run"]
