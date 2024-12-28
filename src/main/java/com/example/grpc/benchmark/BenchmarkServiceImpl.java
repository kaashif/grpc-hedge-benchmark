package com.example.grpc.benchmark;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.Random;

public class BenchmarkServiceImpl extends BenchmarkServiceGrpc.BenchmarkServiceImplBase {
    private final Random random = new Random();
    private final double meanDelayMs;  // mean delay (λ = 1/mean)

    public BenchmarkServiceImpl(double meanDelayMs) {
        this.meanDelayMs = meanDelayMs;
    }

    @Override
    public void process(BenchmarkRequest request, StreamObserver<BenchmarkResponse> responseObserver) {
        try {
            // Generate delay from exponential distribution
            // Using -mean * ln(U) where U is uniform(0,1)
            double delay = -meanDelayMs * Math.log(random.nextDouble());

            // Simulate processing delay
            Thread.sleep((long) delay);

            BenchmarkResponse response = BenchmarkResponse.newBuilder()
                    .setOutput("Processed: " + request.getInput() + " (delayed: " + String.format("%.2f", delay) + "ms)")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (InterruptedException e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Processing interrupted")
                    .withCause(e)
                    .asException());
        }
    }
}