public class MiniThreadPool {
    private static final int RUNNING = 0;
    private static final int SHUTDOWN = 1;
    private static final int STOP = 2;
    private static final int TERMINATED = 3;

    private final int coreSize;
    private final int maxSize;
    private final long keepAliveMillis;
    private final TaskQueue queue;
    private final Thread[] workers;
    private final Object lock = new Object();

    private int state = RUNNING;
    private int workerCount;
    private int activeCount;
    private long completedTaskCount;

    public MiniThreadPool(int coreSize, int maxSize, int queueCapacity, long keepAliveMillis) {
        if (coreSize < 1 || maxSize < coreSize || queueCapacity < 1 || keepAliveMillis < 0) {
            throw new IllegalArgumentException("invalid pool arguments");
        }
        this.coreSize = coreSize;
        this.maxSize = maxSize;
        this.keepAliveMillis = keepAliveMillis;
        this.queue = new TaskQueue(queueCapacity);
        this.workers = new Thread[maxSize];
    }

    public void execute(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        synchronized (lock) {
            ensureAccepting();
            if (workerCount < coreSize) {
                addWorker(task);
                return;
            }
        }
        if (queue.offer(task)) {
            return;
        }
        synchronized (lock) {
            ensureAccepting();
            if (workerCount < maxSize) {
                addWorker(task);
                return;
            }
        }
        throw new RejectedTaskException("task rejected");
    }

    public SimpleFuture submit(final Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        final SimpleFuture result = new SimpleFuture();
        execute(new Runnable() {
            public void run() {
                try {
                    task.run();
                    result.completeSuccessfully();
                } catch (Throwable throwable) {
                    result.completeWithError(throwable);
                }
            }
        });
        return result;
    }

    public void shutdown() {
        synchronized (lock) {
            if (state == RUNNING) {
                state = SHUTDOWN;
                queue.close();
                lock.notifyAll();
            }
        }
    }

    public int shutdownNow() {
        Thread[] snapshot;
        int removed;
        synchronized (lock) {
            if (state < STOP) {
                state = STOP;
            }
            queue.close();
            removed = queue.clear();
            snapshot = copyWorkers();
            lock.notifyAll();
        }
        for (int i = 0; i < snapshot.length; i++) {
            if (snapshot[i] != null) {
                snapshot[i].interrupt();
            }
        }
        return removed;
    }

    public boolean awaitTermination(long timeoutMillis) throws InterruptedException {
        if (timeoutMillis < 0) {
            timeoutMillis = 0;
        }
        synchronized (lock) {
            if (state == TERMINATED) {
                return true;
            }
            if (timeoutMillis == 0) {
                while (state != TERMINATED) {
                    lock.wait();
                }
                return true;
            }
            long deadline = System.currentTimeMillis() + timeoutMillis;
            long remaining = timeoutMillis;
            while (state != TERMINATED && remaining > 0) {
                lock.wait(remaining);
                remaining = deadline - System.currentTimeMillis();
            }
            return state == TERMINATED;
        }
    }

    public boolean isShutdown() {
        synchronized (lock) {
            return state != RUNNING;
        }
    }

    public boolean isTerminated() {
        synchronized (lock) {
            return state == TERMINATED;
        }
    }

    public int getWorkerCount() {
        synchronized (lock) {
            return workerCount;
        }
    }

    public int getActiveCount() {
        synchronized (lock) {
            return activeCount;
        }
    }

    public int getQueueSize() {
        return queue.size();
    }

    public long getCompletedTaskCount() {
        synchronized (lock) {
            return completedTaskCount;
        }
    }

    private void ensureAccepting() {
        if (state != RUNNING) {
            throw new RejectedTaskException("pool is shut down");
        }
    }

    private void addWorker(Runnable firstTask) {
        final int slot = firstFreeSlot();
        Thread worker = new Thread(new Worker(firstTask), "MiniThreadPool-worker-" + slot);
        workers[slot] = worker;
        workerCount++;
        worker.start();
    }

    private int firstFreeSlot() {
        for (int i = 0; i < workers.length; i++) {
            if (workers[i] == null) {
                return i;
            }
        }
        throw new RejectedTaskException("no worker slot available");
    }

    private Thread[] copyWorkers() {
        Thread[] copy = new Thread[workers.length];
        for (int i = 0; i < workers.length; i++) {
            copy[i] = workers[i];
        }
        return copy;
    }

    private void workerExit(Thread current) {
        synchronized (lock) {
            for (int i = 0; i < workers.length; i++) {
                if (workers[i] == current) {
                    workers[i] = null;
                    break;
                }
            }
            workerCount--;
            if (workerCount == 0 && state != RUNNING) {
                state = TERMINATED;
            }
            lock.notifyAll();
        }
    }

    private final class Worker implements Runnable {
        private Runnable firstTask;

        Worker(Runnable firstTask) {
            this.firstTask = firstTask;
        }

        public void run() {
            Thread current = Thread.currentThread();
            try {
                Runnable task = firstTask;
                firstTask = null;
                while (task != null || shouldContinue()) {
                    if (task == null) {
                        task = nextTask();
                    }
                    if (task == null) {
                        break;
                    }
                    beforeTask();
                    try {
                        task.run();
                    } catch (Throwable ignored) {
                    } finally {
                        afterTask();
                        task = null;
                    }
                }
            } finally {
                workerExit(current);
            }
        }

        private boolean shouldContinue() {
            synchronized (lock) {
                return state == RUNNING || (state == SHUTDOWN && queue.size() > 0);
            }
        }

        private Runnable nextTask() {
            try {
                synchronized (lock) {
                    if (state == STOP) {
                        return null;
                    }
                    if (state == SHUTDOWN && queue.size() == 0) {
                        return null;
                    }
                    if (workerCount > coreSize) {
                        return queue.poll(keepAliveMillis);
                    }
                }
                return queue.poll(0);
            } catch (InterruptedException interrupted) {
                synchronized (lock) {
                    if (state == STOP) {
                        return null;
                    }
                }
                return null;
            }
        }

        private void beforeTask() {
            synchronized (lock) {
                activeCount++;
            }
        }

        private void afterTask() {
            synchronized (lock) {
                activeCount--;
                completedTaskCount++;
                lock.notifyAll();
            }
        }
    }
}
