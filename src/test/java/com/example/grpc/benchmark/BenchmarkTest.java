package com.example.grpc.benchmark;

import com.google.common.util.concurrent.RateLimiter;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class BenchmarkTest {
    private RateLimiter rateLimiter;

    @Param({"50"}) // QPS values to test
    private int targetQps;

    @Param({"3"})
    private String hedgeAttempts;
    private Server server;
    private ManagedChannel channel;
    private BenchmarkServiceGrpc.BenchmarkServiceBlockingStub blockingStub;

    @Setup
    public void setup() throws IOException {
        // Initialize rate limiter with target QPS
        rateLimiter = RateLimiter.create(targetQps);
        // Start the server with exponential delay (mean: 200ms)
        server = ServerBuilder.forPort(50051)
                .addService(new BenchmarkServiceImpl(20))  // mean: 200ms
                .build()
                .start();

        // Create channel with hedging using service config JSON
        Map<String, Object> hedgingPolicy = new HashMap<>();
        hedgingPolicy.put("maxAttempts", hedgeAttempts);
        hedgingPolicy.put("hedgingDelay", "0s");
        hedgingPolicy.put("nonFatalStatusCodes",
                Arrays.asList("UNAVAILABLE", "DEADLINE_EXCEEDED"));

        Map<String, Object> methodConfig = new HashMap<>();
        methodConfig.put("name", Collections.singletonList(
                Collections.singletonMap("service", "benchmark.BenchmarkService")));
        methodConfig.put("hedgingPolicy", hedgingPolicy);

        Map<String, Object> serviceConfig = new HashMap<>();
        serviceConfig.put("methodConfig", Collections.singletonList(methodConfig));

        // Create the channel with hedging
        channel = ManagedChannelBuilder.forAddress("localhost", 50051)
                .usePlaintext()
                .defaultServiceConfig(serviceConfig)
                .enableRetry()  // Required for hedging
                .build();

        blockingStub = BenchmarkServiceGrpc.newBlockingStub(channel);
    }

    @TearDown
    public void tearDown() {
        channel.shutdown();
        server.shutdown();
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Fork(value = 1)
    @Warmup(iterations = 2)
    @Measurement(iterations = 3)
    public void benchmarkGrpcCall() {
        // Wait for permit before making the call
        rateLimiter.acquire();
        BenchmarkRequest request = BenchmarkRequest.newBuilder()
                .setInput("test-input")
                .build();
        BenchmarkResponse response = blockingStub.process(request);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                // Set to measure each invocation time
                .mode(Mode.AverageTime)
                .timeUnit(TimeUnit.MILLISECONDS)
                // Sample every operation (no batching)
                .measurementBatchSize(1)
                .include(BenchmarkTest.class.getSimpleName())
                .forks(1)
                // Output configuration
                .result("results.json")
                .resultFormat(ResultFormatType.JSON)
                .output("output.txt")
                // Get more detailed statistics
                .timeUnit(TimeUnit.MILLISECONDS)
                .operationsPerInvocation(1)
                .measurementTime(TimeValue.seconds(1))
                .syncIterations(false)
                .build();


        new Runner(opt).run();
    }
}