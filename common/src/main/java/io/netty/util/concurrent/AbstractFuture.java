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

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Abstract {@link Future} implementation which does not allow for cancellation.
 *
 * 抽象的Future 实现(它不允许取消) ....
 *
 * @param <V>
 */
public abstract class AbstractFuture<V> implements Future<V> {

    @Override
    public V get() throws InterruptedException, ExecutionException {
        await();

        // 等待完成之后,查看原因 ...
        Throwable cause = cause();
        if (cause == null) {
            // 如果为空,表示成功结束 ..
            return getNow();
        }
        // 否则 如果是取消异常 ... 抛出取消异常
        if (cause instanceof CancellationException) {
            throw (CancellationException) cause;
        }
        // 如果是 其他异常,则是一个执行异常
        throw new ExecutionException(cause);
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {

        // 等待一定的时间 ..
        if (await(timeout, unit)) {
            // 如果cause 等于 空,则表示成功完成 ..
            Throwable cause = cause();
            if (cause == null) {
                return getNow();
            }
            if (cause instanceof CancellationException) {
                throw (CancellationException) cause;
            }
            throw new ExecutionException(cause);
        }

        // 否则超时异常 ..
        throw new TimeoutException();
    }
}
