package dev.sh3lby.craftsocial.async;

import org.bukkit.plugin.Plugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Менеджер асинхронных задач для выполнения HTTP запросов.
 * Использует отдельный ExecutorService, чтобы не блокировать главный поток сервера.
 */
public class AsyncTaskManager {
    private static final Logger logger = LoggerFactory.getLogger(AsyncTaskManager.class);
    
    private final Plugin plugin;
    private ExecutorService executorService;
    private final int threadPoolSize;
    private final int queueSize;
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    
    public AsyncTaskManager(Plugin plugin, int threadPoolSize, int queueSize) {
        this.plugin = plugin;
        this.threadPoolSize = threadPoolSize;
        this.queueSize = queueSize;
        initializeExecutor();
    }
    
    private void initializeExecutor() {
        executorService = new ThreadPoolExecutor(
            threadPoolSize,
            threadPoolSize,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueSize),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "CraftSocial-Async-" + threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // Если очередь переполнена, выполняем в вызывающем потоке
        );
        
        logger.info("AsyncTaskManager initialized with {} threads and queue size {}", threadPoolSize, queueSize);
    }
    
    /**
     * Выполняет задачу асинхронно в отдельном потоке.
     * 
     * @param task Задача для выполнения
     * @return CompletableFuture для отслеживания результата
     */
    public <T> CompletableFuture<T> execute(Callable<T> task) {
        activeTasks.incrementAndGet();
        
        CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                logger.error("Error executing async task", e);
                throw new RuntimeException(e);
            } finally {
                activeTasks.decrementAndGet();
            }
        }, executorService);
        
        return future;
    }
    
    /**
     * Выполняет задачу асинхронно без возвращаемого значения.
     * 
     * @param task Задача для выполнения
     * @return CompletableFuture для отслеживания завершения
     */
    public CompletableFuture<Void> execute(Runnable task) {
        activeTasks.incrementAndGet();
        
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                task.run();
            } catch (Exception e) {
                logger.error("Error executing async task", e);
                throw new RuntimeException(e);
            } finally {
                activeTasks.decrementAndGet();
            }
        }, executorService);
        
        return future;
    }
    
    /**
     * Получить количество активных задач.
     * 
     * @return Количество активных задач
     */
    public int getActiveTasksCount() {
        return activeTasks.get();
    }
    
    /**
     * Graceful shutdown - завершает все задачи при выключении плагина.
     */
    public void shutdown() {
        logger.info("Shutting down AsyncTaskManager...");
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("AsyncTaskManager did not terminate gracefully, forcing shutdown...");
                executorService.shutdownNow();
                
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.error("AsyncTaskManager did not terminate");
                }
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted while shutting down AsyncTaskManager", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("AsyncTaskManager shut down complete");
    }
    
    /**
     * Проверяет, доступен ли ExecutorService.
     * 
     * @return true если доступен
     */
    public boolean isShutdown() {
        return executorService.isShutdown();
    }
}
