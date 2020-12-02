/*
 * Copyright (c) 2020 LINE Corporation. All rights reserved.
 * LINE Corporation PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package armeria.lecture.week1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpResponseWriter;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.MediaType;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;

public class HelloWorldTest {

    @RegisterExtension
    static final ServerExtension server = new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) throws Exception {
            sb.service("/api", (ctx, req) -> {
//                final HttpResponseWriter res = HttpResponse.streaming();
//                res.write(ResponseHeaders.of(200));
//                res.write(HttpData.ofUtf8("Hello Armeria"));
//                return res;
                // 껍데기를 만들고 껍데기를 리턴, callback을 이용해서 data를 채워넣는다.
//                final CompletableFuture<HttpResponse> future = new CompletableFuture<>();
//
//                final CompletableFuture<AggregatedHttpRequest> aggFuture = req.aggregate();
//                aggFuture.thenAccept(aggregatedHttpRequest -> {
//                    final String content = aggregatedHttpRequest.contentUtf8(); // armeria
//                    future.complete(HttpResponse.of(HttpStatus.OK, MediaType.PLAIN_TEXT_UTF_8, "Hello " + content));
//                });
//
//                return HttpResponse.from(future);
                // 혹은 streaming도 가능하다
                final CompletableFuture<AggregatedHttpRequest> aggFuture = req.aggregate();
                final HttpResponseWriter res = HttpResponse.streaming();
                res.write(ResponseHeaders.of(200)); // request(1)
//                res.write(HttpData.ofUtf8("Hello " + content));
                return res;
            }).build();
        }

        // test마다 한번씩 띄운다
//        @Override
//        protected boolean funForEachTest() {
//            return true;
//        }
    };

    @RegisterExtension
    static final ServerExtension server2 = new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) throws Exception {
            sb.service("/api", (ctx, req) -> {
                final HttpResponseWriter streaming = HttpResponse.streaming();
                return streaming;
            });
        }
    };

    @Test
    public void test1() {
        final WebClient client = WebClient.of(server.httpUri());
        final String content = client.post("/api", "Armeria").aggregate().join().contentUtf8();
        assertThat(content).isEqualTo("Hello Armeria");
    }
}
