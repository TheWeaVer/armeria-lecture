package armeria.lecture.week2;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.assertj.core.util.Strings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.base.Joiner;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.common.AggregatedHttpRequest;
import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpResponseWriter;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.AbstractHttpService;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.ServiceRequestContext;
import com.linecorp.armeria.server.annotation.Post;
import com.linecorp.armeria.testing.junit5.server.ServerExtension;

/***
 * 비동기는 eventlopp와 wrapper에 대한 이해가 필요하고
 * 추가적으로 reactive stream까지
 * 개별 클라이언트로  부터 개별 SocketChannel이 생성되고
 * selector가 존재한다. 하나의 thread가 selector를 보유하고, 등록된 SocketChannel을 확인
 * socketChannel.register(selector, SelecionKey.OP_READ,  ...);
 * for (;;) {
 *    int keys =  selector.select(timeout);
 *    processSelectedKeys();
 * }
 *
 * Wrapper는 Backend로부터 응답을 기다리지 않고 Service가 return한다. 껍데기만.
 * Wrapper<Response>,그리고 callback을 붙여넣는다.
 *
 * Q. Eventloop : selector = 1 : 1
 * Q. thread가 늘어자니 않는다. SocketChannel이 늘어나지 않으니
 *
 * EventLoop: linux epoll / io_uring(공부해도 좋아!) / kqueue(FreeBSD)
 * netty는 이것을 사용해서 interface를 제공하고 있다.
 *
 * Thread pool의 크기 N_cpu  * U_cpu * (1 + W/C) = 코어수 * 유틸라이제이션cpu(thread pool에 할당된 자원, 0.0~1.0) * ( 1 + wait/computing)
 * Java concurrency in practice
 * Tomcat은 waiting이 길어서 200개의 thread를 사용한다.
 *
 * NIO Java network
 *
 * Event-driven I/O API
 * EventLoop, Channel
 * Channel Pipeline: ChannelHandler를 주입할 수 있다(outbound / inbound).
 * 받을때는 Inbound, 나갈때는 Outbound
 * Armeria HttpObject -> NettyObject -> bytes array
 *
 * Event Pipeline의 처리가 하나의 event loop에서 처리된다. why? channel이 하나의 eventloop에 등록되니까
 * 하나의 channel이 하나의 pipeline에 등록된다.
 *
 * Channel Handler
 *
 * Client Request Flow
 * Service reqeust flow의 buffer뒤에 handler와 event가 붙어있다.
 *
 * Server-side channel registration
 *
 * Event Loop가 core*2인 이유는 대충 정한거임... W/C = 1이라서 그런건 아니다
 * 암달의 법칙, theoretical speedup when using multiple processors
 * HttpService에 도달하기 전까지 모두다 inbound handler에서 담당한다. HttpResponseSubscriber는
 *
 * NioEventLoop
 * HttpServerPipelineConfigurator
 *
 * NioEventLoop
 * 1) strategy
 * 2) 읽을게 있다면 processSelectedKeys(); packet을 읽음
 * 2-1)  unsafe.read() 하나의 채널에 있는 packet를 읽어옴 -> pipeline.channelRead(); -> InbooundPipeline
 * 3) runAllTasks(); EL은 하나의 작업 큐를 가지고 있다. runnable 객체를 넣어둠, 넣어둔걸 꺼내서 실행
 * 4) fireChannelRead() -> invokeChannelRead가 계속 실행 -> executor가 eventloop에 없으면 별도의 executor를 실행(처음에 보여준 service에 등록한 executor인듯)
 * 5) HTTPREqeustDecoder.channelRead() 가 최종적으로 불린다. 여기의 HttpReqeust는 Netty거
 * 5-1) 여기서 ArmeriaDecodedHttpReqeust를 만든다
 * 5-2) ctx.ChannelRead(req);
 * 5-3) HttpServerHandler(여기까지는 Head따로 body따로 온다)
 * 6) HttpSercerHandler.channelread()
 * 6-1) 타고타고 와서  service.serve()에 오게 된다.
 * 6-2) HttpResponseSubscriber.subscribe()
 * 7) HttpResponseSubscriber.onSubscribe
 * 7-1) subscription.request(1) <- back pressure
 * 7-2) onNext가 불린다.Http의 헤더냐 바디냐~~ 따라 동작이 다르다. responseEncoder.writeHeaders() -> netty -> byte로
 * 7-3) writeHeader는 ChannelFuture를 리턴한다. wrapper이다.
 * 7-4) responseEncoder.writeHeaders().addListener() <- callback을 붙인다. 이벤트루프가 수행한다.
 *      executor를 따로 받지 않고, executor를 넣어둠
 * 7-5) ChannelFutureListener에서 onComplete시 noti를 받을 수 있게 되어있음
 * 7-6) ch.write()하면 이제부터 outboundHandler를 계속 타게됨
 * 7-7) sending buffer가 꽉차있으면 OPerationComplete이 불리지 않는다. HttpResponseSubscriber 불려야 backpressure가 수행된다.
 *
 * Q. header와 body를 eveny loop로 각각 불러온다. blocking을 하면, req.qggregate().join() -> 이면 body를 계속 기다린다.
 * Q. 그런 db를 쓰게 되면 ctx를 db 다녀오는 데까지 끌고 가야 할 수 있겠네요. RequestScoping과 연관이 있음
 *
 * coroutine은 별도의 context를 유지하는데, 이것을 7장에서
 *
 * HttpServerPipelineConfigurator
 * HttpCientPipelineConfigurator
 */

// Server생성시 ServerListener를 구현하면 된다.
// serverStarted -> client.of(10000); client.post(/registration, "url:port").aggregate()
public class ServiceInfoServerTest {
    @RegisterExtension
    static final ServerExtension server = new ServerExtension() {
        @Override
        protected void configure(ServerBuilder sb) throws Exception {
            // 여러 thread에서 들어오므로 arraylist는 아니된다.
            final Queue<String> q = new ConcurrentLinkedQueue<>();
            // route style
//            sb.route().path("/registration").methods(HttpMethod.POST)
//              .build(new RegistrationService(q));
//
//            // spring style
//            sb.annotatedService(new Object() {
//                @Post("/registration")
//                public HttpResponse register(AggregatedHttpRequest req) {
//                    return null;
//                }
//            });
//
//            // servlet style - old style
//            sb.service("/registration", new AbstractHttpService() {
//                @Override
//                protected HttpResponse doPost(ServiceRequestContext ctx, HttpRequest req) throws Exception {
//                    return null;
//                }
//            });

            sb.service("/registration", new HttpService() {
                // 껍데기 만들어서 바로 리턴하도록 하고 리퀘스트 aggregation 해서 채워지면 콜백에서 처리하는 것
                @Override
                public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
                    final RequestHeaders headers = req.headers();
                    final HttpMethod method = req.method();
                    if (HttpMethod.POST != method) {
                        // do something
                    }

                    final CompletableFuture<HttpResponse> aggregatedHttpResponseFuture =
                            new CompletableFuture<>();

                    final CompletableFuture<AggregatedHttpRequest> aggregated = req.aggregate();
                    aggregated.thenAccept(aggregatedHttpRequest -> {
                        final String content = aggregatedHttpRequest.contentUtf8();
                        System.out.println(content);
                        q.add(content);
                        aggregatedHttpResponseFuture.complete(HttpResponse.of(200));
                    });

                    return HttpResponse.from(aggregatedHttpResponseFuture);
                }
            });

            sb.service("/discovery", new HttpService() {
                @Override
                public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
                    if (req.method() != HttpMethod.GET) {
                        throw new Exception("method not support");
                    }
                    final CompletableFuture<HttpResponse> response = new CompletableFuture<>();

                    final CompletableFuture<AggregatedHttpRequest> aggreagated = req.aggregate();
                    aggreagated.thenAccept(aggregatedHttpRequest -> {
                        response.complete(HttpResponse.of(ResponseHeaders.of(200), HttpData.ofUtf8(q.element())));
                    });

                    return HttpResponse.from(response);
                }
            });
        }
    };

    @Test
    public void registerIp() {
        final WebClient client = WebClient.of(server.httpUri());
        client.post("/registration", "127.0.0.1:8080").aggregate().join();
        client.post("/registration", "127.0.0.1:8081").aggregate().join();

        final String data = client.get("/discovery").aggregate().join().contentUtf8();
        System.out.println(data);
    }

    static class RegistrationService implements HttpService {
        private final Queue<String> q;

        RegistrationService(Queue<String> q)  {
            this.q = q;
        }

        @Override
        public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
            return null;
        }
    }

    static class DiscoveryService implements HttpService {
        private static final Joiner joiner = Joiner.on(',');
        private final Queue<String> q;

        DiscoveryService(Queue<String> q) {
            this.q = q;
        }

        @Override
        public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
            return null;
        }
    }
}
