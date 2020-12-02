package armeria.lecture.week2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.file.HttpFile;

public class ReactiveServer {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveServer.class);

    public static void main(String[] args) {
        final Server server = Server.builder()
                                    .http(8081)
                                    //정적파일 서비스
                                    .service("/html",
                                             HttpFile.builder(ReactiveServer.class.getClassLoader(),
                                                              "/index.html").build().asService())
                                    .requestTimeoutMillis(1000)
                                    .service("/animation", new AnimationService(250))
                                    .build();
        server.start().join();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop().join();
            logger.info("Server has been stopped.");
        }));
    }

}
