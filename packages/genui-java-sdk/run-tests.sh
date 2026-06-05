#!/usr/bin/env bash
# Maven-free build+test for the GenUI Java SDK (Maven is unavailable in this environment).
# Usage: bash run-tests.sh [--select <ClassName>]
set -euo pipefail
cd "$(dirname "$0")"

JAR=.tools/junit-platform-console-standalone-1.11.4.jar
JAR_URL=https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar
JAR_SHA256=b016ef6b1c3454d6d7c2c88ce081dabf289699686af6622d6e4e2e1b54b4a2fc
CLASSPATH_SEP=":"
case "$(uname -s)" in
  CYGWIN*|MINGW*|MSYS*) CLASSPATH_SEP=";" ;;
esac

mkdir -p .tools target/classes target/test-classes
if [ ! -f "$JAR" ]; then
  curl -fsSL -o "$JAR" "$JAR_URL"
fi
echo "$JAR_SHA256  $JAR" | sha256sum -c -

rm -rf target/classes/* target/test-classes/*
mapfile -t MAIN_SOURCES < <(find src/main/java -name '*.java')
mapfile -t TEST_SOURCES < <(find src/test/java -name '*.java')
javac -encoding UTF-8 -d target/classes "${MAIN_SOURCES[@]}"
javac -encoding UTF-8 -cp "target/classes${CLASSPATH_SEP}$JAR" -d target/test-classes "${TEST_SOURCES[@]}"
cp -r src/main/resources/* target/classes/ 2>/dev/null || true
cp -r src/test/resources/* target/test-classes/ 2>/dev/null || true

if [ "${1:-}" = "--select" ]; then
  java -jar "$JAR" execute -cp "target/classes${CLASSPATH_SEP}target/test-classes" \
    --select-class "com.huawei.clodsop.genui.core.$2" --details=tree --disable-banner
else
  java -jar "$JAR" execute -cp "target/classes${CLASSPATH_SEP}target/test-classes" \
    --scan-classpath --details=summary --disable-banner
fi
