package io.river4j.internal.rivertest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Channel utilities for continuous draining operations.
 * This provides Java equivalents of Go's channel draining functions.
 */
public class ChannelUtils {
    
    /**
     * DiscardContinuously drains continuously out of the given queue and discards
     * anything that comes out of it. Returns a stop function that should be invoked
     * to stop draining. Stop must be invoked before tests finish to stop an
     * internal thread.
     * 
     * This is the Java equivalent of Go's DiscardContinuously function.
     * 
     * @param drainQueue the queue to drain from
     * @return a Runnable that can be called to stop draining
     */
    public static <T> Runnable discardContinuously(BlockingQueue<T> drainQueue) {
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        CompletableFuture<Void> stopped = new CompletableFuture<>();
        
        Thread drainThread = new Thread(() -> {
            try {
                while (!stopRequested.get()) {
                    try {
                        // Poll with timeout to allow checking stop condition
                        drainQueue.poll(100, TimeUnit.MILLISECONDS);
                        // Discard the item (do nothing with it)
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                stopped.complete(null);
            }
        });
        
        drainThread.setDaemon(true);
        drainThread.setName("RiverTest-DiscardContinuously");
        drainThread.start();
        
        return () -> {
            if (stopRequested.compareAndSet(false, true)) {
                try {
                    stopped.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Force interrupt if graceful shutdown fails
                    drainThread.interrupt();
                }
            }
        };
    }
    
    /**
     * DrainContinuously drains continuously out of the given queue and
     * accumulates items that are received from it. Returns a supplier function that can
     * be called to retrieve the current set of received items, and which will also
     * cause the function to shut down and stop draining. This function must be
     * invoked before tests finish to stop an internal thread. It's safe to call
     * it multiple times.
     * 
     * This is the Java equivalent of Go's DrainContinuously function.
     * 
     * @param drainQueue the queue to drain from
     * @return a supplier that returns accumulated items and stops draining when called
     */
    public static <T> java.util.function.Supplier<List<T>> drainContinuously(BlockingQueue<T> drainQueue) {
        List<T> items = new ArrayList<>();
        AtomicBoolean stopRequested = new AtomicBoolean(false);
        AtomicBoolean stopped = new AtomicBoolean(false);
        CompletableFuture<Void> stoppedFuture = new CompletableFuture<>();
        
        Thread drainThread = new Thread(() -> {
            try {
                while (!stopRequested.get()) {
                    try {
                        // Poll with timeout to allow checking stop condition
                        T item = drainQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (item != null) {
                            synchronized (items) {
                                items.add(item);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                
                // Drain until empty after stop requested
                T item;
                while ((item = drainQueue.poll()) != null) {
                    synchronized (items) {
                        items.add(item);
                    }
                }
            } finally {
                stopped.set(true);
                stoppedFuture.complete(null);
            }
        });
        
        drainThread.setDaemon(true);
        drainThread.setName("RiverTest-DrainContinuously");
        drainThread.start();
        
        return () -> {
            if (stopRequested.compareAndSet(false, true)) {
                try {
                    stoppedFuture.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // Force interrupt if graceful shutdown fails
                    drainThread.interrupt();
                }
            }
            
            synchronized (items) {
                return new ArrayList<>(items);
            }
        };
    }
}