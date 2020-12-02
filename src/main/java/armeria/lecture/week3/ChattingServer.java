/*
 * Copyright (c) 2020 LINE Corporation. All rights reserved.
 * LINE Corporation PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package armeria.lecture.week3;

import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpObject;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpRequestWriter;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpResponseWriter;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServiceRequestContext;

public final class ChattingServer {
    public static void main(String[] args) throws InterruptedException {
        final Server server = Server.builder()
                                    .http(8080)
                                    .requestTimeoutMillis(0)
                                    .service("/chat", new ChatService())
                                    .build();
        server.start().join();
        final HttpRequestWriter writer1 = connect(1);
        final HttpRequestWriter writer2 = connect(2);
        final HttpRequestWriter writer3 = connect(3);
        Thread.sleep(1000);
        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                final String nextLine = scanner.nextLine();
                writer1.write(HttpData.ofUtf8(nextLine));
            }
        }
    }

    private static HttpRequestWriter connect(int id) {
        final WebClient client = WebClient.builder("http://127.0.0.1:8080")
                                          .responseTimeoutMillis(0)
                                          .build();
        final HttpRequestWriter streaming = HttpRequest.streaming(RequestHeaders.of(HttpMethod.POST, "/chat"));
        final HttpResponse response = client.execute(streaming);
        response.subscribe(new Subscriber<HttpObject>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(HttpObject httpObject) {
                if (httpObject instanceof HttpData) {
                    System.err.println(id + ": " + ((HttpData) httpObject).toStringUtf8());
                }
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onComplete() {}
        });
        return streaming;
    }

    private static class ChatService implements HttpService {
        private final ConcurrentLinkedQueue<HttpResponseWriter> queue = new ConcurrentLinkedQueue<>();

        @Override
        public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
            final HttpResponseWriter streaming = HttpResponse.streaming();
            queue.add(streaming);
            streaming.write(ResponseHeaders.of(200));
            req.subscribe(new Subscriber<HttpObject>() {
                @Override
                public void onSubscribe(Subscription s) {
                    s.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(HttpObject httpObject) {
                    if (httpObject instanceof HttpData) {
                        final String message = ((HttpData) httpObject).toStringUtf8();
                        for (HttpResponseWriter responseWriter : queue) {
                            if (responseWriter != streaming) {
                                responseWriter.write(HttpData.ofUtf8(message));
                            }
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    queue.remove(streaming);
                }

                @Override
                public void onComplete() {
                    queue.remove(streaming);
                }
            });
            return streaming;
        }
    }
}
