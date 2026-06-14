#!/bin/bash
set -e

if [ -z "$TOMCAT_HOME" ]; then
  echo "Please set TOMCAT_HOME to your Tomcat installation path." >&2
  exit 1
fi

SRC_DIR="$(pwd)/src"
OUT_DIR="$(pwd)/out"
JAR_FILE="$(pwd)/framework.jar"
LIB_DIR="$TOMCAT_HOME/lib"

mkdir -p "$OUT_DIR"
find "$SRC_DIR" -name '*.java' > /tmp/framework_sources.txt
javac -cp "$LIB_DIR/servlet-api.jar" -d "$OUT_DIR" @/tmp/framework_sources.txt
jar cf "$JAR_FILE" -C "$OUT_DIR" .

echo "Built $JAR_FILE"
