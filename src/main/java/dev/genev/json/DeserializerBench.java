package dev.genev.json;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.gson.Gson;
import dev.genev.json.decoder.ByteOrderBookDecoder;
import dev.genev.json.decoder.ByteTickDecoder;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.simdjson.JsonValue;
import org.simdjson.SimdJsonParser;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * The whole shootout in one file. JMH runs each @Benchmark method for both
 * Throughput and SampleTime modes in separate forks, which gives us a
 * complete picture (ops/s and percentile latencies) from a single harness.
 *
 * All library state (ObjectReader, Gson, SimdJsonParser, Jsonb) is created
 * once in @Setup and reused across invocations — this is how anyone serious
 * about latency actually uses these libraries, and benchmarking them any
 * other way would be comparing setup cost instead of per-message cost.
 */
@State(Scope.Benchmark)
@BenchmarkMode({Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
public class DeserializerBench {

    // ---- payloads ----------------------------------------------------------

    byte[] tickBytes;
    byte[] orderBookBytes;
    String tickString;
    String orderBookString;

    // ---- library handles ---------------------------------------------------

    ObjectReader jacksonTickReader;
    ObjectReader jacksonOrderBookReader;
    Gson gson;
    Jsonb jsonb;
    SimdJsonParser simdjson;

    // ---- custom-decoder reusables -----------------------------------------

    ByteTickDecoder byteTickDecoder;
    ByteOrderBookDecoder byteOrderBookDecoder;
    Tick reusableTick;
    OrderBook reusableOrderBook;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        tickBytes = load("/fixtures/tick.json");
        orderBookBytes = load("/fixtures/orderbook.json");
        tickString = new String(tickBytes, StandardCharsets.UTF_8);
        orderBookString = new String(orderBookBytes, StandardCharsets.UTF_8);

        ObjectMapper mapper = new ObjectMapper();
        jacksonTickReader = mapper.readerFor(Tick.class);
        jacksonOrderBookReader = mapper.readerFor(OrderBook.class);

        gson = new Gson();
        jsonb = JsonbBuilder.create();
        simdjson = new SimdJsonParser();

        byteTickDecoder = new ByteTickDecoder();
        byteOrderBookDecoder = new ByteOrderBookDecoder();
        reusableTick = new Tick();
        reusableOrderBook = new OrderBook();
    }

    @TearDown(Level.Trial)
    public void tearDown() throws Exception {
        jsonb.close();
    }

    private static byte[] load(String resource) throws IOException {
        try (InputStream in = DeserializerBench.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("missing resource " + resource);
            return in.readAllBytes();
        }
    }

    // ====================================================================
    // TICK benchmarks
    // ====================================================================

    @Benchmark
    public Tick tick_jackson() throws IOException {
        return jacksonTickReader.readValue(tickBytes);
    }

    @Benchmark
    public Tick tick_gson() {
        return gson.fromJson(tickString, Tick.class);
    }

    @Benchmark
    public Tick tick_fastjson2() {
        return JSON.parseObject(tickBytes, Tick.class);
    }

    @Benchmark
    public Tick tick_jsonb() {
        return jsonb.fromJson(new ByteArrayInputStream(tickBytes), Tick.class);
    }

    @Benchmark
    public void tick_simdjson(Blackhole bh) {
        JsonValue v = simdjson.parse(tickBytes, tickBytes.length);
        // Pull each field to force the parser to actually visit it; the
        // on-demand API would otherwise skip unread fields.
        bh.consume(v.get("symbol").asString());
        bh.consume(v.get("bid").asDouble());
        bh.consume(v.get("ask").asDouble());
        bh.consume(v.get("bidSize").asLong());
        bh.consume(v.get("askSize").asLong());
        bh.consume(v.get("ts").asLong());
        bh.consume(v.get("seq").asLong());
    }

    @Benchmark
    public Tick tick_customByte() {
        byteTickDecoder.decode(tickBytes, reusableTick);
        return reusableTick;
    }

    // ====================================================================
    // ORDER-BOOK benchmarks
    // ====================================================================

    @Benchmark
    public OrderBook ob_jackson() throws IOException {
        return jacksonOrderBookReader.readValue(orderBookBytes);
    }

    @Benchmark
    public OrderBook ob_gson() {
        // Use a Reader over the byte[] to avoid penalising Gson with a giant
        // String materialisation on the large payload.
        try (InputStreamReader r = new InputStreamReader(
                new ByteArrayInputStream(orderBookBytes), StandardCharsets.UTF_8)) {
            return gson.fromJson(r, OrderBook.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Benchmark
    public OrderBook ob_fastjson2() {
        return JSON.parseObject(orderBookBytes, OrderBook.class);
    }

    @Benchmark
    public OrderBook ob_jsonb() {
        return jsonb.fromJson(new ByteArrayInputStream(orderBookBytes), OrderBook.class);
    }

    @Benchmark
    public void ob_simdjson(Blackhole bh) {
        JsonValue v = simdjson.parse(orderBookBytes, orderBookBytes.length);
        bh.consume(v.get("symbol").asString());
        bh.consume(v.get("ts").asLong());
        bh.consume(v.get("seq").asLong());
        walkLevels(v.get("bids"), bh);
        walkLevels(v.get("asks"), bh);
    }

    private static void walkLevels(JsonValue side, Blackhole bh) {
        Iterator<JsonValue> it = side.arrayIterator();
        while (it.hasNext()) {
            JsonValue lvl = it.next();
            bh.consume(lvl.get("price").asDouble());
            bh.consume(lvl.get("size").asLong());
        }
    }

    @Benchmark
    public OrderBook ob_customByte() {
        byteOrderBookDecoder.decode(orderBookBytes, reusableOrderBook);
        return reusableOrderBook;
    }
}
