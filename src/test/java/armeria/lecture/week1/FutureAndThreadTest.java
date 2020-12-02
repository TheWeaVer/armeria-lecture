package armeria.lecture.week1;

import static org.awaitility.Awaitility.await;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

class FutureAndThreadTest {

    @Test
    void simpleCallback() {
        currentThreadName();
        final CompletableFuture<String> future = new CompletableFuture<>();
        future.thenAccept(str -> {
            System.err.println("Hello " + str);
            currentThreadName();
        });
        // callback이 실행됨
        future.complete("Armeria");
    }

    @Test
    void futureGet() throws Exception {
        currentThreadName();
        final CompletableFuture<String> future = new CompletableFuture<>();
        // Never complete
        //
        future.get();

        future.complete("Armeria");
    }

    @Test
    void completeByAnotherThread() {
        currentThreadName();
        final CompletableFuture<String> future = new CompletableFuture<>();
        future.thenAccept(str -> {
            System.err.println("Hello " + str);
            currentThreadName();
        });

        final ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            // 다른 thread에서 instance를 만든다.
            // future을 Callback을 실행하는건 Complete을 만드는 THREAD에서 수행한다.
            future.complete("foo");
        });

//        await().until(future::isDone);
    }

    @Test
    void completeByAnotherThread_completeFirst() {
        currentThreadName();
        final CompletableFuture<String> future = new CompletableFuture<>();
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            future.complete("foo");
        });

        // 인스턴스가 생성되면, 생성된 녀석이
        // 아나리면 callback만 하고 넘어간다.
        // happens before? https://www.logicbig.com/tutorials/core-java-tutorial/java-multi-threading/happens-before.html
        future.thenAccept(str -> {
            // main 또는 thread executor의 다른 thread에서 수행될 수 있다.
            currentThreadName();
        });

        await().until(future::isDone);
    }

    @Test
    void completeByAnotherThread_thenAcceptAsync() {
        currentThreadName();
        final CompletableFuture<String> future = new CompletableFuture<>();
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        // 해당 executore에 의해서 수행을 하라
        future.thenAcceptAsync(str -> {
            currentThreadName();
        }, executor);

        executor.submit(() -> {
            future.complete("foo");
        });

        await().until(future::isDone);
    }

    // thread를 하나 가져와서 실행
    // CompletableFuture#defaultExecutor()
    @Test
    void completeByAnotherThread_thenAcceptAsync_forkJoin() {
        currentThreadName();
        final CompletableFuture<String> future = new CompletableFuture<>();
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        future.thenAcceptAsync(str -> {
            currentThreadName();
        });

        executor.submit(() -> {
            future.complete("foo");
        });

        await().until(future::isDone);
    }

    static void currentThreadName() {
        System.err.println("Name: " + Thread.currentThread().getName());
    }
}
