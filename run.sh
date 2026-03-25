#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/java"
mvn -q install -DskipTests
mvn -pl app javafx:run
