#!/usr/bin/env node
// Generate a sample JMH-format results file so the blog chart pipeline has
// something realistic to render before anyone runs the real benchmark.
// Numbers are hand-tuned to match published JMH results for each library on
// similarly-shaped payloads; they are NOT measurements from this machine.
//
// Overwrite with the real file (`results.json`) once you've run ./run.sh.

import { writeFileSync } from "node:fs";

// [name, avgNsPerOp, bytesPerOp]
const tick = [
  ["tick_customByte",  80,    0],
  ["tick_fastjson2",  430,  112],
  ["tick_simdjson",   560,  168],
  ["tick_jackson",    730,  152],
  ["tick_gson",      1620,  592],
  ["tick_jsonb",     3480, 1248],
];

const ob = [
  ["ob_customByte",  1850,     0],
  ["ob_simdjson",    3450,  3264],
  ["ob_fastjson2",   9100,  9856],
  ["ob_jackson",    15200, 11264],
  ["ob_gson",       31800, 21600],
  ["ob_jsonb",      55400, 34560],
];

const jmhMeta = {
  jmhVersion: "1.37",
  threads: 1,
  forks: 2,
  warmupIterations: 5,
  warmupTime: "1 s",
  measurementIterations: 10,
  measurementTime: "1 s",
  vmName: "OpenJDK 64-Bit Server VM",
  vmVersion: "21.0.4+7-LTS",
  jdkVersion: "21.0.4",
  jvm: "(representative sample — not a real run)",
  jvmArgs: ["--add-modules", "jdk.incubator.vector"],
  params: {},
};

function throughput(name, avgNs, bytesOp) {
  const opsPerSec = 1e9 / avgNs;
  const err = opsPerSec * 0.02;
  return {
    ...jmhMeta,
    benchmark: `dev.genev.json.DeserializerBench.${name}`,
    mode: "thrpt",
    primaryMetric: {
      score: opsPerSec,
      scoreError: err,
      scoreConfidence: [opsPerSec - err, opsPerSec + err],
      scorePercentiles: {},
      scoreUnit: "ops/s",
      rawData: [[opsPerSec]],
    },
    secondaryMetrics: {
      "·gc.alloc.rate.norm": {
        score: bytesOp,
        scoreError: 0,
        scoreConfidence: [bytesOp, bytesOp],
        scorePercentiles: {},
        scoreUnit: "B/op",
        rawData: [[bytesOp]],
      },
    },
  };
}

function sample(name, avgNs) {
  // Construct a realistic percentile curve: p50 ~= avg, p99 ~3x, p999 ~8x.
  const p50 = avgNs * 0.92;
  const p90 = avgNs * 1.3;
  const p99 = avgNs * 2.8;
  const p999 = avgNs * 7.5;
  const p9999 = avgNs * 22;
  return {
    ...jmhMeta,
    benchmark: `dev.genev.json.DeserializerBench.${name}`,
    mode: "sample",
    primaryMetric: {
      score: avgNs,
      scoreError: avgNs * 0.03,
      scoreConfidence: [avgNs * 0.97, avgNs * 1.03],
      scorePercentiles: {
        "0.0": avgNs * 0.5,
        "50.0": p50,
        "90.0": p90,
        "95.0": avgNs * 1.8,
        "99.0": p99,
        "99.9": p999,
        "99.99": p9999,
        "100.0": avgNs * 60,
      },
      scoreUnit: "ns/op",
      rawData: [[]],
    },
  };
}

const out = [];
for (const [n, ns, b] of [...tick, ...ob]) {
  out.push(throughput(n, ns, b));
  out.push(sample(n, ns));
}

writeFileSync(
  new URL("./results.sample.json", import.meta.url),
  JSON.stringify(out, null, 2) + "\n",
);
console.log(`wrote ${out.length} result rows to results.sample.json`);
