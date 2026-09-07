#!/usr/bin/env bash
export MAVEN_OPTS="-Xms1024m -Xmx6096m -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
if [[ "$1" = 'd' ]]
then cd .. && mvn clean install -DskipTests -Plocal -pl !arnold-server && cd arnold-server
fi
TRUSTSTORE_PASS="${PHONEPE_TRUSTSTORE_PASS:-changeit}"
keytool -import -alias phonepecacert -file cert/phonepecacert.pem -keystore "${PHONEPE_TRUSTSTORE:-$JAVA_HOME/lib/security/cacerts}" -storetype JKS -noprompt -storepass "${TRUSTSTORE_PASS}"
keytool -import -alias ca-cert-aerospike -file cert/ca-cert-aerospike.pem -keystore "${PHONEPE_TRUSTSTORE:-$JAVA_HOME/lib/security/cacerts}" -storetype JKS -noprompt -storepass "${TRUSTSTORE_PASS}"
mvn compile -Plocal exec:java -Dexec.mainClass="com.phonepe.devx.arnold.server.ArnoldApplication" -Duser.timezone=IST -DlocalConfig=true -Dexec.args="server config/local.yml"
#mvn clean install -DskipTests -Plocal && java -jar -XX:+UseG1GC -Xms1g -Xmx1g -DlocalConfig=true -Ddb.shards=1 target/arnold-server-1.0-SNAPSHOT.jar server config/local.yml