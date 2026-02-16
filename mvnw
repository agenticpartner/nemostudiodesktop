#!/bin/sh
# ----------------------------------------------------------------------------
# Apache Maven Wrapper - run Maven without installing it (version 3.3.2)
# ----------------------------------------------------------------------------

if [ -z "$MAVEN_SKIP_RC" ]; then
  [ -f /usr/local/etc/mavenrc ] && . /usr/local/etc/mavenrc
  [ -f /etc/mavenrc ] && . /etc/mavenrc
  [ -f "$HOME/.mavenrc" ] && . "$HOME/.mavenrc"
fi

if [ -z "$JAVA_HOME" ] && [ -x "/usr/libexec/java_home" ] 2>/dev/null; then
  JAVA_HOME="$(/usr/libexec/java_home)"
  export JAVA_HOME
fi

JAVACMD="${JAVA_HOME}/bin/java"
[ ! -x "$JAVACMD" ] && JAVACMD="$(command -v java)"
if [ ! -x "$JAVACMD" ]; then
  echo "Error: Set JAVA_HOME or ensure java is on PATH." >&2
  exit 1
fi

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
while [ ! -d "$BASE_DIR/.mvn" ] && [ "$BASE_DIR" != "/" ]; do BASE_DIR="$(dirname "$BASE_DIR")"; done
MAVEN_PROJECTBASEDIR="${MAVEN_BASEDIR:-$BASE_DIR}"
export MAVEN_PROJECTBASEDIR

WRAPPER_JAR="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

if [ ! -r "$WRAPPER_JAR" ]; then
  wrapperUrl=$(grep '^wrapperUrl=' "$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties" 2>/dev/null | cut -d= -f2- | tr -d '\r')
  [ -z "$wrapperUrl" ] && wrapperUrl="https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar"
  mkdir -p "$(dirname "$WRAPPER_JAR")"
  if command -v curl >/dev/null; then
    curl -sL -o "$WRAPPER_JAR" "$wrapperUrl" || { rm -f "$WRAPPER_JAR"; exit 1; }
  elif command -v wget >/dev/null; then
    wget -q -O "$WRAPPER_JAR" "$wrapperUrl" || { rm -f "$WRAPPER_JAR"; exit 1; }
  else
    echo "Need curl or wget to download Maven Wrapper." >&2
    exit 1
  fi
fi

exec "$JAVACMD" ${MAVEN_OPTS} -classpath "$WRAPPER_JAR" "-Dmaven.multiModuleProjectDirectory=$MAVEN_PROJECTBASEDIR" $WRAPPER_LAUNCHER "$@"
