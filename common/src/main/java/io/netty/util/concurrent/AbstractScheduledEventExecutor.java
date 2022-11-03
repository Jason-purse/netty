/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.concurrent;

import io.netty.util.internal.DefaultPriorityQueue;
import io.netty.util.internal.ObjectUtil;
import io.netty.util.internal.PriorityQueue;

import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

/**
 * Abstract base class for {@link EventExecutor}s that want to support scheduling.
 * 对于想要支持调度的EventExecutor的基类 ...
 */
public abstract class AbstractScheduledEventExecutor extends AbstractEventExecutor {

    private static final Comparator<ScheduledFutureTask<?>> SCHEDULED_FUTURE_TASK_COMPARATOR =
            new Comparator<ScheduledFutureTask<?>>() {
                @Override
                public int compare(ScheduledFutureTask<?> o1, ScheduledFutureTask<?> o2) {
                    return o1.compareTo(o2);
                }
            };

    // 启动时间 = System.nanoTime()
    private static final long START_TIME = System.nanoTime();

    // wakeup_task
    static final Runnable WAKEUP_TASK = new Runnable() {
       @Override
       public void run() { } // Do nothing
    };

    // 调度队列(优先级)
    PriorityQueue<ScheduledFutureTask<?>> scheduledTaskQueue;

    long nextTaskId;

    protected AbstractScheduledEventExecutor() {
    }

    protected AbstractScheduledEventExecutor(EventExecutorGroup parent) {
        super(parent);
    }

    /**
     * Get the current time in nanoseconds by this executor's clock. This is not the same as {@link System#nanoTime()}
     * for two reasons:
     *
     * <ul>
     *     <li>We apply a fixed offset to the {@link System#nanoTime() nanoTime}</li>
     *     <li>Implementations (in particular EmbeddedEventLoop) may use their own time source so they can control time
     *     for testing purposes.</li>
     * </ul>
     * 通过执行器的 时钟进行获取当前时间(纳秒) ...
     * 这不等价于 System#nanoTime() 有两点原因 ..
     * - 于System#nanoTime() 有一个固定的偏距 .
     * - 实现(尤其是EmbeddedEventLoop) 也许使用他们自己的时间来源(因此他们能够控制时间 - 表测试目的) ...
     *
     *
     */
    protected long getCurrentTimeNanos() {
        return defaultCurrentTimeNanos();
    }

    /**
     * @deprecated Use the non-static {@link #getCurrentTimeNanos()} instead.
     */
    @Deprecated
    protected static long nanoTime() {
        return defaultCurrentTimeNanos();
    }

    static long defaultCurrentTimeNanos() {
        // 算出当前时间的Nano单位(当前时间 减去一开始的时间起点)
        return System.nanoTime() - START_TIME;
    }

    static long deadlineNanos(long nanoTime, long delay) {
        long deadlineNanos = nanoTime + delay;
        // Guard against overflow
        return deadlineNanos < 0 ? Long.MAX_VALUE : deadlineNanos;
    }

    /**
     * Given an arbitrary deadline {@code deadlineNanos}, calculate the number of nano seconds from now
     * {@code deadlineNanos} would expire.
     * @param deadlineNanos An arbitrary deadline in nano seconds.
     * @return the number of nano seconds from now {@code deadlineNanos} would expire.
     */
    protected static long deadlineToDelayNanos(long deadlineNanos) {
        return ScheduledFutureTask.deadlineToDelayNanos(defaultCurrentTimeNanos(), deadlineNanos);
    }

    /**
     * The initial value used for delay and computations based upon a monatomic time source.
     * @return initial value used for delay and computations based upon a monatomic time source.
     */
    protected static long initialNanoTime() {
        return START_TIME;
    }

    PriorityQueue<ScheduledFutureTask<?>> scheduledTaskQueue() {
        if (scheduledTaskQueue == null) {
            scheduledTaskQueue = new DefaultPriorityQueue<ScheduledFutureTask<?>>(
                    SCHEDULED_FUTURE_TASK_COMPARATOR,
                    // Use same initial capacity as java.util.PriorityQueue
                    11);
        }
        return scheduledTaskQueue;
    }

    private static boolean isNullOrEmpty(Queue<ScheduledFutureTask<?>> queue) {
        return queue == null || queue.isEmpty();
    }

    /**
     * Cancel all scheduled tasks.
     *
     * This method MUST be called only when {@link #inEventLoop()} is {@code true}.
     *
     * 取消所有已经调度的任务 ...
     * 这个方法必须在事件循环中调用 ...
     */
    protected void cancelScheduledTasks() {
        assert inEventLoop();
        PriorityQueue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        if (isNullOrEmpty(scheduledTaskQueue)) {
            return;
        }

        final ScheduledFutureTask<?>[] scheduledTasks =
                scheduledTaskQueue.toArray(new ScheduledFutureTask<?>[0]);

        for (ScheduledFutureTask<?> task: scheduledTasks) {
            // 取消但是不移除
            task.cancelWithoutRemove(false);
        }

        // 然后 清理掉(而不需要显式的移除对他们的引用).. 然后以便垃圾回收
        scheduledTaskQueue.clearIgnoringIndexes();
    }

    /**
     * @see #pollScheduledTask(long)
     */
    protected final Runnable pollScheduledTask() {
        return pollScheduledTask(getCurrentTimeNanos());
    }

    /**
     * Return the {@link Runnable} which is ready to be executed with the given {@code nanoTime}.
     * You should use {@link #getCurrentTimeNanos()} to retrieve the correct {@code nanoTime}.
     *
     * 返回在给定nanoTime中可以准备好执行的Runnable ..
     * 你应该使用getCurrentTimeNanos() 去抓取正确的nanoTime ...
     */
    protected final Runnable pollScheduledTask(long nanoTime) {
        assert inEventLoop();

        // 查询调度的任务
        ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
        if (scheduledTask == null || scheduledTask.deadlineNanos() - nanoTime > 0) {
            return null;
        }

        // 移除队列首的一个任务 ..
        scheduledTaskQueue.remove();

        scheduledTask.setConsumed();
        return scheduledTask;
    }

    /**
     * Return the nanoseconds until the next scheduled task is ready to be run or {@code -1} if no task is scheduled.
     */
    protected final long nextScheduledTaskNano() {
        ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
        return scheduledTask != null ? scheduledTask.delayNanos() : -1;
    }

    /**
     * Return the deadline (in nanoseconds) when the next scheduled task is ready to be run or {@code -1}
     * if no task is scheduled.
     */
    protected final long nextScheduledTaskDeadlineNanos() {
        ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
        return scheduledTask != null ? scheduledTask.deadlineNanos() : -1;
    }

    final ScheduledFutureTask<?> peekScheduledTask() {
        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = this.scheduledTaskQueue;
        return scheduledTaskQueue != null ? scheduledTaskQueue.peek() : null;
    }

    /**
     * Returns {@code true} if a scheduled task is ready for processing.
     */
    protected final boolean hasScheduledTasks() {
        ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
        return scheduledTask != null && scheduledTask.deadlineNanos() <= getCurrentTimeNanos();
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        ObjectUtil.checkNotNull(command, "command");
        ObjectUtil.checkNotNull(unit, "unit");
        if (delay < 0) {
            delay = 0;
        }
        validateScheduled0(delay, unit);

        return schedule(new ScheduledFutureTask<Void>(
                this,
                command,
                deadlineNanos(getCurrentTimeNanos(), unit.toNanos(delay))));
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        ObjectUtil.checkNotNull(callable, "callable");
        ObjectUtil.checkNotNull(unit, "unit");
        if (delay < 0) {
            delay = 0;
        }
        validateScheduled0(delay, unit);

        return schedule(new ScheduledFutureTask<V>(
                this, callable, deadlineNanos(getCurrentTimeNanos(), unit.toNanos(delay))));
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        ObjectUtil.checkNotNull(command, "command");
        ObjectUtil.checkNotNull(unit, "unit");
        if (initialDelay < 0) {
            throw new IllegalArgumentException(
                    String.format("initialDelay: %d (expected: >= 0)", initialDelay));
        }
        if (period <= 0) {
            throw new IllegalArgumentException(
                    String.format("period: %d (expected: > 0)", period));
        }
        validateScheduled0(initialDelay, unit);
        validateScheduled0(period, unit);

        return schedule(new ScheduledFutureTask<Void>(
                this, command, deadlineNanos(getCurrentTimeNanos(), unit.toNanos(initialDelay)), unit.toNanos(period)));
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        ObjectUtil.checkNotNull(command, "command");
        ObjectUtil.checkNotNull(unit, "unit");
        if (initialDelay < 0) {
            throw new IllegalArgumentException(
                    String.format("initialDelay: %d (expected: >= 0)", initialDelay));
        }
        if (delay <= 0) {
            throw new IllegalArgumentException(
                    String.format("delay: %d (expected: > 0)", delay));
        }

        validateScheduled0(initialDelay, unit);
        validateScheduled0(delay, unit);

        return schedule(new ScheduledFutureTask<Void>(
                this, command, deadlineNanos(getCurrentTimeNanos(), unit.toNanos(initialDelay)), -unit.toNanos(delay)));
    }

    @SuppressWarnings("deprecation")
    private void validateScheduled0(long amount, TimeUnit unit) {
        validateScheduled(amount, unit);
    }

    /**
     * Sub-classes may override this to restrict the maximal amount of time someone can use to schedule a task.
     *
     * @deprecated will be removed in the future.
     */
    @Deprecated
    protected void validateScheduled(long amount, TimeUnit unit) {
        // NOOP
    }

    final void scheduleFromEventLoop(final ScheduledFutureTask<?> task) {
        // nextTaskId a long and so there is no chance it will overflow back to 0
        scheduledTaskQueue().add(task.setId(++nextTaskId));
    }

    private <V> ScheduledFuture<V> schedule(final ScheduledFutureTask<V> task) {
        if (inEventLoop()) {
            scheduleFromEventLoop(task);
        } else {
            final long deadlineNanos = task.deadlineNanos();
            // task will add itself to scheduled task queue when run if not expired
            if (beforeScheduledTaskSubmitted(deadlineNanos)) {
                execute(task);
            } else {
                lazyExecute(task);
                // Second hook after scheduling to facilitate race-avoidance
                if (afterScheduledTaskSubmitted(deadlineNanos)) {
                    execute(WAKEUP_TASK);
                }
            }
        }

        return task;
    }

    final void removeScheduled(final ScheduledFutureTask<?> task) {
        assert task.isCancelled();
        if (inEventLoop()) {
            scheduledTaskQueue().removeTyped(task);
        } else {
            // task will remove itself from scheduled task queue when it runs
            lazyExecute(task);
        }
    }

    /**
     * Called from arbitrary non-{@link EventExecutor} threads prior to scheduled task submission.
     * Returns {@code true} if the {@link EventExecutor} thread should be woken immediately to
     * process the scheduled task (if not already awake).
     * <p>
     * If {@code false} is returned, {@link #afterScheduledTaskSubmitted(long)} will be called with
     * the same value <i>after</i> the scheduled task is enqueued, providing another opportunity
     * to wake the {@link EventExecutor} thread if required.
     *
     * @param deadlineNanos deadline of the to-be-scheduled task
     *     relative to {@link AbstractScheduledEventExecutor#getCurrentTimeNanos()}
     * @return {@code true} if the {@link EventExecutor} thread should be woken, {@code false} otherwise
     */
    protected boolean beforeScheduledTaskSubmitted(long deadlineNanos) {
        return true;
    }

    /**
     * See {@link #beforeScheduledTaskSubmitted(long)}. Called only after that method returns false.
     *
     * @param deadlineNanos relative to {@link AbstractScheduledEventExecutor#getCurrentTimeNanos()}
     * @return  {@code true} if the {@link EventExecutor} thread should be woken, {@code false} otherwise
     */
    protected boolean afterScheduledTaskSubmitted(long deadlineNanos) {
        return true;
    }
}
