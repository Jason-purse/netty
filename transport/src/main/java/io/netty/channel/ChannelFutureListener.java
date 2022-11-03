/*
 * Copyright 2012 The Netty Project
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
package io.netty.channel;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;


/**
 * Listens to the result of a {@link ChannelFuture}.  The result of the
 * asynchronous {@link Channel} I/O operation is notified once this listener
 * is added by calling {@link ChannelFuture#addListener(GenericFutureListener)}.
 *
 * 监听 ChannelFuture的结果 ,  一旦调用了ChannelFuture#addListener(...GenericFutureListener) 之后
 * 异步IO 操作的结果将会通知给监听器 ...
 *
 * 快速的返回控制给调用者 ..
 * #operationComplete(Future) 由 I/O 线程直接调用 ..
 * 因此在handler方法中执行一个耗时任务或者阻塞操作可能导致在IO 期间意外的暂停. ..
 * 如果你需要在I/O 完成之后执行一个阻塞操作,尝试使用线程池在单独的线程中执行这个操作 ...
 *
 * <h3>Return the control to the caller quickly</h3>
 *
 * {@link #operationComplete(Future)} is directly called by an I/O
 * thread.  Therefore, performing a time consuming task or a blocking operation
 * in the handler method can cause an unexpected pause during I/O.  If you need
 * to perform a blocking operation on I/O completion, try to execute the
 * operation in a different thread using a thread pool.
 */
public interface ChannelFutureListener extends GenericFutureListener<ChannelFuture> {

    /**
     * A {@link ChannelFutureListener} that closes the {@link Channel} which is
     * associated with the specified {@link ChannelFuture}.
     */
    ChannelFutureListener CLOSE = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) {
            future.channel().close();
        }
    };

    /**
     * A {@link ChannelFutureListener} that closes the {@link Channel} when the
     * operation ended up with a failure or cancellation rather than a success.
     */
    ChannelFutureListener CLOSE_ON_FAILURE = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) {
            if (!future.isSuccess()) {
                future.channel().close();
            }
        }
    };

    /**
     * A {@link ChannelFutureListener} that forwards the {@link Throwable} of the {@link ChannelFuture} into the
     * {@link ChannelPipeline}. This mimics the old behavior of Netty 3.
     */
    ChannelFutureListener FIRE_EXCEPTION_ON_FAILURE = new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) {
            if (!future.isSuccess()) {
                future.channel().pipeline().fireExceptionCaught(future.cause());
            }
        }
    };

    // Just a type alias
}
