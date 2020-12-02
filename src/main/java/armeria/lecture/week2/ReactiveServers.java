/*
 * Copyright (c) 2020 LINE Corporation. All rights reserved.
 * LINE Corporation PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package armeria.lecture.week2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.file.HttpFile;

public class ReactiveServers {
    private static final Logger logger = LoggerFactory.getLogger(ReactiveServers.class);
    public static void main(String[] args) {
        startServer(8081, 100);
        startServer(8082, 300);
        startServer(8083, 500);
    }
    private static void startServer(int port, int frameIntervalMillis) {
        final Server server = Server.builder()
                                    .http(port)
                                    .requestTimeoutMillis(0)
                                    .service("/html/", HttpFile.builder(ReactiveServers.class.getClassLoader(),
                                                                        "index.html").build().asService())
                                    .service("/animation", new AnimationService(frameIntervalMillis))
                                    .build();
        server.start().join();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop().join();
            logger.info("Server has been stopped.");
        }));
    }
}
