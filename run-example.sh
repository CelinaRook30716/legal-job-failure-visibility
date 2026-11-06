#!/bin/sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/legal-job-visibility-classes"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" src/main/java/legaljobs/*.java
java -cp "$BUILD_DIR" legaljobs.DeadlineFollowUpExample
