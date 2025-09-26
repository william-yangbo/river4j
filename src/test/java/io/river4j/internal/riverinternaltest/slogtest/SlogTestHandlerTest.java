package io.river4j.internal.riverinternaltest.slogtest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Test class for SlogTest handler functionality.
 * This mirrors the Go slog_test_handler_test.go tests.
 */
@Execution(ExecutionMode.CONCURRENT)
class SlogTestHandlerTest {

    /**
     * Test different log levels - equivalent to TestSlogTestHandler_levels in Go.
     * This test doesn't assert anything due to the inherent difficulty of testing
     * this test helper, but it can be run with verbose output to observe that it's
     * working correctly.
     */
    @Test
    @Execution(ExecutionMode.CONCURRENT)
    void testSlogTestHandler_levels() throws InterruptedException {
        TestCase[] testCases = {
            new TestCase("FINEST", Level.FINEST),
            new TestCase("INFO", Level.INFO),
            new TestCase("WARNING", Level.WARNING),
            new TestCase("SEVERE", Level.SEVERE)
        };

        // Use CountDownLatch to run tests concurrently like Go's t.Parallel()
        CountDownLatch latch = new CountDownLatch(testCases.length);
        ExecutorService executor = Executors.newCachedThreadPool();

        for (TestCase testCase : testCases) {
            executor.submit(() -> {
                try {
                    Logger logger = SlogTest.newLogger("TestLogger_" + testCase.desc, testCase.level);

                    logger.finest("debug message");
                    logger.info("info message");
                    logger.warning("warn message");
                    logger.severe("error message");
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Stress test for concurrent logging - equivalent to TestSlogTestHandler_stress in Go.
     */
    @Test
    @Execution(ExecutionMode.CONCURRENT)
    void testSlogTestHandler_stress() throws InterruptedException {
        Logger logger = SlogTest.newLogger("StressTestLogger");
        
        CountDownLatch latch = new CountDownLatch(10);
        ExecutorService executor = Executors.newCachedThreadPool();

        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        logger.info("message from thread " + threadId + ", iteration " + j + ", key=value");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * Helper class to represent test cases, similar to Go's anonymous struct.
     */
    private static class TestCase {
        final String desc;
        final Level level;

        TestCase(String desc, Level level) {
            this.desc = desc;
            this.level = level;
        }
    }
}