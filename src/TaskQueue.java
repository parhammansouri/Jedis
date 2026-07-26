public class TaskQueue {
    private final Runnable[] items;
    private int head;
    private int tail;
    private int count;
    private boolean closed;

    public TaskQueue(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        items = new Runnable[capacity];
    }

    public synchronized boolean offer(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task must not be null");
        }
        if (closed || count == items.length) {
            return false;
        }
        items[tail] = task;
        tail = (tail + 1) % items.length;
        count++;
        notifyAll();
        return true;
    }

    public synchronized Runnable poll(long timeoutMillis) throws InterruptedException {
        if (timeoutMillis < 0) {
            timeoutMillis = 0;
        }
        if (timeoutMillis == 0) {
            while (count == 0 && !closed) {
                wait();
            }
        } else {
            long deadline = System.currentTimeMillis() + timeoutMillis;
            long remaining = timeoutMillis;
            while (count == 0 && !closed && remaining > 0) {
                wait(remaining);
                remaining = deadline - System.currentTimeMillis();
            }
        }
        if (count == 0) {
            return null;
        }
        Runnable task = items[head];
        items[head] = null;
        head = (head + 1) % items.length;
        count--;
        notifyAll();
        return task;
    }

    public synchronized int clear() {
        int removed = count;
        while (count > 0) {
            items[head] = null;
            head = (head + 1) % items.length;
            count--;
        }
        head = 0;
        tail = 0;
        notifyAll();
        return removed;
    }

    public synchronized void close() {
        closed = true;
        notifyAll();
    }

    public synchronized int size() {
        return count;
    }
}
