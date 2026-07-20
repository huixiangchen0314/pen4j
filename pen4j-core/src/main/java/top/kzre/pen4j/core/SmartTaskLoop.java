package top.kzre.pen4j.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 通用 Boss 线程，当有任务注册时自动启动，所有任务注销后自动停止。
 * 适用于周期性探测、事件轮询等场景。
 */
public class SmartTaskLoop implements Runnable {
    private final String name;
    private final long sleepMillis; // 每轮循环后的休眠时间（毫秒）
    private final List<Runnable> tasks = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread thread;

    /**
     * @param name        线程名称
     * @param sleepMillis 每轮任务执行后的休眠毫秒数，建议 ≥1
     */
    public SmartTaskLoop(String name, long sleepMillis) {
        this.name = name;
        this.sleepMillis = sleepMillis;
    }

    /**
     * 注册一个任务。如果 Boss 线程未启动，则自动启动。
     */
    public synchronized void register(Runnable task) {
        tasks.add(task);
        ensureRunning();
    }

    /**
     * 注销一个任务。如果任务列表为空且 Boss 线程正在运行，则自动停止。
     */
    public synchronized void unregister(Runnable task) {
        tasks.remove(task);
        if (tasks.isEmpty()) {
            stopIfRunning();
        }
    }

    // ─── 内部启停逻辑 ───

    private void ensureRunning() {
        if (running.compareAndSet(false, true)) {
            thread = new Thread(this, name);
            thread.setDaemon(true);
            thread.start();
        }
    }

    private void stopIfRunning() {
        if (running.compareAndSet(true, false)) {
            if (thread != null) {
                thread.interrupt();
                try {
                    thread.join(2000);
                } catch (InterruptedException ignored) {
                }
                thread = null;
            }
        }
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                // 执行当前注册的所有任务
                for (Runnable task : tasks) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        // 单个任务异常不影响其他任务
                        // 可在此记录日志
                    }
                }
                // 休眠控制循环速率
                if (sleepMillis > 0) {
                    Thread.sleep(sleepMillis);
                } else {
                    Thread.sleep(1); // 避免 CPU 空转
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}