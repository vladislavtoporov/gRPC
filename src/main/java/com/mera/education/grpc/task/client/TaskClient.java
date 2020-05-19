package com.mera.education.grpc.task.client;

import com.mera.education.grpc.proto.task.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TaskClient {

    private static Logger logger = LoggerFactory.getLogger(TaskClient.class);

    String ipAddress = "localhost";
    int port = 5051;

    public static void main(String[] args) {
        logger.debug("gRPC Client is started");
        TaskClient main = new TaskClient();
        main.run();
    }

    private void run() {
        logger.debug("Channel is created on {} with port: {}", ipAddress, port);
        //gRPC provides a channel construct which abstracts out the underlying details like connection, connection pooling, load balancing, etc.
        ManagedChannel channel = ManagedChannelBuilder.forAddress(ipAddress, port)
                .usePlaintext()
                .build();

        //examples how to initialize clients for gRPC
        //TestServiceGrpc.TestServiceBlockingStub syncClient = TestServiceGrpc.newBlockingStub(channel);
        //async client
        //TestServiceGrpc.TestServiceFutureStub asyncClient = TestServiceGrpc.newFutureStub(channel);

        //unary implementation
        // print sqrt of number
        doUnaryCall(channel);

        //server streaming implementation
        // print multipliers of number
        doServerStreamingCall(channel);

        //client streaming implementation
        // print standart deviation of number array
        doClientStreamingCall(channel);

        //bi directional streaming implementation
        // print max number in number array
        doBiDiStreamingCall(channel);

        logger.debug("Shutting down channel");
        channel.shutdown();

    }


    private void doUnaryCall(ManagedChannel channel) {
        logger.debug("*** Unary implementation ***");
        //created a task service client (blocking - sync)
        TaskServiceGrpc.TaskServiceBlockingStub taskClient = TaskServiceGrpc.newBlockingStub(channel);

        //Unary
        //created a protocol buffer processing message
        Task tasking = Task.newBuilder()
                .setNumber(625)
                .build();

        // the same for request
        TaskRequest taskRequest = TaskRequest.newBuilder()
                .setTask(tasking)
                .build();

        //call RPC call and get result
        TaskResponse taskResponse = taskClient.task(taskRequest);

        logger.debug("Response has been received from server: {}", taskResponse.getResult());
    }

    private void doServerStreamingCall(ManagedChannel channel) {
        logger.debug("*** Server streaming implementation ***");
        //created a task service client (blocking - sync)
        TaskServiceGrpc.TaskServiceBlockingStub taskClient = TaskServiceGrpc.newBlockingStub(channel);
        //Server Streaming
        Task tasking = Task.newBuilder()
                .setNumber(210)
                .build();
        TaskManyTimesRequest taskManyTimesRequest = TaskManyTimesRequest.newBuilder()
                .setTask(tasking)
                .build();
        logger.debug("Send object with name - {}", taskManyTimesRequest);
        taskClient.taskManyTimes(taskManyTimesRequest)
                .forEachRemaining(taskManyTimesResponse -> {
                    logger.debug("Response has been received from server: {}", taskManyTimesResponse.getResult());
                });


    }

    private void doClientStreamingCall(ManagedChannel channel) {
        logger.debug("*** Client streaming implementation ***");
        //created a task service client (async)
        TaskServiceGrpc.TaskServiceStub asyncClient = TaskServiceGrpc.newStub(channel);

        CountDownLatch latch = new CountDownLatch(1);

        StreamObserver<LongTaskRequest> requestObserver = asyncClient.longTask(new StreamObserver<LongTaskResponse>() {
            @Override
            public void onNext(LongTaskResponse longTaskResponse) {
                //we get a response from the server, onNext will be called only once
                logger.debug("Received a response from the server: {}", longTaskResponse.getResult());
            }

            @Override
            public void onError(Throwable throwable) {
                //we get an error from the server
            }

            @Override
            public void onCompleted() {
                //the server is done sending us data
                //onCompleted will be called right after onNext()
                logger.debug("Server has completed sending us something");
                latch.countDown();
            }
        });

        logger.debug("Sending number #1");
        requestObserver.onNext(LongTaskRequest.newBuilder()
                .setTask(Task.newBuilder()
                        .setNumber(1)
                        .build())
                .build());

        logger.debug("Sending number #2");
        requestObserver.onNext(LongTaskRequest.newBuilder()
                .setTask(Task.newBuilder()
                        .setNumber(2)
                        .build())
                .build());

        logger.debug("Sending number #3");
        requestObserver.onNext(LongTaskRequest.newBuilder()
                .setTask(Task.newBuilder()
                        .setNumber(3)
                        .build())
                .build());

        requestObserver.onCompleted();

        try {
            latch.await(3L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    private void doBiDiStreamingCall(ManagedChannel channel) {
        logger.debug("*** Bi directional streaming implementation ***");
        //created a task service client (async)
        TaskServiceGrpc.TaskServiceStub asyncClient = TaskServiceGrpc.newStub(channel);

        CountDownLatch latch = new CountDownLatch(1);
        StreamObserver<TaskEveryoneRequest> requestObserver = asyncClient.taskEveryone(new StreamObserver<TaskEveryoneResponse>() {
            @Override
            public void onNext(TaskEveryoneResponse taskEveryoneResponse) {
                logger.debug("Response from the server: {}", taskEveryoneResponse.getResult());
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                logger.debug("Server is done sending data");
                latch.countDown();
            }
        });

        Arrays.asList(1, 2, 3, 4, 5).forEach(
                number -> {
                    logger.debug("Sending: {}", number);
                    requestObserver.onNext(TaskEveryoneRequest.newBuilder()
                            .setTask(Task.newBuilder()
                                    .setNumber(number)
                                    .build())
                            .build());

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });

        requestObserver.onCompleted();

        try {
            latch.await(3L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}
