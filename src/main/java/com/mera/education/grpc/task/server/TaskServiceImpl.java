package com.mera.education.grpc.task.server;

import io.grpc.stub.StreamObserver;
import com.mera.education.grpc.proto.task.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.Math;
import java.util.DoubleSummaryStatistics;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class TaskServiceImpl extends TaskServiceGrpc.TaskServiceImplBase {
    private static String default_fallback_message = "Неверный формат числа";
    private static volatile double max_number = 0d;
    private static Logger logger = LoggerFactory.getLogger(TaskServiceImpl.class);

    @Override
    public void task(TaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        logger.debug("*** Unary implementation on server side ***");
        Task tasking = request.getTask();
        String number = tasking.getNumber();
        logger.debug("Request has been received on server side: number - {}", number);
        Double double_number = null;
        try {
            double_number = Double.parseDouble(number);
            double_number = Math.sqrt(double_number);
            String result = String.valueOf(double_number);
            TaskResponse response = TaskResponse.newBuilder()
                    .setResult(result)
                    .build();

            //send the response
            responseObserver.onNext(response);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            TaskResponse response = TaskResponse.newBuilder()
                    .setResult(default_fallback_message)
                    .build();
            //send the response
            responseObserver.onNext(response);
        } finally {
            //complete RPC call
            responseObserver.onCompleted();
        }
    }

    @Override
    public void taskManyTimes(TaskManyTimesRequest request, StreamObserver<TaskManyTimesResponse> responseObserver) {
        logger.debug("*** Server streaming implementation on server side ***");
        Task tasking = request.getTask();
        String number = tasking.getNumber();
        try {
            int currentNumber = Integer.parseInt(number);
            int k = 2;
            while (currentNumber != 1) {
                if (currentNumber % k != 0)
                    k++;
                else {
                    currentNumber /= k;

                    TaskManyTimesResponse response = TaskManyTimesResponse.newBuilder()
                            .setResult(String.valueOf(k))
                            .build();
                    logger.debug("send response {}", k);
                    responseObserver.onNext(response);
                    Thread.sleep(1000L);

                }
            }
        } catch (InterruptedException | NumberFormatException e) {
            e.printStackTrace();
            TaskManyTimesResponse response = TaskManyTimesResponse.newBuilder()
                    .setResult(default_fallback_message)
                    .build();

            //send the response
            responseObserver.onNext(response);
        } finally {
            logger.debug("all messages have been sent");
            responseObserver.onCompleted();

        }

    }

    @Override
    public StreamObserver<LongTaskRequest> longTask(StreamObserver<LongTaskResponse> responseObserver) {
        logger.debug("*** Client streaming implementation on server side ***");
        StreamObserver<LongTaskRequest> streamObserverofRequest = new StreamObserver<LongTaskRequest>() {
            Stream.Builder<Double> ds = Stream.builder();

            @Override
            public void onNext(LongTaskRequest longTaskRequest) {
                logger.debug("make some calculation for each request");
                //client sends a message
                try {
                    ds.add(Double.parseDouble(longTaskRequest.getTask().getNumber()));
                } catch (NumberFormatException e) {
                    LongTaskResponse response = LongTaskResponse.newBuilder()
                            .setResult(default_fallback_message)
                            .build();
                    //send the response
                    responseObserver.onNext(response);
                }

            }

            @Override
            public void onError(Throwable throwable) {
//                responseObserver.onError(throwable);
            }

            @Override
            public void onCompleted() {
                Stream<Double> stream = ds.build();
//                client is done, this is when we want to return a response (responseObserver)
                double result = stream.collect(DoubleStatistics.collector()).getStandardDeviation();
                responseObserver.onNext(LongTaskResponse.newBuilder()
                        .setResult(String.valueOf(result))
                        .build());
                logger.debug("Send result: {}", result);
                responseObserver.onCompleted();
            }
        };

        return streamObserverofRequest;
    }


    @Override
    public StreamObserver<TaskEveryoneRequest> taskEveryone(StreamObserver<TaskEveryoneResponse> responseObserver) {
        logger.debug("*** Bi directional streaming implementation on server side ***");

        StreamObserver<TaskEveryoneRequest> requestObserver = new StreamObserver<TaskEveryoneRequest>() {
            @Override
            public void onNext(TaskEveryoneRequest value) {
                //client sends a message
                String number = value.getTask().getNumber();
                try {
                    double double_number = Double.parseDouble(number);
                    if (double_number > max_number)
                        max_number = double_number;
                    TaskEveryoneResponse taskEveryoneResponse = TaskEveryoneResponse.newBuilder()
                            .setResult(String.valueOf(max_number))
                            .build();

                    //send message for each request
                    logger.debug("Send result for each request: {}", max_number);
                    responseObserver.onNext(taskEveryoneResponse);
                }
                catch (NumberFormatException e) {
                    TaskEveryoneResponse response = TaskEveryoneResponse.newBuilder()
                            .setResult(default_fallback_message)
                            .build();
                    //send the response
                    responseObserver.onNext(response);
                }
            }

            @Override
            public void onError(Throwable throwable) {
//                responseObserver.onError(throwable);
            }

            @Override
            public void onCompleted() {
                //client is done, so complete server-side also
                logger.debug("close bi directional streaming");
                responseObserver.onCompleted();
            }
        };

        return requestObserver;
    }

    static class DoubleStatistics extends DoubleSummaryStatistics {

        private double sumOfSquare = 0.0d;
        private double sumOfSquareCompensation; // Low order bits of sum
        private double simpleSumOfSquare; // Used to compute right sum for
        // non-finite inputs

        @Override
        public void accept(double value) {
            super.accept(value);
            double squareValue = value * value;
            simpleSumOfSquare += squareValue;
            sumOfSquareWithCompensation(squareValue);
        }

        public DoubleStatistics combine(DoubleStatistics other) {
            super.combine(other);
            simpleSumOfSquare += other.simpleSumOfSquare;
            sumOfSquareWithCompensation(other.sumOfSquare);
            sumOfSquareWithCompensation(other.sumOfSquareCompensation);
            return this;
        }

        private void sumOfSquareWithCompensation(double value) {
            double tmp = value - sumOfSquareCompensation;
            double velvel = sumOfSquare + tmp; // Little wolf of rounding error
            sumOfSquareCompensation = (velvel - sumOfSquare) - tmp;
            sumOfSquare = velvel;
        }

        public double getSumOfSquare() {
            double tmp = sumOfSquare + sumOfSquareCompensation;
            if (Double.isNaN(tmp) && Double.isInfinite(simpleSumOfSquare)) {
                return simpleSumOfSquare;
            }
            return tmp;
        }

        public final double getStandardDeviation() {
            long count = getCount();
            double sumOfSquare = getSumOfSquare();
            double average = getAverage();
            return count > 0 ? Math.sqrt((sumOfSquare - count * Math.pow(average, 2)) / (count - 1)) : 0.0d;
        }

        public static Collector<Double, ?, DoubleStatistics> collector() {
            return Collector.of(DoubleStatistics::new, DoubleStatistics::accept, DoubleStatistics::combine);
        }

    }
}
