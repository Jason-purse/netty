/*
 * Copyright 2013 The Netty Project
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

import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;
import io.netty.util.internal.SystemPropertyUtil;
import io.netty.util.internal.ThrowableUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static io.netty.util.internal.ObjectUtil.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class DefaultPromise<V> extends AbstractFuture<V> implements Promise<V> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultPromise.class);
    // 拒绝执行日志器 ...
    private static final InternalLogger rejectedExecutionLogger =
            InternalLoggerFactory.getInstance(DefaultPromise.class.getName() + ".rejectedExecution");

    // 最大监听器栈深度
    private static final int MAX_LISTENER_STACK_DEPTH = Math.min(8,
            SystemPropertyUtil.getInt("io.netty.defaultPromise.maxListenerStackDepth", 8));

    // 通过FieldUpdater 进行 volatile 字段的更新 ..
    @SuppressWarnings("rawtypes")
    private static final AtomicReferenceFieldUpdater<DefaultPromise, Object> RESULT_UPDATER =
            AtomicReferenceFieldUpdater.newUpdater(DefaultPromise.class, Object.class, "result");

    // (Success 对象 - 减少无用对象的产生 ..) ..
    private static final Object SUCCESS = new Object();
    // 未取消的.
    private static final Object UNCANCELLABLE = new Object();

    // 取消异常HOLDER..
    private static final CauseHolder CANCELLATION_CAUSE_HOLDER = new CauseHolder(
            StacklessCancellationException.newInstance(DefaultPromise.class, "cancel(...)"));

    // 取消堆栈 引用
    private static final StackTraceElement[] CANCELLATION_STACK = CANCELLATION_CAUSE_HOLDER.cause.getStackTrace();

    // 结果 ..
    private volatile Object result;

    // 事件执行器 ...
    private final EventExecutor executor;
    /**
     * One or more listeners. Can be a {@link GenericFutureListener} or a {@link DefaultFutureListeners}.
     * If {@code null}, it means either 1) no listeners were added yet or 2) all listeners were notified.
     *
     * Threading - synchronized(this). We must support adding listeners when there is no EventExecutor.
     *
     * 一个或者多个监听器 ... 能够是GenericFutureListener ... 或者 DefaultFutureListeners ...
     *
     * 如果为null,意味着:
     * 要么 1 没有监听器 / 2  所有监听器已经被通知了 ....
     */
    private Object listeners;
    /**
     * Threading - synchronized(this). We are required to hold the monitor to use Java's underlying wait()/notifyAll().
     *
     * 线程 - synchronized(this), 需要这个监视器 使用java底层的 wait / notifyAll()
     */
    private short waiters;

    /**
     * Threading - synchronized(this).
     * We must prevent concurrent notification and FIFO listener notification if the
     * executor changes.
     *
     * 如果 执行器发生改变 (我们必须阻止当前的通知 并 FIFO 监听器顺序监听)
     */
    private boolean notifyingListeners;

    /**
     * Creates a new instance.
     *
     * It is preferable to use {@link EventExecutor#newPromise()} to create a new promise
     *
     * @param executor
     *        the {@link EventExecutor} which is used to notify the promise once it is complete.
     *        It is assumed this executor will protect against {@link StackOverflowError} exceptions.
     *        The executor may be used to avoid {@link StackOverflowError} by executing a {@link Runnable} if the stack
     *        depth exceeds a threshold.
     *
     */
    public DefaultPromise(EventExecutor executor) {
        this.executor = checkNotNull(executor, "executor");
    }

    /**
     * See {@link #executor()} for expectations of the executor.
     *
     * executor()获取期待的执行器 ...
     */
    protected DefaultPromise() {
        // only for subclasses
        executor = null;
    }

    @Override
    public Promise<V> setSuccess(V result) {
        // 当它成功的时候 ..
        // 它会判断是否已经完成,如果已经完成(不管是取消还是真的完成,它将会return false) ..
        if (setSuccess0(result)) {
            return this;
        }
        throw new IllegalStateException("complete already: " + this);
    }

    @Override
    public boolean trySuccess(V result) {
        // 尝试成功... 一种尝试...
        // 如果已经完成,则return false ..
        return setSuccess0(result);
    }

    // 本质上 set / try 区别不是很大 ..
    // 仅仅一个是抛出异常,一个 返回状态 ...
    @Override
    public Promise<V> setFailure(Throwable cause) {
        if (setFailure0(cause)) {
            return this;
        }
        throw new IllegalStateException("complete already: " + this, cause);
    }

    @Override
    public boolean tryFailure(Throwable cause) {
        return setFailure0(cause);
    }

    // 设置为不可取消的 ...
    // 很显然,不可取消,并没有完成 ...
    @Override
    public boolean setUncancellable() {
        // 如果没有任何状态,则可以修改 ...
        if (RESULT_UPDATER.compareAndSet(this, null, UNCANCELLABLE)) {
            return true;
        }
        // 否则获取结果
        Object result = this.result;

        // 如果完成了,则false,如果取消了,则false ..
        // 否则没有完成 或者 没有取消,则true ..

        // 个人认为,它认为在设置了 UNCANCELLABLE 之后,
        //仅仅在没有完成的情况下是可以设置成功的 / 或者没有取消的情况下 ..
        // 它这里用或者的原因是(它应用程序通过反射修改result,导致在未完成的情况下,导致 setUncancellable 状态异常?) 也同样是排除所有相关
        //状态
        // 也就是说,我们可以反思维判断,是否在isPending ...
        // 也就是 null 和 UNCANCELLABLE的状态进行判断 ...(如果是,则true,否则false) ..
        return !isDone0(result) || !isCancelled0(result);
    }

    @Override
    public boolean isSuccess() {
        Object result = this.result;
        // 获取结果,判断结果是否不等于null 且 非 UNCANCELLABLE 且非 异常...
        return result != null && result != UNCANCELLABLE && !(result instanceof CauseHolder);
    }

    @Override
    public boolean isCancellable() {
        // 取消了,result == null ??
        return result == null;
    }

    // lean 倾斜 / 依靠 / 倾向 / 依赖
    private static final class LeanCancellationException extends CancellationException {
        private static final long serialVersionUID = 2794674970981187807L;

        // Suppress a warning since the method doesn't need synchronization
        @Override
        public Throwable fillInStackTrace() {   // lgtm[java/non-sync-override]
            setStackTrace(CANCELLATION_STACK);
            return this;
        }

        @Override
        public String toString() {
            return CancellationException.class.getName();
        }
    }

    @Override
    public Throwable cause() {
        return cause0(result);
    }

    private Throwable cause0(Object result) {
        // 如果结果不是异常 ...
        if (!(result instanceof CauseHolder)) {
            return null;
        }
        // 否则
        if (result == CANCELLATION_CAUSE_HOLDER) {
            // 如果是取消异常 ...
            CancellationException ce = new LeanCancellationException();

            // 切换异常,并修改为这个异常 ...
            if (RESULT_UPDATER.compareAndSet(this, CANCELLATION_CAUSE_HOLDER, new CauseHolder(ce))) {
                return ce;
            }

            // 考虑的真细致 ..
            // 否则 获取 result
            // 已经被其他线程设置了 ...
            result = this.result;
        }
        return ((CauseHolder) result).cause;
    }

    @Override
    public Promise<V> addListener(GenericFutureListener<? extends Future<? super V>> listener) {
        checkNotNull(listener, "listener");

        // addListener0 加锁
        synchronized (this) {
            addListener0(listener);
        }

        // 如果已经完成,直接通知 ...
        if (isDone()) {
            // 直接执行
            notifyListeners();
        }

        return this;
    }

    @Override
    public Promise<V> addListeners(GenericFutureListener<? extends Future<? super V>>... listeners) {
        checkNotNull(listeners, "listeners");

        // 同样的,一个一个的增加 ..
        synchronized (this) {
            for (GenericFutureListener<? extends Future<? super V>> listener : listeners) {
                // 如果存在null,那么后续的无法增加进去 ..
                // 这可能会导致bug ...
                // 但是没有人会这样写,可以忽略 ..
                if (listener == null) {
                    break;
                }
                addListener0(listener);
            }
        }

        if (isDone()) {
            notifyListeners();
        }

        return this;
    }

    @Override
    public Promise<V> removeListener(final GenericFutureListener<? extends Future<? super V>> listener) {
        checkNotNull(listener, "listener");

        synchronized (this) {
            removeListener0(listener);
        }

        return this;
    }

    @Override
    public Promise<V> removeListeners(final GenericFutureListener<? extends Future<? super V>>... listeners) {
        checkNotNull(listeners, "listeners");

        // 同步 ..
        synchronized (this) {
            for (GenericFutureListener<? extends Future<? super V>> listener : listeners) {

                // 同理
                if (listener == null) {
                    break;
                }
                removeListener0(listener);
            }
        }

        return this;
    }

    @Override
    public Promise<V> await() throws InterruptedException {

        // await的详细实现
        // 如果已经完成了 ....
        if (isDone()) {

            // 直接返回自己 ..
            // 它自身实现了promise ..
            return this;
        }

        // 如果当前线程已经被打断了 ...
        // 抛出一个打断异常 ...
        // 它已经被打断了,不可能陷入沉睡 ..
        if (Thread.interrupted()) {
            throw new InterruptedException(toString());
        }

        // 检查死锁
        checkDeadLock();

        // 同步访问 ..
        synchronized (this) {
            // cas 自旋 ...
            // 这里使用cas 自选的原因防止,线程假醒,但是任务却没有完成的情况下,应该继续等待 ...
            // 对于一定要等待什么完成的情况下,while是最好的选择 。。
            while (!isDone()) {
                // 如果没有完成,增加等待者 ...
                incWaiters();
                try {
                    wait();
                } finally {
                    // 如果无意间醒了,那么减少等待着 ..
                    decWaiters();
                }
            }
        }

        // 如果完成了 ...
        // 返回自身 ...(这个promise 是线程安全的) ...
        return this;
    }

    @Override
    public Promise<V> awaitUninterruptibly() {
        // 等待不关心是否打断 ..
        // 这可能会导致无法正常关闭应用程序 ...
        if (isDone()) {
            return this;
        }

        checkDeadLock();

        boolean interrupted = false;

        synchronized (this) {
            while (!isDone()) {
                incWaiters();
                try {
                    wait();
                } catch (InterruptedException e) {
                    // Interrupted while waiting.
                    // 打断设置为 true ...
                    // 同样不理会 ...
                    interrupted = true;
                } finally {
                    decWaiters();
                }
            }
        }

        // 如果确实被打断了 ...
        // 设置当前线程的打断状态
        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        return this;
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        // 超时等待 ...
        return await0(unit.toNanos(timeout), true);
    }

    @Override
    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(MILLISECONDS.toNanos(timeoutMillis), true);
    }

    @Override
    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        try {
            return await0(unit.toNanos(timeout), false);
        } catch (InterruptedException e) {
            // Should not be raised at all.
            throw new InternalError();
        }
    }

    @Override
    public boolean awaitUninterruptibly(long timeoutMillis) {
        try {
            return await0(MILLISECONDS.toNanos(timeoutMillis), false);
        } catch (InterruptedException e) {
            // Should not be raised at all.
            throw new InternalError();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public V getNow() {
        Object result = this.result;
        if (result instanceof CauseHolder || result == SUCCESS || result == UNCANCELLABLE) {
            return null;
        }
        return (V) result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get() throws InterruptedException, ExecutionException {
        Object result = this.result;
        if (!isDone0(result)) {
            await();
            result = this.result;
        }
        if (result == SUCCESS || result == UNCANCELLABLE) {
            return null;
        }
        Throwable cause = cause0(result);
        if (cause == null) {
            return (V) result;
        }
        if (cause instanceof CancellationException) {
            throw (CancellationException) cause;
        }
        throw new ExecutionException(cause);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        Object result = this.result;
        if (!isDone0(result)) {
            if (!await(timeout, unit)) {
                throw new TimeoutException();
            }
            result = this.result;
        }
        if (result == SUCCESS || result == UNCANCELLABLE) {
            return null;
        }
        Throwable cause = cause0(result);
        if (cause == null) {
            return (V) result;
        }
        if (cause instanceof CancellationException) {
            throw (CancellationException) cause;
        }
        throw new ExecutionException(cause);
    }

    /**
     * {@inheritDoc}
     *
     * @param mayInterruptIfRunning this value has no effect in this implementation.
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (RESULT_UPDATER.compareAndSet(this, null, CANCELLATION_CAUSE_HOLDER)) {
            // 检查 通知等待者
            if (checkNotifyWaiters()) {
                notifyListeners();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled0(result);
    }

    @Override
    public boolean isDone() {
        return isDone0(result);
    }

    @Override
    public Promise<V> sync() throws InterruptedException {
        await();
        rethrowIfFailed();
        return this;
    }

    @Override
    public Promise<V> syncUninterruptibly() {
        awaitUninterruptibly();
        rethrowIfFailed();
        return this;
    }

    @Override
    public String toString() {
        return toStringBuilder().toString();
    }

    protected StringBuilder toStringBuilder() {
        StringBuilder buf = new StringBuilder(64)
                .append(StringUtil.simpleClassName(this))
                .append('@')
                .append(Integer.toHexString(hashCode()));

        Object result = this.result;
        if (result == SUCCESS) {
            buf.append("(success)");
        } else if (result == UNCANCELLABLE) {
            buf.append("(uncancellable)");
        } else if (result instanceof CauseHolder) {
            buf.append("(failure: ")
                    .append(((CauseHolder) result).cause)
                    .append(')');
        } else if (result != null) {
            buf.append("(success: ")
                    .append(result)
                    .append(')');
        } else {
            buf.append("(incomplete)");
        }

        return buf;
    }

    /**
     * Get the executor used to notify listeners when this promise is complete.
     * <p>
     * It is assumed this executor will protect against {@link StackOverflowError} exceptions.
     * The executor may be used to avoid {@link StackOverflowError} by executing a {@link Runnable} if the stack
     * depth exceeds a threshold.
     * @return The executor used to notify listeners when this promise is complete.
     */
    protected EventExecutor executor() {
        return executor;
    }

    protected void checkDeadLock() {
        EventExecutor e = executor();

        // 这个线程正在调度这个任务执行,你不能够在这个线程上等待任务完成 ...
        // 因为这会陷入死锁 ..
        if (e != null && e.inEventLoop()) {
            throw new BlockingOperationException(toString());
        }
    }

    /**
     * Notify a listener that a future has completed.
     * <p>
     * This method has a fixed depth of {@link #MAX_LISTENER_STACK_DEPTH} that will limit recursion to prevent
     * {@link StackOverflowError} and will stop notifying listeners added after this threshold is exceeded.
     * @param eventExecutor the executor to use to notify the listener {@code listener}.
     *
     * 通知一个监听器, future 已经完成了 ..
     * 这个方法有一个固定的深度(去限制递归 导致的堆栈溢出错误,并且将在这个阈值达到之后停止通知增加的监听器) ...
     * @param future the future that is complete.
     * @param listener the listener to notify.
     */
    protected static void notifyListener(
            EventExecutor eventExecutor, final Future<?> future, final GenericFutureListener<?> listener) {
        notifyListenerWithStackOverFlowProtection(
                checkNotNull(eventExecutor, "eventExecutor"),
                checkNotNull(future, "future"),
                checkNotNull(listener, "listener"));
    }

    // 通知监听器
    private void notifyListeners() {

        // 拿到执行器 ...
        EventExecutor executor = executor();

        if (executor.inEventLoop()) {
            // 如果在当前事件循环中 ...
            final InternalThreadLocalMap threadLocals = InternalThreadLocalMap.get();

            // 默认一开始创建的必然是空 ...
            final int stackDepth = threadLocals.futureListenerStackDepth();

            // 为什么需要这样做??
            // 能够在当前事件执行组完成,就尽量完成??  减少线程调度的压力 .. 但是也不会占用线程太长时间(也就是无限占用) ...
            // 这可能是一种优雅的方式吧(对于netty 开发者) ..
            // 但是也减少无限套娃 ...
            if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                // 设置 栈深度加1
                threadLocals.setFutureListenerStackDepth(stackDepth + 1);
                try {
                    // 立即通知监听器
                    notifyListenersNow();
                } finally {
                    threadLocals.setFutureListenerStackDepth(stackDepth);
                }
                return;
            }
        }

        // 也就是当前调用者的线程和这个promise的执行器不在同一个事件循环中,不能够直接触发 ...
        // 猜测,有可能是不在事件循环中,那么重新调度一个 ..
        // 并在新的事件循环中,尽可能的处理掉所有的事件回调 ...
        safeExecute(executor, new Runnable() {
            @Override
            public void run() {
                notifyListenersNow();
            }
        });
    }

    /**
     * The logic in this method should be identical to {@link #notifyListeners()} but
     * cannot share code because the listener(s) cannot be cached for an instance of {@link DefaultPromise}
     * since the
     * listener(s) may be changed and is protected by a synchronized operation.
     *
     * 这段方法中的逻辑等价于 #notifyListeners() 但是它不能够共享代码(因为监听器列表不能够缓存,因为
     * DefaultPromise的监听器可能会通过同步操作发生改变 ...
     *
     * 它的说法过于严谨了 .
     *
     * 也就是当天的事情当天做,不应该依靠其他方法 (例如 {@link #notifyListenersNow()} ..)
     */
    private static void notifyListenerWithStackOverFlowProtection(final EventExecutor executor,
                                                                  final Future<?> future,
                                                                  final GenericFutureListener<?> listener) {
        if (executor.inEventLoop()) {
            final InternalThreadLocalMap threadLocals = InternalThreadLocalMap.get();
            final int stackDepth = threadLocals.futureListenerStackDepth();
            if (stackDepth < MAX_LISTENER_STACK_DEPTH) {
                threadLocals.setFutureListenerStackDepth(stackDepth + 1);
                try {
                    notifyListener0(future, listener);
                } finally {
                    threadLocals.setFutureListenerStackDepth(stackDepth);
                }
                return;
            }
        }

        safeExecute(executor, new Runnable() {
            @Override
            public void run() {
                notifyListener0(future, listener);
            }
        });
    }

    private void notifyListenersNow() {
        Object listeners;


        synchronized (this) {
            // Only proceed if there are listeners to notify
            // and we are not already notifying listeners.
            // 如果有监听器正在处理 ..
            // 不存在正在通知的监听器
            if (notifyingListeners || this.listeners == null) {
                return;
            }
            notifyingListeners = true;
            listeners = this.listeners;
            this.listeners = null;
        }


        // 这种写法,确实很安全 ..
        // 在执行监听器的过程中,如果增加了新的监听器,则继续处理 ...
        for (;;) {
            if (listeners instanceof DefaultFutureListeners) {
                notifyListeners0((DefaultFutureListeners) listeners);
            } else {
                notifyListener0(this, (GenericFutureListener<?>) listeners);
            }

            // 客户端锁, listeners 必须排他性访问 ....
            synchronized (this) {

                // 如果监听器列表为空 ..
                if (this.listeners == null) {
                    // Nothing can throw from within this method,
                    // so setting notifyingListeners back to false does not
                    // need to be in a finally block.
                    // 也就是说,这里不会抛出异常,直接通过 设置而不是通过finally block ...
                    notifyingListeners = false;
                    return;
                }

                // 执行完毕之后,进行判断,有可能在执行过程中,存在新的监听器中注册 ..
                listeners = this.listeners;
                this.listeners = null;
            }
        }
    }

    private void notifyListeners0(DefaultFutureListeners listeners) {
        GenericFutureListener<?>[] a = listeners.listeners();
        int size = listeners.size();
        for (int i = 0; i < size; i ++) {
            notifyListener0(this, a[i]);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void notifyListener0(Future future, GenericFutureListener l) {
        try {
            // 直接调用 ...
            l.operationComplete(future);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("An exception was thrown by " + l.getClass().getName() + ".operationComplete()", t);
            }
        }
    }

    private void addListener0(GenericFutureListener<? extends Future<? super V>> listener) {
        if (listeners == null) {
            listeners = listener;
        } else if (listeners instanceof DefaultFutureListeners) {
            ((DefaultFutureListeners) listeners).add(listener);
        } else {
            listeners = new DefaultFutureListeners((GenericFutureListener<?>) listeners, listener);
        }
    }

    private void removeListener0(GenericFutureListener<? extends Future<? super V>> listener) {
        if (listeners instanceof DefaultFutureListeners) {
            ((DefaultFutureListeners) listeners).remove(listener);
        } else if (listeners == listener) {
            listeners = null;
        }
    }

    private boolean setSuccess0(V result) {
        // 设置结果 ..
        return setValue0(result == null ? SUCCESS : result);
    }

    // 设置Failure 和 success 类似 ..
    private boolean setFailure0(Throwable cause) {
        return setValue0(new CauseHolder(checkNotNull(cause, "cause")));
    }

    private boolean setValue0(Object objResult) {
        // 如果本身等于 null /  或者还不是取消状态 ..
        if (RESULT_UPDATER.compareAndSet(this, null, objResult) ||
            RESULT_UPDATER.compareAndSet(this, UNCANCELLABLE, objResult)) {

            // 检查并通知等待线程 ...
            if (checkNotifyWaiters()) {

                // 尽可能通知所有的监听器 ..
                notifyListeners();
            }
            return true;
        }
        return false;
    }

    /**
     * Check if there are any waiters and if so notify these.
     * @return {@code true} if there are any listeners attached to the promise, {@code false} otherwise.
     */
    private synchronized boolean checkNotifyWaiters() {
        // 表示存在等待者 ...
        if (waiters > 0) {
            // 全部唤醒 ...
            notifyAll();
        }
        return listeners != null;
    }

    private void incWaiters() {
        if (waiters == Short.MAX_VALUE) {
            throw new IllegalStateException("too many waiters: " + this);
        }
        ++waiters;
    }

    private void decWaiters() {
        --waiters;
    }

    private void rethrowIfFailed() {
        Throwable cause = cause();
        if (cause == null) {
            return;
        }

        // 如果异常存在
        PlatformDependent.throwException(cause);
    }

    private boolean await0(long timeoutNanos, boolean interruptable) throws InterruptedException {
        // 同样的操作 ..
        if (isDone()) {
            return true;
        }

        // 如果等待时间小于 0,直接判断是否完成 ...
        if (timeoutNanos <= 0) {
            return isDone();
        }

        // 如果可打断,并且当前线程已经打断 ... 抛出异常 ...
        // 我认为最安全的方式,从这个方法进入一开始,直接进行 try -finally  ..
        //如果发生了了打断,根据状态进行 打断异常处理 ..
        // 然后后续动作,直接进行while(循环) .. .从而保障线程的打断状态能够无缝控制 ...

        // 但是,线程的打断状态并不会影响线程的执行
        // 我们可以在执行过程中的任何打断进行处理 ...
        // 如果我是初学者,可能我会向上述我自己思考的那样的形式进行处理 ...
        // ThreadTests#main
        if (interruptable && Thread.interrupted()) {
            throw new InterruptedException(toString());
        }

        checkDeadLock();


        // 如果进入了这个阶段,
        //可以也需要不断的检测是否打断
        // Start counting time from here instead of the first line of this method,
        // to avoid/postpone performance cost of System.nanoTime().
        // 从这里开始算持续时间而不是从这个方法的第一行开始 ..
        //避免 / 延缓 System.nanoTime()的性能消耗 ...
        final long startTime = System.nanoTime();
        synchronized (this) {
            boolean interrupted = false;
            try {
                long waitTime = timeoutNanos;
                while (!isDone() && waitTime > 0) {
                    // 增加等待者
                    incWaiters();
                    try {
                        // 等待 ...
                        wait(waitTime / 1000000, (int) (waitTime % 1000000));
                    } catch (InterruptedException e) {

                        // 如果被打断了,判断是否可以打断,是则抛出异常 ...
                        if (interruptable) {
                            throw e;
                        } else {
                            interrupted = true;
                        }
                    } finally {
                        // 然后减少等待着... 尝试继续等待 ..
                        decWaiters();
                    }
                    // Check isDone() in advance, try to avoid calculating the elapsed time later.
                    // 提前检查 是否完成,避免在之后计算逝去的时间
                    if (isDone()) {
                        return true;
                    }
                    // Calculate the elapsed time here instead of in the while condition,
                    // try to avoid performance cost of System.nanoTime() in the first loop of while.
                    // 计算逝去的时间取代while 条件 ..
                    // 尽量避免在while的第一个循环中的 System.nanoTime()的性能成本 。。
                    // 真到这里来了,就只能够处理 ...
                    waitTime = timeoutNanos - (System.nanoTime() - startTime);
                }

                // 最后等待时间完成,尝试通过isDone 返回 ..
                return isDone();
            } finally {
                // 最后,如果它被打断了 ..
                // 设置线程打断状态 ...
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Notify all progressive listeners.
     * 通知所有 渐进式监听器 ???
     * <p>
     * No attempt is made to ensure notification order if multiple calls are made to this method before
     * the original invocation completes.
     * <p>
     * 不会尝试确保通知顺序(如果多次调用此方法(在原始调用执行完成之前)) ...
     * This will do an iteration over all listeners to get all of type {@link GenericProgressiveFutureListener}s.
     * 这将在所有的监听器上进行迭代(获取所有 GenericProgressiveFutureListener 类型的监听器)
     * @param progress the new progress.
     * @param total the total progress.
     */
    @SuppressWarnings("unchecked")
    void notifyProgressiveListeners(final long progress, final long total) {
        // 获取出监听器 ..
        final Object listeners = progressiveListeners();
        if (listeners == null) {
            return;
        }

        final ProgressiveFuture<V> self = (ProgressiveFuture<V>) this;

        EventExecutor executor = executor();

        // 如果在事件循环中 ...
        // 那么问题是,为什么一定要在事件循环内 执行监听器事件 ...
        // 我的理解是保证线程安全 ...

        if (executor.inEventLoop()) {
            if (listeners instanceof GenericProgressiveFutureListener[]) {
                notifyProgressiveListeners0(
                        self, (GenericProgressiveFutureListener<?>[]) listeners, progress, total);
            } else {
                notifyProgressiveListener0(
                        self, (GenericProgressiveFutureListener<ProgressiveFuture<V>>) listeners, progress, total);
            }
        } else {
            if (listeners instanceof GenericProgressiveFutureListener[]) {
                final GenericProgressiveFutureListener<?>[] array =
                        (GenericProgressiveFutureListener<?>[]) listeners;
                safeExecute(executor, new Runnable() {
                    @Override
                    public void run() {
                        notifyProgressiveListeners0(self, array, progress, total);
                    }
                });
            } else {
                final GenericProgressiveFutureListener<ProgressiveFuture<V>> l =
                        (GenericProgressiveFutureListener<ProgressiveFuture<V>>) listeners;
                // 安全执行 ...
                safeExecute(executor, new Runnable() {
                    @Override
                    public void run() {
                        notifyProgressiveListener0(self, l, progress, total);
                    }
                });
            }
        }
    }

    /**
     * Returns a {@link GenericProgressiveFutureListener}, an array of {@link GenericProgressiveFutureListener}, or
     * {@code null}.
     *
     * 返回所有渐进式 FutureListener ...
     * 一个GenericProgressiveFutureListener 或者一个列表 / null
     *
     * 排他性访问 。。。
     */
    private synchronized Object progressiveListeners() {
        Object listeners = this.listeners;
        if (listeners == null) {
            // No listeners added
            return null;
        }

        // 如果是 DefaultFuture ...Listener ....
        if (listeners instanceof DefaultFutureListeners) {
            // Copy DefaultFutureListeners into an array of listeners.
            DefaultFutureListeners dfl = (DefaultFutureListeners) listeners;
            // 获取 progressiveSize
            int progressiveSize = dfl.progressiveSize();

            // 如果为 0
            switch (progressiveSize) {
                // 表示没有 ...
                case 0:
                    return null;
                // 1 表示
                case 1:
                    for (GenericFutureListener<?> l: dfl.listeners()) {
                        // 直接返回 ...
                        if (l instanceof GenericProgressiveFutureListener) {
                            return l;
                        }
                    }
                    return null;
            }

            // 列表
            GenericFutureListener<?>[] array = dfl.listeners();
            GenericProgressiveFutureListener<?>[] copy = new GenericProgressiveFutureListener[progressiveSize];
            // 遍历
            for (int i = 0, j = 0; j < progressiveSize; i ++) {
                GenericFutureListener<?> l = array[i];
                // 仅仅保留渐进式 FutureListener ...
                if (l instanceof GenericProgressiveFutureListener) {
                    copy[j ++] = (GenericProgressiveFutureListener<?>) l;
                }
            }

            return copy;
        } else if (listeners instanceof GenericProgressiveFutureListener) {
            // 仅仅有一个
            return listeners;
        } else {
            // Only one listener was added and it's not a progressive listener.
            // 仅仅增加了 一个非 GenericProgressiveFutureListener 的监听器 ...
            return null;
        }
    }

    private static void notifyProgressiveListeners0(
            ProgressiveFuture<?> future, GenericProgressiveFutureListener<?>[] listeners, long progress, long total) {
        for (GenericProgressiveFutureListener<?> l: listeners) {
            // 如果有一个为null,则直接退出 ...
            if (l == null) {
                break;
            }
            notifyProgressiveListener0(future, l, progress, total);
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void notifyProgressiveListener0(
            ProgressiveFuture future, GenericProgressiveFutureListener l, long progress, long total) {
        try {
            l.operationProgressed(future, progress, total);
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("An exception was thrown by " + l.getClass().getName() + ".operationProgressed()", t);
            }
        }
    }

    // 如果结果为Cancel 异常 ..
    private static boolean isCancelled0(Object result) {
        return result instanceof CauseHolder && ((CauseHolder) result).cause instanceof CancellationException;
    }

    // 完成的状态就是结果不为空, 也不等于UNCANCELLABLE ..
    // 也就是说 结束了 result 就不可能为空,(要么成功 / 要么失败 / 要么取消了的) ..
    // 肯定不是UNCANCELLABLE 状态 ...
    private static boolean isDone0(Object result) {
        return result != null && result != UNCANCELLABLE;
    }

    private static final class CauseHolder {
        final Throwable cause;
        CauseHolder(Throwable cause) {
            this.cause = cause;
        }
    }

    private static void safeExecute(EventExecutor executor, Runnable task) {
        try {
            executor.execute(task);
        } catch (Throwable t) {
            rejectedExecutionLogger.error("Failed to submit a listener notification task. Event loop shut down?", t);
        }
    }

    /**
     * 无堆栈 取消异常
     */
    private static final class StacklessCancellationException extends CancellationException {

        private static final long serialVersionUID = -2974906711413716191L;

        private StacklessCancellationException() { }

        // Override fillInStackTrace() so we not populate the backtrace via a native call and so leak the
        // Classloader.
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }

        static StacklessCancellationException newInstance(Class<?> clazz, String method) {
            return ThrowableUtil.unknownStackTrace(new StacklessCancellationException(), clazz, method);
        }
    }
}
