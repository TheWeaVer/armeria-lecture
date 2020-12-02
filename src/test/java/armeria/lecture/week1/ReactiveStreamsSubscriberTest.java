package armeria.lecture.week1;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpObject;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.ResponseHeaders;

class ReactiveStreamsSubscriberTest {

    @Test
    void aggregate() {
        final HttpResponse res = HttpResponse.of(ResponseHeaders.of(200), HttpData.ofUtf8("foo"));
        // HTTP/1.1 200 OK
        // Content-length: 3
        //
        // foo

        assert res instanceof Publisher;

        // http reponse는 header가 오고 body가 오는데, 다 받았을 때 future를 채워라
        final CompletableFuture<AggregatedHttpResponse> aggregated = res.aggregate();
        final AggregatedHttpResponse aggregatedHttpResponse = aggregated.join();
        System.err.println(aggregatedHttpResponse.headers().status());
        System.err.println(aggregatedHttpResponse.contentUtf8());
    }

    @Test
    void customSubscriber() {
        final HttpResponse res = HttpResponse.of(ResponseHeaders.of(200), HttpData.ofUtf8("foo"));

        final CompletableFuture<MyAggregatedHttpResponse> aggregated = aggregate(res);
        final MyAggregatedHttpResponse aggregatedHttpResponse = aggregated.join();
        System.err.println(aggregatedHttpResponse.headers().status());
        System.err.println(aggregatedHttpResponse.contentUtf8());
    }

    private CompletableFuture<MyAggregatedHttpResponse> aggregate(HttpResponse res) {
        // wrapper 생성
        final CompletableFuture<MyAggregatedHttpResponse> future = new CompletableFuture<>();

        // res는 Publisher, Subscriber를 구현해야한다.
        // instance에 callback을 붙인 것이다.
        res.subscribe(new Subscriber<>() {
            private Subscription s; //하나의 thread에서 수행되기 떄문에, volatile을 붙이지 않아도 된다.
            private HttpData data;
            private ResponseHeaders headers;

            @Override
            public void onSubscribe(Subscription s) {
                currentThreadName("onSubscribe");
                this.s = s;
                // back pressure를 위해서는 n == 1이어야 한다.
                s.request(5);

                //s.request(2);
                // onNext 가 2번 호출됨
                // onComplete은 어떻게 호출되는거지?
            }

            @Override
            public void onNext(HttpObject httpObject) {
                currentThreadName("onNext");
                if (httpObject instanceof ResponseHeaders) {
                    headers = (ResponseHeaders) httpObject;
                    //s.request(1);
                } else {
                    assert httpObject instanceof HttpData;
                    data = (HttpData) httpObject;
                }
                //s.request(1);
            }

            @Override
            public void onError(Throwable t) {
                currentThreadName("onError");
                future.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                currentThreadName("onComplete");
                future.complete(new MyAggregatedHttpResponse(headers, data));
            }
        });
        // executor를 지정하지 않는다면, 기존에 제공되는 forkJoinPool에서 수행된다.

        return future;
    }

    private static void currentThreadName(String method) {
        System.err.println("Name: " + Thread.currentThread().getName() + " (in " + method + ')');
    }

    static class MyAggregatedHttpResponse {

        private final ResponseHeaders responseHeaders;
        private final HttpData httpData;

        MyAggregatedHttpResponse(ResponseHeaders responseHeaders, HttpData httpData) {
            this.responseHeaders = responseHeaders;
            this.httpData = httpData;
        }

        ResponseHeaders headers() {
            return responseHeaders;
        }

        public String contentUtf8() {
            return httpData.toStringUtf8();
        }
    }
}
