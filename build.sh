#!/bin/bash
set -e
if [ -n "$1" ]; then
  TOMCAT_HOME="$1"
fi

if [ -z "$TOMCAT_HOME" ]; then
  echo "Please set TOMCAT_HOME to your Tomcat installation path or pass it as the first argument." >&2
  echo "Usage: $0 /opt/tomcat/apache-tomcat-9.0.118" >&2
  exit 1
fi
if [ -z "$TOMCAT_HOME" ]; then
  echo "Please set TOMCAT_HOME to your Tomcat installation path." >&2
  exit 1
fi

SRC_DIR="$(pwd)/src"
JAR_FILE="$(pwd)/framework.jar"
LIB_DIR="$TOMCAT_HOME/lib"
TMP_DIR=$(mktemp -d)

find "$SRC_DIR" -name '*.java' > "$TMP_DIR/framework_sources.txt"
javac -cp "$LIB_DIR/servlet-api.jar" -d "$TMP_DIR" @"$TMP_DIR/framework_sources.txt"
jar cf "$JAR_FILE" -C "$TMP_DIR" .
rm -rf "$TMP_DIR"

echo "Built $JAR_FILE"
