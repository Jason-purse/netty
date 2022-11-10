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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.UnstableApi;

import static io.netty.util.internal.ObjectUtil.checkNotNull;

/**
 * Allocates a new receive buffer whose capacity is probably large enough to read all inbound data and small enough
 * not to waste its space.
 *
 *分配一个尽可能容量足够大去读取所有进入的数据并且足够小且不会浪费空间的接受buffer ...的分配器 ...
 */
public interface RecvByteBufAllocator {
    /**
     * Creates a new handle.  The handle provides the actual operations and keeps the internal information which is
     * required for predicting an optimal buffer capacity.
     */
    Handle newHandle();

    /**
     * @deprecated Use {@link ExtendedHandle}.
     */
    @Deprecated
    interface Handle {
        /**
         * Creates a new receive buffer whose capacity is probably large enough to read all inbound data and small
         * enough not to waste its space.
         */
        ByteBuf allocate(ByteBufAllocator alloc);

        /**
         * Similar to {@link #allocate(ByteBufAllocator)} except that it does not allocate anything but just tells the
         * capacity.
         */
        int guess();

        /**
         * Reset any counters that have accumulated and recommend how many messages/bytes should be read for the next
         * read loop.
         *
         * 重置任何计数器(进行估算并且推荐究竟有多少消息 /字节 应该在下一次read loop中读取)
         * <p>
         * This may be used by {@link #continueReading()} to determine if the read operation should complete.
         * </p>
         * This is only ever a hint and may be ignored by the implementation.
         * @param config The channel configuration which may impact this object's behavior. // 这个管道配置也许能够影响 这个对象的行为 ...
         *
         * 如果读取操作应该完成 ... 这可能被 continueReading方法使用决定 ..
         *  这仅仅只是一种提示并且也许可能被实现忽略 ..
         *
         */
        void reset(ChannelConfig config);

        /**
         * Increment the number of messages that have been read for the current read loop.
         * 增加消息的数量(对于当前读取循环中所已经读取的) ...
         * @param numMessages The amount to increment by. 增加的数量
         */
        void incMessagesRead(int numMessages);

        /**
         * Set the bytes that have been read for the last read operation.
         * This may be used to increment the number of bytes that have been read.
         * @param bytes The number of bytes from the previous read operation. This may be negative if an read error
         * occurs. If a negative value is seen it is expected to be return on the next call to
         * {@link #lastBytesRead()}. A negative value will signal a termination condition enforced externally
         * to this class and is not required to be enforced in {@link #continueReading()}.
         *
         *  设置上一次读取操作已经读取了多少字节 ..
         *  这用来增加已经有多少字节被读取了 ...
         * @param bytes ... 从前面读取操作读取的字节数 .. 这也许是一个负数(如果发生了读取异常),
         *              如果是一个负数( 那么下一次调用lastBytesRead()将会返回期待的值 负数),
         *              一个负数将会触发一个中断条件(强制外力为这个类标识) 并且不需要强制在 #continueReading()中执行 ..
         */
        void lastBytesRead(int bytes);

        /**
         * Get the amount of bytes for the previous read operation.
         * @return The amount of bytes for the previous read operation.
         *
         * 返回之前读取操作的字节数量
         */
        int lastBytesRead();

        /**
         * Set how many bytes the read operation will (or did) attempt to read.
         * @param bytes How many bytes the read operation will (or did) attempt to read.
         *
         *  设置此次读操作将尝试或者真的读取的字节数量 ...
         */
        void attemptedBytesRead(int bytes);

        /**
         * Get how many bytes the read operation will (or did) attempt to read.
         * @return How many bytes the read operation will (or did) attempt to read.
         */
        int attemptedBytesRead();

        /**
         * Determine if the current read loop should continue.
         * 决定当前读取循环是否继续 ...
         * 如果true,表示继续,否则表示读取循环完成 ..
         * @return {@code true} if the read loop should continue reading. {@code false} if the read loop is complete.
         */
        boolean continueReading();

        /**
         * The read has completed.
         */
        void readComplete();
    }

    @SuppressWarnings("deprecation")
    @UnstableApi
    interface ExtendedHandle extends Handle {
        /**
         * Same as {@link Handle#continueReading()} except "more data" is determined by the supplier parameter.
         * @param maybeMoreDataSupplier A supplier that determines if there maybe more data to read.
         */
        boolean continueReading(UncheckedBooleanSupplier maybeMoreDataSupplier);
    }

    /**
     * A {@link Handle} which delegates all call to some other {@link Handle}.
     */
    class DelegatingHandle implements Handle {
        private final Handle delegate;

        public DelegatingHandle(Handle delegate) {
            this.delegate = checkNotNull(delegate, "delegate");
        }

        /**
         * Get the {@link Handle} which all methods will be delegated to.
         * @return the {@link Handle} which all methods will be delegated to.
         */
        protected final Handle delegate() {
            return delegate;
        }

        @Override
        public ByteBuf allocate(ByteBufAllocator alloc) {
            return delegate.allocate(alloc);
        }

        @Override
        public int guess() {
            return delegate.guess();
        }

        @Override
        public void reset(ChannelConfig config) {
            delegate.reset(config);
        }

        @Override
        public void incMessagesRead(int numMessages) {
            delegate.incMessagesRead(numMessages);
        }

        @Override
        public void lastBytesRead(int bytes) {
            delegate.lastBytesRead(bytes);
        }

        @Override
        public int lastBytesRead() {
            return delegate.lastBytesRead();
        }

        @Override
        public boolean continueReading() {
            return delegate.continueReading();
        }

        @Override
        public int attemptedBytesRead() {
            return delegate.attemptedBytesRead();
        }

        @Override
        public void attemptedBytesRead(int bytes) {
            delegate.attemptedBytesRead(bytes);
        }

        @Override
        public void readComplete() {
            delegate.readComplete();
        }
    }
}
