package armeria.lecture.week2;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.linecorp.armeria.common.HttpData;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpRequest;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.HttpResponseWriter;
import com.linecorp.armeria.common.HttpStatus;
import com.linecorp.armeria.common.ResponseHeaders;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;

public final class AnimationService implements HttpService {

    private static final List<String> frames = Arrays.asList(
            "<pre>" +
            "╔════╤╤╤╤════╗\n" +
            "║    │││ \\   ║\n" +
            "║    │││  O  ║\n" +
            "║    OOO     ║" +
            "</pre>",

            "<pre>" +
            "╔════╤╤╤╤════╗\n" +
            "║    ││││    ║\n" +
            "║    ││││    ║\n" +
            "║    OOOO    ║" +
            "</pre>",

            "<pre>" +
            "╔════╤╤╤╤════╗\n" +
            "║   / │││    ║\n" +
            "║  O  │││    ║\n" +
            "║     OOO    ║" +
            "</pre>",

            "<pre>" +
            "╔════╤╤╤╤════╗\n" +
            "║    ││││    ║\n" +
            "║    ││││    ║\n" +
            "║    OOOO    ║" +
            "</pre>"
    );
    private final long frameIntervalMillis;

    public AnimationService(long frameIntervalMillis) {
        this.frameIntervalMillis = frameIntervalMillis;
    }

    // 이 부분을 구현하자
    @Override
    public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
        final HttpResponseWriter response = HttpResponse.streaming();
        //response.write(ResponseHeaders.of(200));
        //response.whenConsumed().thenRun(runnable);
        //ctx.eventLoop().schedule(runnable, frameIntervalMillis, TimeUnit.MILLISECONDS);

        // header없이 body만 보내는 것은 spec위반이다.
        response.write(ResponseHeaders.of(200));
//        response.whenConsumed().thenRun(() -> {
//            int index;
//            streamData(ctx, response, 0);
//
//            response.write(HttpData.ofUtf8(frames.get(index)));
//            response.whenConsumed().thenRun(() -> {
//                ctx.eventLoop().schedule(() -> {
//                    streamData(ctx, response, (index + 1) % 4);
//                }, frameIntervalMillis, TimeUnit.MILLISECONDS);
//            });
//
//            //// response.close(); when count 10 return;
//        });

        return response;
    }
}

//    @Override
//    public HttpResponse serve(ServiceRequestContext ctx, HttpRequest req) throws Exception {
//        final HttpResponseWriter streaming = HttpResponse.streaming();
//        streaming.write(ResponseHeaders.of(200));
//        streaming.whenConsumed().thenRun(() -> {
//            streamData(ctx, streaming, 0, 0);
//        });
//        return streaming;
//    }
//    private void streamData(ServiceRequestContext ctx, HttpResponseWriter streaming, int frameIndex, int count) {
//        streaming.write(HttpData.ofUtf8(frames.get(frameIndex)));
//        streaming.whenConsumed().thenRun(() -> {
//            ctx.eventLoop().schedule(() -> streamData(ctx, streaming, (frameIndex + 1) % 4, count + 1),
//                                     frameIntervalMillis, TimeUnit.MILLISECONDS);
//        });
//    }
