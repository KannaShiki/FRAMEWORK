#!/bin/bash
set -e

if [ -n "$1" ]; then
  TOMCAT_HOME="$1"
fi

find_servlet_jar() {
  if [ -n "$TOMCAT_HOME" ] && [ -f "$TOMCAT_HOME/lib/servlet-api.jar" ]; then
    echo "$TOMCAT_HOME/lib/servlet-api.jar"
    return 0
  fi

  if [ -n "$TOMCAT_HOME" ] && [ -f "$TOMCAT_HOME/lib/javax.servlet-api.jar" ]; then
    echo "$TOMCAT_HOME/lib/javax.servlet-api.jar"
    return 0
  fi

  local jar
  jar=$(find /home/rhia/.m2/repository -path '*/javax.servlet-api-*.jar' ! -name '*sources.jar' ! -name '*javadoc.jar' 2>/dev/null | head -n 1)
  if [ -n "$jar" ]; then
    echo "$jar"
    return 0
  fi

  jar=$(find / -path '*/lib/servlet-api.jar' 2>/dev/null | head -n 1)
  if [ -n "$jar" ]; then
    echo "$jar"
    return 0
  fi

  return 1
}

SERVLET_JAR=$(find_servlet_jar || true)
if [ -z "$SERVLET_JAR" ]; then
  echo "Aucun jar Servlet compatible javax.servlet n'a été trouvé." >&2
  echo "Usage: $0 /opt/tomcat/apache-tomcat-9.0.118" >&2
  exit 1
fi

SRC_DIR="$(pwd)/src"
JAR_FILE="$(pwd)/framework.jar"
TMP_DIR=$(mktemp -d)

find "$SRC_DIR" -name '*.java' > "$TMP_DIR/framework_sources.txt"
javac -cp "$SERVLET_JAR" -parameters -d "$TMP_DIR" @"$TMP_DIR/framework_sources.txt"
jar cf "$JAR_FILE" -C "$TMP_DIR" .
rm -rf "$TMP_DIR"

echo "Built $JAR_FILE using $SERVLET_JAR"
