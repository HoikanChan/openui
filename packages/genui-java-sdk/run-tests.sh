#!/usr/bin/env bash
# Maven-free build+test for the GenUI Java SDK (Maven is unavailable in this environment).
# Usage: bash run-tests.sh [--select <ClassName>]
set -euo pipefail
cd "$(dirname "$0")"

JAR=.tools/junit-platform-console-standalone-1.11.4.jar
JAR_URL=https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar

mkdir -p .tools target/classes target/test-classes
[ -f "$JAR" ] || curl -sS -L -o "$JAR" "$JAR_URL"

rm -rf target/classes/* target/test-classes/*
javac -encoding UTF-8 -d target/classes $(find src/main/java -name '*.java')
javac -encoding UTF-8 -cp "target/classes;$JAR" -d target/test-classes $(find src/test/java -name '*.java')
cp -r src/main/resources/* target/classes/ 2>/dev/null || true
cp -r src/test/resources/* target/test-classes/ 2>/dev/null || true

if [ "${1:-}" = "--select" ]; then
  java -jar "$JAR" execute -cp "target/classes;target/test-classes" \
    --select-class "dev.openui.genui.$2" --details=tree --disable-banner
else
  java -jar "$JAR" execute -cp "target/classes;target/test-classes" \
    --scan-classpath --details=summary --disable-banner
fi
