/*
 * Copyright 2016 The Netty Project
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

import io.netty.util.internal.ObjectUtil;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Expose helper methods which create different {@link RejectedExecutionHandler}s.
 */
public final class RejectedExecutionHandlers {
    private static final RejectedExecutionHandler REJECT = new RejectedExecutionHandler() {
        @Override
        public void rejected(Runnable task, SingleThreadEventExecutor executor) {
            throw new RejectedExecutionException();
        }
    };

    private RejectedExecutionHandlers() { }

    /**
     * Returns a {@link RejectedExecutionHandler} that will always just throw a {@link RejectedExecutionException}.
     */
    public static RejectedExecutionHandler reject() {
        return REJECT;
    }

    /**
     * Tries to backoff when the task can not be added due restrictions for an configured amount of time. This
     * is only done if the task was added from outside of the event loop which means
     * {@link EventExecutor#inEventLoop()} returns {@code false}.
     *
     *
     * 在配置的时间内由于限制而无法添加任务时尝试回退
     * 如果任务从事件循环之外增加,这意味着 EventExecutor#inEventLoop() 返回 false ... (这种情况下才会执行) ..
     */
    public static RejectedExecutionHandler backoff(final int retries, long backoffAmount, TimeUnit unit) {
        ObjectUtil.checkPositive(retries, "retries");
        final long backOffNanos = unit.toNanos(backoffAmount);
        return new RejectedExecutionHandler() {
            @Override
            public void rejected(Runnable task, SingleThreadEventExecutor executor) {
                if (!executor.inEventLoop()) {
                    for (int i = 0; i < retries; i++) {
                        // Try to wake up the executor so it will empty its task queue.
                        // 唤醒执行器, 让它清空任务队列
                        executor.wakeup(false);

                        // 可能队列中的东西已经满了, 等待一段时间,尝试加入 ..
                        // 线程休眠 ..
                        LockSupport.parkNanos(backOffNanos);

                        // 如果添加成功,就返回,否则就尝试 ..
                        // 最后用了补偿也无法加入,则抛出异常 ....
                        if (executor.offerTask(task)) {
                            return;
                        }
                    }
                }
                // Either we tried to add the task from within the EventLoop or we was not able to add it even with
                // backoff.
                throw new RejectedExecutionException();
            }
        };
    }
}
