package com.mera.education.grpc.task.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TaskServer {
    private static Logger logger = LoggerFactory.getLogger(TaskServer.class);

    public static void main(String[] args) throws InterruptedException, IOException {
        logger.debug("gRPC Server is started");

        Server server = ServerBuilder.forPort(5051)
                .addService(new TaskServiceImpl())
                .build();

        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread (()->{
            System.out.println("Received Shutdown Request");
            server.shutdown();
            System.out.println("Successfully stopped the server");
        }));

        server.awaitTermination();
    }
}
