/*
 * Copyright (c) 2020 LINE Corporation. All rights reserved.
 * LINE Corporation PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package armeria.lecture.week3;

import java.util.Date;
import java.util.concurrent.CompletableFuture;

import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.client.brave.BraveClient;
import com.linecorp.armeria.common.AggregatedHttpResponse;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.RequestContext;
import com.linecorp.armeria.common.util.SafeCloseable;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.brave.BraveService;
import com.linecorp.armeria.server.logging.LoggingService;

import brave.Tracing;

/**
 * zipkin:
 * curl -sSL https://zipkin.io/quickstart.sh | bash -s
 * java -jar zipkin.jar
 *
 * Q. 혹시 이벤트 루프가 하나인 경우에 쓰레드가 하나인 것 같은데(맞나요?? yes),
 * 이 경우에는 backend1 요청이랑 backend2 요청이 처리되는 스레드가 같을 것 같거든요…
 * 그렇다면 이 때는 리퀘스트 스코핑이 없어도 될까요???
 * HttpServerHandler try-with-resource내에서 실행이 되어서, context가 사라진다.
 * https://github.com/line/armeria/blob/master/core/src/main/java/com/linecorp/armeria/server/HttpServerHandler.java#L379
 *
 * Q. 하나의 스레드에서 concurrent하게 여러개 요청이 처리되는데 어떻게 threadlocal로 관리할 수 있는건가요?
 * 한시점에는 하나의 리퀘스트만 처리함
 *
 * try-with-resource를 안쓰면 push할 경우에 pop을 해야한다. 안그러면 context가 섞인다.
 *
 * Q. kotlin 코루틴과 함께 사용해서 await()를 이용해 순차적으로 client 호출을 할 때도 지금과같은 request scoping이 필요한가요?
 * kotlin coroutine은 필요없다.
 *
 * Q. 요청을 처리하다가 아직 요청에 대한 처리가 안끝났을 때(아직 현재 ctx에 대한 pop()이 되기 전),
 * 그것과 다른 또다른 요청이 들어와서 ctx.push()가 되면 쓰레드 로컬이 섞일 수도 있지 않나요???
 * 다른 요청을 처리 하지 않는다.
 *
 * Q. context leak이 발생한 경우
 * IllegalStateException이 발생한다.
 *
 * Q. ctx1.push() scope 안에서 ctx2.push() 를 했을 때 ctx2의 내용이 close 될때 ctx1의 내용이 다시 자동으로 입력되어 들어갈까요?
 * error가 발생한다. -? 해당 스레드에서 다른 컨텍스트로의 접근은 하면 안되니까....?
 *
 * Q. 요청의 처리가 순차적으로 수행되어야 한다면,
 * 비동기 서버의 장점은 network io나 disk io등이 발생할 때 해당 스레드가 대기하지 않고 다른 일을 할 수 있다는 건가요??
 * 그.. wrapper의 힘을 빌려서...??
 * 맞습니다. Eventloop가 배치라고 생각하면 좋을 것 같다.
 *
 * Q. 그럼 한 thread가 request를 처리하던 중 I/O가 발생하고
 * 그 동안 다른 request가 들어와서 그 요청을 처리해야한다면 ServiceRequestContext가 바뀌는건가요?
 * 네 맞습니다. HttpServerHandler에서 다음 요청의 ctx를 채워넣는다. -> 아마도 PCB에 있는 context가 지속적으로 replace되는것인듯...??
 *
 * Q.
 * 1. 첫 번째 요청이 들어왔고 이벤트 루프에서 front -> backend1 요청을 보냄. 응답은 아직 안옴 (여기서 첫 번째 요청에 대한 ctx.push() 이후 ctx.pop() 됨)
 * 2. 두 번째 요청이 들어왔고 이벤트 루프에서 front -> backend1 요청을 보냄. 응답은 아직 안옴 (여기서 두 번째 요청에 대한 ctx.push() 이후 ctx.pop() 됨)
 * 3. 첫 번째 요청에서 front -> backend1 요청에 대한 응답이 와서 콜백 처리. 여기서… 첫 번째 요청에 대한 ctx.push()가 되어야 하는데 ctx는 콜백에 들어있어서 알 수 있는 거죠??
 * 맞습니다.
 *
 * Q. thread context를 변환하는 오버헤드가 발생할 것 같은데(아까 몇 가지 방법이 있다 말씀해주셨지만..),
 * 이것보다 io를 대기하지 않는게 더 큰 이득을 가져오기때문에 비동기 서버를 운용한다고 생각하면 될까요??
 * thread context를 변환하는 오버헤드는 없다고 보시면 됩니다~ 그냥 thread local에 넣었다 빼주는 거예요~
 * */
public final class OneFrontTwoBackend {
    public static void main(String[] args) {
        final Tracing tracing = TracingFactory.create("frontend");
        final Tracing backendTracing = TracingFactory.create("backend");

        final Server server1 =
                Server.builder()
                      .http(9000)
                      .service("/api", (ctx, req) -> HttpResponse.of(new Date().toString()))
                      .decorator(BraveService.newDecorator(backendTracing))
                      .decorator(LoggingService.newDecorator())
                      .build();
        final Server server2 =
                Server.builder()
                      .http(9001)
                      .service("/api", (ctx, req) -> HttpResponse.of(new Date().toString()))
                      .decorator(BraveService.newDecorator(backendTracing))
                      .decorator(LoggingService.newDecorator())
                      .build();

        final WebClient backendClient1 =
                WebClient.builder("http://localhost:9000/")
                         .decorator(BraveClient.newDecorator(tracing, "backend1"))
                         .build();

        final WebClient backendClient2 =
                WebClient.builder("http://localhost:9001/")
                         .decorator(BraveClient.newDecorator(tracing, "backend2"))
                         .build();

        final Server front =
                Server.builder()
                      .http(8081)
                      .service("/", (ctx, req) -> {
                          final CompletableFuture<HttpResponse> future = new CompletableFuture<>();

                          // armeria에서 client의  요청을 받자마자 backend1으로 reqeust scoping을 자동으로 해줌
                          // ctx로 request scoping을 관리함
                          final CompletableFuture<AggregatedHttpResponse> aggregated1 = backendClient1.get(
                                  "/api").aggregate();

                          // request scoping
                          // 쉬움 1. 오버헤드가 있다.
                          final CompletableFuture<AggregatedHttpResponse> contextAware =
                                  ctx.makeContextAware(aggregated1);
                          // 쉬움 2. 추천
                          // callback이 어떤 thread에서 실행되는지
                          // completedFuture / incompleteFutrue / future.thenAccecptAsync({}, executor)
                          // 3번만 deterministic
                          aggregated1.thenRunAsync(() -> {
                              // callback을 하는 경우 무조건 request scoping을 해야한다.
                              final RequestContext requestContext1 = RequestContext.currentOrNull();
                              assert requestContext1 == null;

                              // leak을 제거하기 위해서 필요하다
                              try (SafeCloseable ignored = ctx.push()) {
                                  // armeria에서는 어떻게 request scoping을 해야하는지 알 수 없음
                                  final CompletableFuture<AggregatedHttpResponse> aggregated2 = backendClient2.get(
                                          "/api").aggregate();
                                  aggregated2.thenAccept(res  -> {
                                      future.complete(res.toHttpResponse());
                                  });
                              }
                          }, ctx.eventLoop());

                          // thread local에서 관리함
                          final RequestContext requestContext =  RequestContext.currentOrNull();
                          assert requestContext != null;
                          aggregated1.thenRun(()  ->  {
                              // callback을 하는 경우 무조건 request scoping을 해야한다.
                              final RequestContext requestContext1 = RequestContext.currentOrNull();
                              assert requestContext1 == null;

                              // leak을 제거하기 위해서 필요하다
                              try (SafeCloseable ignored = ctx.push()) {
                                  // armeria에서는 어떻게 request scoping을 해야하는지 알 수 없음
                                  final CompletableFuture<AggregatedHttpResponse> aggregated2 = backendClient2.get(
                                          "/api").aggregate();
                                  aggregated2.thenAccept(res  -> {
                                      future.complete(res.toHttpResponse());
                                  });
                              }
                          });
//                          final CompletableFuture<AggregatedHttpResponse> aggregated2 = backendClient2.get(
//                                  "/api").aggregate();

//                          CompletableFuture.allOf(aggregated1, aggregated2).thenRun(
//                                  () -> future.complete(HttpResponse.of(200)));

                          return HttpResponse.from(future);
                      })
                      .decorator(BraveService.newDecorator(tracing))
                      .decorator(LoggingService.newDecorator())
                      .build();

        front.start().join();
        server1.start().join();
        server2.start().join();
    }
}
