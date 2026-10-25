#!/usr/bin/env bash
# Runs the server locally against config/local.yml. Pass "d" to rebuild dependent modules first.
set -euo pipefail
export MAVEN_OPTS="${MAVEN_OPTS:--Xms1024m -Xmx4096m -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005}"
if [[ "${1:-}" = 'd' ]]; then
  (cd .. && mvn clean install -DskipTests -pl '!arnold-server')
fi
mvn compile exec:java -Dexec.mainClass="com.phonepe.arnold.server.ArnoldApplication" -Dexec.args="server config/local.yml"
