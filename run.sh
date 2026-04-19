#!/usr/bin/env bash
# Build the uberjar and run the full benchmark suite.
# Output ends up in ./results.json and is what the blog post's charts consume.
set -euo pipefail

cd "$(dirname "$0")"

mvn -q -DskipTests package

java \
  -jar target/benchmarks.jar \
  -bm thrpt,sample \
  -prof gc \
  -f 2 \
  -wi 5 \
  -i 10 \
  -r 1s \
  -w 1s \
  -rf json \
  -rff results.json
