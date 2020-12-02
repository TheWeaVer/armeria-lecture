package armeria.lecture.week2;

import com.linecorp.armeria.client.Endpoint;
import com.linecorp.armeria.client.WebClient;
import com.linecorp.armeria.client.endpoint.EndpointGroup;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.RequestHeaders;
import com.linecorp.armeria.common.SessionProtocol;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServiceRequestContext;

public class ProxyServer {

    public static void main(String[] args) {

        // Server를 여러개 만들 수 있다. port를 여러개 두어서
        final Server server = Server.builder()
                                    .http(8000)
                                    .requestTimeoutMillis(0)
                                    .serviceUnder("/", new ProxyService())
                                    .build();
        server.start().join();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop().join();
            System.err.println("Server has been stopped.");
        }));
    }

    // client로 부터 요청은 받아서, ProxyClient를 만들어서, ReactiveServer에  전달
    private static class ProxyService implements HttpService {
        final WebClient client;

        ProxyService() {
            // EndpointGroup
            // weighted round robin default는 1000
            // EndpointSelectionoStrategy
            // health check는 되지 않고 있다.
            // 다른 곳에서 정보를 얻어올 수 있다. client-service-discovery
            final EndpointGroup endpointGroup = EndpointGroup.of(Endpoint.of("127.0.0.1:8081"),
                                                                 Endpoint.of("127.0.0.1:8082"),
                                                                 Endpoint.of("127.0.0.1:8083"));
            client = WebClient.of(SessionProtocol.HTTP, endpointGroup); // ~ 8083
        }

        // browser로 부터 요청이 들어온다.
        @Override
        public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
            // header 수정
            req.withHeaders(RequestHeaders.of(req.headers().toBuilder().add("foo","bar").build()));
            return client.execute(req);
        }
    }
}
