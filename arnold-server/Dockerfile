FROM registry.example.com/image

EXPOSE 8080
EXPOSE 8081

VOLUME /var/log/arnold-server

ADD target/arnold-server.jar arnold-server.jar

ENTRYPOINT exec java -jar -XX:+${GC_ALGO-UseG1GC} -Xms${JAVA_PROCESS_MIN_HEAP-1g} -Xmx${JAVA_PROCESS_MAX_HEAP-1g} ${JAVA_OPTS} arnold-server.jar server /rosey/config.yml