# Build the jar first: mvn -f arnold-backend/pom.xml -DskipTests package
FROM eclipse-temurin:17-jre

RUN useradd --create-home --uid 10001 arnold
WORKDIR /app
COPY target/arnold-server.jar /app/arnold-server.jar
COPY config/local.yml /app/config/arnold.yml
USER arnold

EXPOSE 8090 8091
ENV JAVA_OPTS="-XX:+UseG1GC -Xms512m -Xmx1g"
# Mount a different config at /app/config/arnold.yml, or pass settings through the environment variables it references.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/arnold-server.jar server /app/config/arnold.yml"]
