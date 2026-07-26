public class SimpleFuture {
    private boolean done;
    private Throwable error;

    public synchronized boolean isDone() {
        return done;
    }

    public synchronized boolean hasFailed() {
        return done && error != null;
    }

    public synchronized Throwable getError() {
        return error;
    }

    public synchronized void waitDone() throws InterruptedException {
        while (!done) {
            wait();
        }
    }

    public synchronized boolean waitDone(long timeoutMillis) throws InterruptedException {
        if (done) {
            return true;
        }
        if (timeoutMillis < 0) {
            timeoutMillis = 0;
        }
        if (timeoutMillis == 0) {
            return done;
        }
        long deadline = System.currentTimeMillis() + timeoutMillis;
        long remaining = timeoutMillis;
        while (!done && remaining > 0) {
            wait(remaining);
            remaining = deadline - System.currentTimeMillis();
        }
        return done;
    }

    synchronized void completeSuccessfully() {
        if (!done) {
            done = true;
            notifyAll();
        }
    }

    synchronized void completeWithError(Throwable throwable) {
        if (!done) {
            error = throwable;
            done = true;
            notifyAll();
        }
    }
}
