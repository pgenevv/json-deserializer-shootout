# json-deserializer-shootout

JMH benchmarks comparing five Java JSON deserializers to a hand-rolled byte
decoder, on two latency-sensitive payloads.

Companion code for the blog post
[Java JSON deserializers: five libraries vs. a hand-rolled byte decoder](https://genev.dev/posts/java-json-deserializers-shootout)
on [genev.dev](https://genev.dev).

## Contenders

| Library         | Maven coordinate                                  | Notes                              |
|-----------------|---------------------------------------------------|------------------------------------|
| Jackson Databind | `com.fasterxml.jackson.core:jackson-databind`    | Reused `ObjectReader` per type     |
| Gson            | `com.google.code.gson:gson`                       | `fromJson(Reader)` on large input  |
| Fastjson2       | `com.alibaba.fastjson2:fastjson2`                 | `parseObject(byte[], Class)`       |
| JSON-B / Yasson | `org.eclipse:yasson` + `org.eclipse.parsson:parsson` | Reused `Jsonb` instance         |
| DSL-JSON        | `com.dslplatform:dsl-json`                        | Reflection-free, byte-array native |
| **custom byte decoder** | (this repo, `dev.genev.json.decoder`)     | Zero-allocation fast path          |

Explicitly excluded:
- simdjson-java was the original pick; its current releases only run on x86
  (AVX2/AVX-512), so it won't load on Apple Silicon or other ARM hosts.
  DSL-JSON takes its slot here.
- Moshi is predominantly Kotlin/Android.
- jsoniter is effectively unmaintained since 2018.

## Payloads

| Name          | Size    | Shape                                            |
|---------------|---------|--------------------------------------------------|
| `tick`        | ~300 B  | single trading tick, fixed schema                |
| `orderBook`   | ~6 KB   | 100 bid levels + 100 ask levels + metadata       |

Fixtures live at [src/main/resources/fixtures/](src/main/resources/fixtures/)
and are read once at JMH trial setup.

## Running it

### Prerequisites

- **JDK 21** on your `PATH` (or set `JAVA_HOME`). Temurin 21 is known to work.
- **No global Maven required.** The repo ships `mvnw`, which downloads Maven
  3.9.9 into `~/.m2/wrapper` the first time you run it.

### One-liner

```bash
chmod +x run.sh mvnw   # first time only, if your git checkout lost +x
./run.sh
```

This builds an uberjar and runs both modes (`Throughput`, `SampleTime`) with
the GC profiler (`-prof gc`), 2 forks, 5×1s warmup iterations, and 10×1s
measurement iterations. Output ends up in `results.json`, which is the file
the blog post's chart script consumes.

### Manual invocation

```bash
./mvnw -DskipTests package
java -jar target/benchmarks.jar \
     -bm thrpt,sample \
     -prof gc \
     -f 2 -wi 5 -i 10 \
     -w 1s -r 1s \
     -rf json -rff results.json
```

To narrow to a single benchmark:

```bash
java -jar target/benchmarks.jar "dev.genev.json.DeserializerBench.tick_customByte"
```

## Results

The canonical run that produced the charts on the blog post is committed as
[results.json](results.json). Diff your machine's output against it — if your
ordering matches but your absolute numbers differ, that's a good sanity
check that the harness is working.

If you haven't run the benchmark yet you'll see
[results.sample.json](results.sample.json) here instead — that's a
representative set of numbers hand-tuned to match published results for each
library on similarly-shaped payloads. It is **not** a measurement from any
machine. The blog post renders real numbers only.

## What the hand-rolled decoder actually does

See [`ByteTickDecoder`](src/main/java/dev/genev/json/decoder/ByteTickDecoder.java)
and [`ByteOrderBookDecoder`](src/main/java/dev/genev/json/decoder/ByteOrderBookDecoder.java).
Summary of the tricks:

1. **Operate on `byte[]`**, never the input `String`. No UTF-8 decode round-trip.
2. **Fixed field order.** The caller knows the message shape; we walk past
   each field name without reading it.
3. **Numbers are parsed by hand** — no `Long.parseLong`, no `Double.parseDouble`.
   Skips the interim String allocation and the general-purpose parser logic.
4. **Symbols are interned** through [`SymbolCache`](src/main/java/dev/genev/json/decoder/SymbolCache.java),
   a tiny open-addressed table keyed by byte content. Known symbols return a
   canonical `String` with zero allocation.
5. **POJO instances are reused.** The caller holds a single `Tick` /
   `OrderBook` and the decoder rewrites its fields in place. `OrderBook`
   preallocates `Level[100]` per side so even the large payload decodes
   without a fresh allocation.

The trade-off: this is **not** a drop-in JSON parser. It assumes ASCII input
with no escape sequences, a fixed field order, and a fixed array depth. Use
it when you control the producer (trading feeds, internal RPC, binary
protocol adapters). Stay on Jackson when you don't.

## License

[MIT](LICENSE).
