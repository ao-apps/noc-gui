#!/bin/bash
set -e

# Java
export JDK_HOME='/opt/jdk1.8.0'
#export JDK_HOME='/usr/lib/jvm/java-11-openjdk-amd64'
export JAVA_HOME="${JDK_HOME}"
export PATH="${JDK_HOME}/bin:${PATH}"

[ -d "$HOME/noc-gui" ] || mkdir -m 700 "$HOME/noc-gui"
cd "$HOME/noc-gui"

export CLASSPATH='/opt/noc-gui/classes'
for JAR in /opt/noc-gui/lib/*.jar
do
	export CLASSPATH="${CLASSPATH}:${JAR}"
done

ulimit -n 40960

#	-Djava.security.debug=access,failure \

exec java \
	-Xms256M \
	-ea:com.aoindustries... \
	-Djava.security.policy='/opt/noc-gui/security.policy.wideopen' \
	-Dsun.net.maxDatagramSockets=4096 \
	-Djavax.net.ssl.trustStore='/opt/noc-gui/truststore' \
	'com.aoindustries.noc.gui.NOC' >& 'noc-gui.err'

#	2>&1 | grep -v "allowed" >&noc-gui.err
