package dev.twelvetimes.vector.task;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 只保护短数据库阶段，不让CPU计算占用许可。公平锁使关闭方排队后新的写入不能插队。
 * 关闭方先宣布不再发许可，再等已经进入的事务（含物理提交及确认）退出，之后才可销毁数据源。
 */
public final class TaskWriteBarrier {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    private volatile boolean open = true;

    public Lease enter() {
        lock.readLock().lock();
        if (!open) {
            lock.readLock().unlock();
            return null;
        }
        return new Lease();
    }

    public void closeAndDrain() {
        open = false;
        lock.writeLock().lock();
        try {
            // 获得写锁意味着所有已获许可的短事务均已离开，不能强制中断持有H2连接的线程。
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isOpen() { return open; }

    public final class Lease implements AutoCloseable {
        private boolean released;
        private Lease() { }
        @Override public void close() {
            if (!released) { released = true; lock.readLock().unlock(); }
        }
    }
}
