#!/bin/sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/legal-job-visibility-test-classes"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" src/main/java/legaljobs/*.java src/test/java/legaljobs/LegalJobMonitorTest.java
java -cp "$BUILD_DIR" legaljobs.LegalJobMonitorTest
