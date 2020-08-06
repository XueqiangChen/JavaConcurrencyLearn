package com.cxq.part1.chapter13;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.IntStream;

public class MyThreadPool extends Thread {
    // 实现一个简易版的线程池
    // 创建和释放线程是比较耗时的；浪费资源；
    // 线程池的几个概念：
    // 1. 任务队列
    // 2. 拒绝策略(抛出异常，直接丢弃，阻塞，临时队列)
    // 3. 初始化值 init(min)
    // 4. active
    // 5. max 线程池中线程的最大个数
    //
    // max >= active >= min
    //
    // 线程池有一个初始线程，当线程数比较多，并维持在一个特定数量的时候，线程数增长到active，
    // 线程继续增长后，达到最大线程数，后面的任务存储到任务队列中，任务队列满了之后，就会触发
    // 拒绝策略。
    //
    // 异步执行，批处理等
    // Quartz - 有自定义的线程池，可以参考

    private int size; //当前任务的大小

    private final int queueSize;

//    private final static int DEFAULT_SIZE = 1000;

    private final static int DEFAULT_TASK_QUEUE_SIZE = 2000;

    private final static LinkedList<Runnable> TASK_QUEUE = new LinkedList<>();//任务队列，用来放待执行的任务

    private static volatile int SEQUENCE = 0;

    private final static ThreadGroup GROUP = new ThreadGroup("Pool-group");

    private final static String THREAD_PREFIX = "My-Thread-Pool-";

    private final static List<WorkerTask> THREAD_QUEUE = new ArrayList<>();

    private final DiscardPolicy discardPolicy;

    public static final DiscardPolicy DEFAULT_DISCARD_POLICY = () -> {
        throw new DiscardException("Discard This Task.");
    };

    private volatile boolean destroy = false;//销毁后不能再提交任务

    private int min;

    private int max;

    private int active;

    public MyThreadPool() {
        this(4, 8, 12, DEFAULT_TASK_QUEUE_SIZE, DEFAULT_DISCARD_POLICY);
    }

    public MyThreadPool(int min, int active, int max, int queueSize, DiscardPolicy discardPolicy) {
        this.min = min;
        this.active = active;
        this.max = max;

        this.queueSize = queueSize;
        this.discardPolicy = discardPolicy;
        init();
    }

    private void init() {
        for (int i = 0; i < this.min; i++) {
            createWorkTask();
        }

        this.size = min;
        start();
    }

    // 提交任务
    public void submit(Runnable runnable) {
        if (destroy)
            throw new IllegalStateException("The thread pool already destroy and not allow submit task.");

        synchronized (TASK_QUEUE) {
            if (TASK_QUEUE.size() > queueSize) {
                discardPolicy.discard();
            }
            TASK_QUEUE.addLast(runnable);
            TASK_QUEUE.notifyAll();
        }
    }

    /**
     * 这个线程用来维护线程池的各种因子
     */
    @Override
    public void run() {
        while (!destroy) {
            System.out.printf("Pool#Min:%d, Active:%d, Max:%d, Current:%d, QueueSize:%d\n", min, active, max, size, TASK_QUEUE.size());

            try {
                Thread.sleep(1_000);
                if (TASK_QUEUE.size() > active && size < active) {
                    for (int i = size; i < active; i++) {
                        createWorkTask();
                    }
                    System.out.println("The pool incremented to active.");
                    size = active;
                } else if (TASK_QUEUE.size() > max && size < max) {
                    for (int i = size; i < max; i++) {
                        createWorkTask();
                    }
                    System.out.println("The pool incremented to max.");
                    size = max;
                }

                // 无任务，线程数过多
                synchronized (TASK_QUEUE) {
                    if (TASK_QUEUE.isEmpty() && size > active) {
                        System.out.println("======================Reduce===============");
                        int releaseSize = size - active;
                        for (Iterator<WorkerTask> it = THREAD_QUEUE.iterator(); it.hasNext(); ) {
                            if (releaseSize <= 0)
                                break;

                            WorkerTask task = it.next();
                            task.close();
                            task.interrupt();
                            it.remove();
                            releaseSize--;
                        }

                        size = active;
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void createWorkTask() {
        WorkerTask workerTask = new WorkerTask(GROUP, THREAD_PREFIX + (SEQUENCE++));
        workerTask.start();
        THREAD_QUEUE.add(workerTask);
    }

    public void shutdown() throws InterruptedException {
        while (!TASK_QUEUE.isEmpty()) {
            Thread.sleep(50);
        }

        synchronized (THREAD_QUEUE) {
            int initVal = THREAD_QUEUE.size();
            while (initVal > 0) {
                for (WorkerTask task : THREAD_QUEUE) {
                    if (task.getTaskState() == TaskState.BLOCKED) {
                        task.interrupt();
                        // 中断的时候已经在执行run方法了，需要将状态设置成 DEAD
                        task.close();
                        initVal--;
                    } else {
                        Thread.sleep(10);
                    }
                }
            }
        }
        this.destroy = true;

        System.out.println("The Thread pool disposed");
    }

    public int getSize() {
        return size;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public boolean isDestroy() {
        return this.destroy;
    }

    private enum TaskState {
        FREE, RUNNING, BLOCKED, DEAD
    }

    public static class DiscardException extends RuntimeException {
        public DiscardException(String message) {
            super(message);
        }
    }

    public interface DiscardPolicy {
        void discard() throws DiscardException;
    }

    // 线程池的工作线程，默认线程数量是10个
    private static class WorkerTask extends Thread {

        private volatile TaskState taskState = TaskState.FREE; //默认任务状态

        private TaskState getTaskState() {
            return this.taskState;
        }

        public void close() {
            this.taskState = TaskState.DEAD;
        }

        public WorkerTask(ThreadGroup threadGroup, String threadName) {
            super(threadGroup, threadName);
        }

        // 执行完任务不能立即结束, 去队列中获取任务执行
        public void run() {
            OUTER:
            while (this.taskState != TaskState.DEAD) {
                Runnable runnable;
                synchronized (TASK_QUEUE) {
                    while (TASK_QUEUE.isEmpty()) {
                        try {
                            this.taskState = TaskState.BLOCKED;
                            TASK_QUEUE.wait();
                        } catch (InterruptedException e) {
                            System.out.println("Close");
                            break OUTER; // 被中断时要退出到最外层的循环
                        }
                    }
                    runnable = TASK_QUEUE.removeFirst(); //任务队列不为空，取出任务执行
                }
                // 这一段不需要放在锁里面，否则会因为线程占有锁，其他线程要等待它执行完任务才能重新拿到锁
                if (runnable != null) {
                    this.taskState = TaskState.RUNNING;
                    runnable.run();
                    this.taskState = TaskState.FREE;
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        MyThreadPool threadPool = new MyThreadPool();
        IntStream.rangeClosed(0, 40)
                .forEach(i -> threadPool.submit(() -> {
                    System.out.println("The runnable " + i + " be serviced by " + Thread.currentThread().getName() + " start.");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    System.out.println("The runnable " + i + " be serviced by " + Thread.currentThread().getName() + " finished.");
                }));
//        Thread.sleep(10000);
//        threadPool.shutdown();
//        threadPool.submit(()-> System.out.println("==================="));
    }
}
