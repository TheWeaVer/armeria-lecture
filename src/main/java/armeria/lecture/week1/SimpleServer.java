package armeria.lecture.week1;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpRequestWriter;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpResponseWriter;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.Server;

public class SimpleServer {

    public static void main(String[] args) {
        final Server server = Server.builder()
                                    .http(8088)
                                    // streaming response도 전달할 수 있다
                                    .service("/hello", (ctx, req) -> HttpResponse.of(200))
                                    .service("/hi", (ctx, req) -> {
                                        final HttpResponseWriter res =  HttpResponse.streaming();
                                        res.write(ResponseHeaders.of(200));
                                        res.write(HttpData.ofUtf8("foo"));
                                        return res;
                                    })
                                    .build();
        server.start().join();
        final WebClient client = WebClient.builder("http://127.0.0.1:8088/")
                                          .build();
        // HttpRequest object 사용가능
        // final HttpRequest request = HttpRequest.of(HttpMethod.GET, "hello");
        final HttpRequestWriter request =  HttpRequest.streaming(RequestHeaders.of(HttpMethod.GET, "hello"));
        request.write(HttpData.ofUtf8("foo"));
        request.write(HttpData.ofUtf8("bar"));
        request.write(HttpData.ofUtf8("baz"));
        final AggregatedHttpResponse res = client.get("/hello").aggregate().join();
        System.err.println(res.headers());
        server.stop().join();
    }
}

// HttpResponse는 publisher
// HttpResponseSubscriber가 있다.
// subscriber를 통해서 send 하고, decoder를 사용해서 buffer에서 read

// response를 HttpService가 만들면, subscriber가 subscribe를 한다.
// request(1)를 통해서 데이터 전달 요청
// data를 send buffer에 기록한다. 기록에 성공한 후에(res.write()), subscription.request()를 호출
//// HttpResponseSubscriber에 구현되어있다.
// client는 받아서
//// 그런데 꽉 차 있다면, tcp에 rate control(congestion control 보내는 쪽 / flow control 받는 쪽),
//// 즉 hang이 걸렸다면, memory를 많이 쓰게 된다.
//// client가 받을 수 있을때만 write를 한다. back pressure를 통한 reactive server
//// subscription.request(1) 을 요청할때만 write를 한다.
//// streaming.whenConsumed.thenRun --> subscription.request()
//// ctx.eventLoop().schedule()
