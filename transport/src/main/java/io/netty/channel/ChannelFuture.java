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

import io.netty.bootstrap.Bootstrap;
import io.netty.util.concurrent.BlockingOperationException;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.concurrent.TimeUnit;


/**
 * Channel Future 表示一个异步Channel I/O 操作 ...
 *
 * The result of an asynchronous {@link Channel} I/O operation.
 * <p>
 * All I/O operations in Netty are asynchronous.  It means any I/O calls will
 * return immediately with no guarantee that the requested I/O operation has
 * been completed at the end of the call.  Instead, you will be returned with
 * a {@link ChannelFuture} instance which gives you the information about the
 * result or status of the I/O operation.
 * <p>
 * 在Netty中所有的I/O 操作都是异步的, 这意味着任何的I/O 调用将立即返回而不会保证请求的I/O 操作在调用结束之后立即完成 ..
 * 相反,将会返回一个ChannelFuture 示例(这将会给你有关I/O 操作状态的结果或者状态) ...
 *
 *
 *
 * A {@link ChannelFuture} is either <em>uncompleted</em> or <em>completed</em>.
 * When an I/O operation begins, a new future object is created.  The new future
 * is uncompleted initially - it is neither succeeded, failed, nor cancelled
 * because the I/O operation is not finished yet.  If the I/O operation is
 * finished either successfully, with failure, or by cancellation, the future is
 * marked as completed with more specific information, such as the cause of the
 * failure.  Please note that even failure and cancellation belong to the
 * completed state.
 *
 * 一个ChannelFuture 要么是一个未完成或者完成的状态 ..
 * 当一个I/O 操作开始时,一个新的future对象将会被创建..
 * 新的future 刚开始是未完成的 .. 它既不是成功/失败或者取消(因为I/O 操作可能是未完成的) ...
 * 如果I/O 操作要么成功 / 失败或者取消所完成,那么这个future 将会标记为完成的(并包含更多特定的信息,例如 失败的原因) ..
 * 请注意 甚至失败或者取消都属于完成状态 ..
 *
 * <pre>
 *                                      +---------------------------+
 *                                      | Completed successfully    |
 *                                      +---------------------------+
 *                                 +---->      isDone() = true      |
 * +--------------------------+    |    |   isSuccess() = true      |
 * |        Uncompleted       |    |    +===========================+
 * +--------------------------+    |    | Completed with failure    |
 * |      isDone() = false    |    |    +---------------------------+
 * |   isSuccess() = false    |----+---->      isDone() = true      |
 * | isCancelled() = false    |    |    |       cause() = non-null  |
 * |       cause() = null     |    |    +===========================+
 * +--------------------------+    |    | Completed by cancellation |
 *                                 |    +---------------------------+
 *                                 +---->      isDone() = true      |
 *                                      | isCancelled() = true      |
 *                                      +---------------------------+
 * </pre>
 *
 * 上述图表明了,未完成状态, 完成是false,成功是false, 取消是false,原因是null ..
 * 反之包含了三种成功状态 ...
 *
 * Various methods are provided to let you check if the I/O operation has been
 * completed, wait for the completion, and retrieve the result of the I/O
 * operation. It also allows you to add {@link ChannelFutureListener}s so you
 * can get notified when the I/O operation is completed.
 *
 * 包含了各种方法用来检测I/O 操作是否已经完成了 ... 等待完成并抓取I/O 操作的结果 ..
 * 它也总是允许你增加ChannelFutureListener 能够在I/O 操作完成时得到通知 ...
 *
 * <h3>Prefer {@link #addListener(GenericFutureListener)} to {@link #await()}</h3>
 * 更偏好于选择 addListener.... 而不是await ...
 *
 * It is recommended to prefer {@link #addListener(GenericFutureListener)} to
 * {@link #await()} wherever possible to get notified when an I/O operation is
 * done and to do any follow-up tasks.
 *
 * 通过addListener的形式 形式更加友好 ...  在I/O 操作完成之后还可以继续做后续的任务 ..
 * <p>
 * {@link #addListener(GenericFutureListener)} is non-blocking.  It simply adds
 * the specified {@link ChannelFutureListener} to the {@link ChannelFuture}, and
 * I/O thread will notify the listeners when the I/O operation associated with
 * the future is done.  {@link ChannelFutureListener} yields the best
 * performance and resource utilization because it does not block at all, but
 * it could be tricky to implement a sequential logic if you are not used to
 * event-driven programming.
 * <p>
 * addListener... 是非阻塞的 .. 它简单的增加特定给的ChannelFutureListener 到ChannelFuture ..
 * 并且I/O 线程将通知监听器(一旦I/O 操作已经完成) ...
 * ChannelFutureListener 保留了最佳性能 .. 以及资源利用(因为它已知都是非阻塞的,但是也能够 灵活的
 * 去实现一些连续性逻辑,如果你不打算进行事件驱动编程) ..
 *
 * By contrast, {@link #await()} is a blocking operation.  Once called, the
 * caller thread blocks until the operation is done.  It is easier to implement
 * a sequential logic with {@link #await()}, but the caller thread blocks
 * unnecessarily until the I/O operation is done and there's relatively
 * expensive cost of inter-thread notification.  Moreover, there's a chance of
 * dead lock in a particular circumstance, which is described below.
 *
 * 对比之下,await 是一个阻塞操作 .. 一旦调用,那么调用者线程阻塞直到操作完成 ..
 * 更容易通过await()实现一个连续性逻辑,但是调用者线程会不必要的阻塞直到I/O 操作完成并且在线程之间的通知还有一些额外的昂贵的消耗..
 * 更有甚者,在特定环境下的造成死锁的一个机会,这将会在下面进行阐述 ...
 *
 * <h3>Do not call {@link #await()} inside {@link ChannelHandler}</h3>
 * 不要再ChannelHandler中调用await ...
 * <p>
 * The event handler methods in {@link ChannelHandler} are usually called by
 * an I/O thread.  If {@link #await()} is called by an event handler
 * method, which is called by the I/O thread, the I/O operation it is waiting
 * for might never complete because {@link #await()} can block the I/O
 * operation it is waiting for, which is a dead lock.
 * <pre>
 *  在ChannelHandler中的事件处理器方法通常是由I/O 线程进行调用 ..
 *  如果await方法被一个事件处理器方法调用,而await是由I/O 线程调用,那么这个I/O 操作将会等待并且可能不可能完成(因为await可能会阻塞了它等待的I/O 操作,这导致了死锁) ..
 *
 *  也就是(对于netty来说, 每一个线程仅仅会暂用线程的一部分生命时间,那么有可能一个线程执行多个任务,那么可能完蛋) ...
 *
 * // 以下是一个错误示例 ....
 * // BAD - NEVER DO THIS
 * {@code @Override}
 * public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     {@link ChannelFuture} future = ctx.channel().close();
 *     future.awaitUninterruptibly();
 *     // Perform post-closure operation
 *     // ...
 * }
 *
 * // GOOD
 * {@code @Override}
 * public void channelRead({@link ChannelHandlerContext} ctx, Object msg) {
 *     {@link ChannelFuture} future = ctx.channel().close();
 *     future.addListener(new {@link ChannelFutureListener}() {
 *         public void operationComplete({@link ChannelFuture} future) {
 *             // Perform post-closure operation
 *             // ...
 *         }
 *     });
 * }
 * </pre>
 * <p>
 *
 * 尽管上面提及了缺点,但是有一些这样的情况更适合调用await ...
 * 例如确保你没有在I/O 线程中调用此方法 ...
 * 否则将会抛出将会抛出一个BlockingOperationException 阻止死锁 ..
 * In spite of the disadvantages mentioned above, there are certainly the cases
 * where it is more convenient to call {@link #await()}. In such a case, please
 * make sure you do not call {@link #await()} in an I/O thread.  Otherwise,
 * {@link BlockingOperationException} will be raised to prevent a dead lock.
 *
 *
 *
 * I/O 超时和 等待超时不需要困惑 ..
 * <h3>Do not confuse I/O timeout and await timeout</h3>
 *
 * The timeout value you specify with {@link #await(long)},
 * {@link #await(long, TimeUnit)}, {@link #awaitUninterruptibly(long)}, or
 * {@link #awaitUninterruptibly(long, TimeUnit)} are not related with I/O
 * timeout at all.  If an I/O operation times out, the future will be marked as
 * 'completed with failure,' as depicted in the diagram above.  For example,
 * connect timeout should be configured via a transport-specific option:
 * 通过await可以指定超时值 ...  它们和I/O 超时是不相关的 ..
 * 如果一个I/O 操作超时, 那么future将会标记为完成的失败状态 (正如上面的表所描述的那样) ...
 * 例如连接超时应该通过特定的传输选项进行配置 ...
 * <pre>
 *
 *     // 为什么不能这样做, 因为这里的等待超时不一定大于I/O 超时时间,那么可能 future 还没有完成 ... 这样的结果是不正确的 ...
 * // BAD - NEVER DO THIS
 * {@link Bootstrap} b = ...;
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly(10, TimeUnit.SECONDS);
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     // You might get a NullPointerException here because the future
 *     // might not be completed yet.
 *     f.cause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 *
 * // GOOD
 * {@link Bootstrap} b = ...;
 * // Configure the connect timeout option.
 * <b>b.option({@link ChannelOption}.CONNECT_TIMEOUT_MILLIS, 10000);</b>
 * {@link ChannelFuture} f = b.connect(...);
 * f.awaitUninterruptibly();
 *
 * // Now we are sure the future is completed.
 * assert f.isDone();
 *
 * if (f.isCancelled()) {
 *     // Connection attempt cancelled by user
 * } else if (!f.isSuccess()) {
 *     f.cause().printStackTrace();
 * } else {
 *     // Connection established successfully
 * }
 * </pre>
 */
public interface ChannelFuture extends Future<Void> {

    /**
     * Returns a channel where the I/O operation associated with this
     * future takes place.
     */
    Channel channel();

    @Override
    ChannelFuture addListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture addListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture removeListener(GenericFutureListener<? extends Future<? super Void>> listener);

    @Override
    ChannelFuture removeListeners(GenericFutureListener<? extends Future<? super Void>>... listeners);

    @Override
    ChannelFuture sync() throws InterruptedException;

    @Override
    ChannelFuture syncUninterruptibly();

    @Override
    ChannelFuture await() throws InterruptedException;

    @Override
    ChannelFuture awaitUninterruptibly();

    /**
     * Returns {@code true} if this {@link ChannelFuture} is a void future and so not allow to call any of the
     * following methods:
     * <ul>
     *     <li>{@link #addListener(GenericFutureListener)}</li>
     *     <li>{@link #addListeners(GenericFutureListener[])}</li>
     *     <li>{@link #await()}</li>
     *     <li>{@link #await(long, TimeUnit)} ()}</li>
     *     <li>{@link #await(long)} ()}</li>
     *     <li>{@link #awaitUninterruptibly()}</li>
     *     <li>{@link #sync()}</li>
     *     <li>{@link #syncUninterruptibly()}</li>
     * </ul>
     *
     * 返回true,表示这个ChannelFuture 是一个void future,它不允许调用任何以下的方法之一 ...
     */
    boolean isVoid();
}
