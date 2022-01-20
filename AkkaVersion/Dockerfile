FROM gradle:6.9-jdk11 AS build
COPY . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle jarConsole jarServer

FROM openjdk:11.0.11-jre-buster
COPY --from=build /home/gradle/src/build/libs/* /bin/kvs/
WORKDIR /bin/kvs
EXPOSE 25520/tcp
EXPOSE 8080/tcp
ENTRYPOINT ["/bin/bash"]
#CMD ["--help"]
