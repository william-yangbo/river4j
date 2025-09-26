package io.river4j.internal.riverinternaltest.slogtest;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * SlogTest provides logging utilities for tests.
 * This is the Java equivalent of the Go slogtest package.
 */
public class SlogTest {
    
    /**
     * Creates a logger that outputs to the test framework.
     * In Java, we use a custom handler that outputs to System.out
     * which will be captured by test frameworks like JUnit.
     */
    public static Logger newLogger(String name) {
        return newLogger(name, Level.INFO);
    }
    
    /**
     * Creates a logger with a specific minimum level that outputs to the test framework.
     */
    public static Logger newLogger(String name, Level minLevel) {
        Logger logger = Logger.getLogger(name);
        logger.setUseParentHandlers(false);
        logger.setLevel(minLevel);
        
        // Add our custom test handler
        TestLogHandler handler = new TestLogHandler();
        handler.setLevel(minLevel);
        logger.addHandler(handler);
        
        return logger;
    }
    
    /**
     * Custom log handler that outputs to System.out for test visibility.
     */
    private static class TestLogHandler extends Handler {
        
        @Override
        public void publish(LogRecord record) {
            if (!isLoggable(record)) {
                return;
            }
            
            String message = formatMessage(record);
            System.out.println(message);
        }
        
        @Override
        public void flush() {
            System.out.flush();
        }
        
        @Override
        public void close() throws SecurityException {
            // Nothing to close
        }
        
        private String formatMessage(LogRecord record) {
            return String.format("%s [%s] %s: %s",
                java.time.Instant.ofEpochMilli(record.getMillis()),
                record.getLevel(),
                record.getLoggerName(),
                record.getMessage()
            );
        }
    }
}