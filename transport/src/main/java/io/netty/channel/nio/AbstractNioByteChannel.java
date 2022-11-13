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
package io.netty.channel.nio;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.FileRegion;
import io.netty.channel.RecvByteBufAllocator;
import io.netty.channel.internal.ChannelUtils;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

import static io.netty.channel.internal.ChannelUtils.WRITE_STATUS_SNDBUF_FULL;

/**
 * {@link AbstractNioChannel} base class for {@link Channel}s that operate on bytes.
 *
 * 这是一个Channel 操作字节的 AbstractNioChannel的基类 ...
 */
public abstract class AbstractNioByteChannel extends AbstractNioChannel {
    private static final ChannelMetadata METADATA = new ChannelMetadata(false, 16);
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(ByteBuf.class) + ", " +
            StringUtil.simpleClassName(FileRegion.class) + ')';

    private final Runnable flushTask = new Runnable() {
        @Override
        public void run() {
            // Calling flush0 directly to ensure we not try to flush messages that were added via write(...) in the
            // meantime.  直接调用flush0(避免刷新在调度期间通过write(..)加入的消息) ...
            ((AbstractNioUnsafe) unsafe()).flush0();
        }
    };

    // 在读取的时候由于错误输入关闭
    private boolean inputClosedSeenErrorOnRead;

    /**
     * Create a new instance
     *
     * @param parent            the parent {@link Channel} by which this instance was created. May be {@code null}
     * @param ch                the underlying {@link SelectableChannel} on which it operates
     */
    protected AbstractNioByteChannel(Channel parent, SelectableChannel ch) {
        super(parent, ch, SelectionKey.OP_READ);
    }

    /**
     * Shutdown the input side of the channel.
     */
    protected abstract ChannelFuture shutdownInput();

    protected boolean isInputShutdown0() {
        return false;
    }

    @Override
    protected AbstractNioUnsafe newUnsafe() {
        return new NioByteUnsafe();
    }

    @Override
    public ChannelMetadata metadata() {
        return METADATA;
    }

    final boolean shouldBreakReadReady(ChannelConfig config) {
        // 输入如果关闭了 并且 (读取时发生了错误 或者 非半包) 则可以打断读 ready
        return isInputShutdown0() && (inputClosedSeenErrorOnRead || !isAllowHalfClosure(config));
    }

    // 允许半包 ??
    private static boolean isAllowHalfClosure(ChannelConfig config) {
        // socket channel 半包配置 ...
        return config instanceof SocketChannelConfig &&
                ((SocketChannelConfig) config).isAllowHalfClosure();
    }

    /**
     * 真正来自 ByteBuf的 Unsafe
     */
    protected class NioByteUnsafe extends AbstractNioUnsafe {

        private void closeOnRead(ChannelPipeline pipeline) {
            if (!isInputShutdown0()) {
                if (isAllowHalfClosure(config())) {
                    shutdownInput();
                    pipeline.fireUserEventTriggered(ChannelInputShutdownEvent.INSTANCE);
                } else {
                    close(voidPromise());
                }
            } else {
                inputClosedSeenErrorOnRead = true;
                pipeline.fireUserEventTriggered(ChannelInputShutdownReadComplete.INSTANCE);
            }
        }

        private void handleReadException(ChannelPipeline pipeline, ByteBuf byteBuf, Throwable cause, boolean close,
                RecvByteBufAllocator.Handle allocHandle) {
            if (byteBuf != null) {
                if (byteBuf.isReadable()) {
                    readPending = false;
                    pipeline.fireChannelRead(byteBuf);
                } else {
                    byteBuf.release();
                }
            }
            allocHandle.readComplete();
            pipeline.fireChannelReadComplete();
            pipeline.fireExceptionCaught(cause);

            // If oom will close the read event, release connection.
            // See https://github.com/netty/netty/issues/10434
            if (close || cause instanceof OutOfMemoryError || cause instanceof IOException) {
                closeOnRead(pipeline);
            }
        }

        @Override
        public final void read() {
            // 读取配置 ..
            final ChannelConfig config = config();
            // 应该打断读准备
            if (shouldBreakReadReady(config)) {
                clearReadPending();
                return;
            }
            final ChannelPipeline pipeline = pipeline();
            final ByteBufAllocator allocator = config.getAllocator();
            final RecvByteBufAllocator.Handle allocHandle = recvBufAllocHandle();
            // 重置buf 大概估算一个暂存这些数据的大小 ..
            allocHandle.reset(config);

            ByteBuf byteBuf = null;
            boolean close = false;
            try {
                do {
                    byteBuf = allocHandle.allocate(allocator);

                    allocHandle.lastBytesRead(doReadBytes(byteBuf));
                    // lastBytesRead(n) 方法已经说明,如果为负数,则下一次的lastBytesRead() 必然是负数
                    // 假设这里为负数 或者 0,则标志中断 ...
                    if (allocHandle.lastBytesRead() <= 0) {
                        // nothing was read. release the buffer.
                        byteBuf.release();
                        byteBuf = null;
                        // 再次判断, 如果小于0,标识需要关闭,(已经接收到 EOF,没有任何数据需要进行读) ..
                        close = allocHandle.lastBytesRead() < 0;
                        if (close) {
                            // There is nothing left to read as we received an EOF.
                            // 设置读等待
                            readPending = false;
                        }
                        break;
                    }

                    // 假设上一次读取到数据,那么增加消息读的数量 ...
                    allocHandle.incMessagesRead(1);

                    // 继续设定 读等待false ..
                    // 因为已经在开始读取数据了 ..
                    readPending = false;
                    // 这个时候开始真正的触发消息读回调 ..
                    // 也就是读取到的消息处理

                    // 那你可能会很疑惑,如果包是一个不完整的会怎么办 ..
                    // 很显然,netty帮我们通过channelHandler的形式处理,在获得全包的时候向下传递消息, 那么我们就能够正确的处理到消息 ...
                    pipeline.fireChannelRead(byteBuf); // 触发消息读 ...(进行消息处理) ..
                    byteBuf = null;
                } while (allocHandle.continueReading());

                // 读取完成 ..
                allocHandle.readComplete();

                pipeline.fireChannelReadComplete(); // 这里执行读完成 回调...

                // 如果应该关闭 ..
                if (close) {
                    closeOnRead(pipeline);
                }
            } catch (Throwable t) {
                handleReadException(pipeline, byteBuf, t, close, allocHandle);
            } finally {
                // Check if there is a readPending which was not processed yet.
                // This could be for two reasons:
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelRead(...) method
                // * The user called Channel.read() or ChannelHandlerContext.read() in channelReadComplete(...) method
                //
                // See https://github.com/netty/netty/issues/2254
                if (!readPending && !config.isAutoRead()) {
                    removeReadOp();
                }
            }
        }
    }

    /**
     * Write objects to the OS.  写入对象到OS ...
     * @param in the collection which contains objects to write.
     * @return The value that should be decremented from the write quantum which starts at
     * {@link ChannelConfig#getWriteSpinCount()}. The typical use cases are as follows:
     * <ul>
     *     <li>0 - if no write was attempted. This is appropriate if an empty {@link ByteBuf} (or other empty content)
     *     is encountered</li>  0 (表示没有写入需要),如果是空的ByteBuf(或者其他空的内容)
     *     <li>1 - if a single call to write data was made to the OS</li>  1,表示单词调用去写入数据到OS ...
     *     <li>{@link ChannelUtils#WRITE_STATUS_SNDBUF_FULL} - if an attempt to write data was made to the OS, but no data was accepted</li>
     *      写状态发送缓冲区是满的(如果尝试写入数据到OS,但是没有数据接受) ....(也就是缓冲区满了) ..
     * </ul>
     * @throws Exception if an I/O exception occurs during write.
     */
    protected final int doWrite0(ChannelOutboundBuffer in) throws Exception {
        Object msg = in.current();
        if (msg == null) {
            // Directly return here so incompleteWrite(...) is not called.
            return 0;
        }
        return doWriteInternal(in, in.current());
    }

    private int doWriteInternal(ChannelOutboundBuffer in, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (!buf.isReadable()) {
                in.remove();
                return 0;
            }

            final int localFlushedAmount = doWriteBytes(buf);
            if (localFlushedAmount > 0) {
                in.progress(localFlushedAmount);
                if (!buf.isReadable()) {
                    in.remove();
                }
                return 1;
            }
        } else if (msg instanceof FileRegion) {
            FileRegion region = (FileRegion) msg;
            if (region.transferred() >= region.count()) {
                in.remove();
                return 0;
            }

            long localFlushedAmount = doWriteFileRegion(region);
            if (localFlushedAmount > 0) {
                in.progress(localFlushedAmount);
                if (region.transferred() >= region.count()) {
                    in.remove();
                }
                return 1;
            }
        } else {
            // Should not reach here.
            throw new Error();
        }
        return WRITE_STATUS_SNDBUF_FULL;
    }

    @Override
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        int writeSpinCount = config().getWriteSpinCount();
        do {
            Object msg = in.current();
            if (msg == null) {
                // Wrote all messages.
                clearOpWrite();
                // Directly return here so incompleteWrite(...) is not called.
                return;
            }
            writeSpinCount -= doWriteInternal(in, msg);
        } while (writeSpinCount > 0);

        incompleteWrite(writeSpinCount < 0);
    }

    @Override
    protected final Object filterOutboundMessage(Object msg) {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (buf.isDirect()) {
                return msg;
            }

            return newDirectBuffer(buf);
        }

        if (msg instanceof FileRegion) {
            return msg;
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    protected final void incompleteWrite(boolean setOpWrite) {
        // Did not write completely.
        if (setOpWrite) {
            setOpWrite(); // 继续保留写Ops ...
        } else {
            // It is possible that we have set the write OP, woken up by NIO because the socket is writable, and then
            // use our write quantum. In this case we no longer want to set the write OP because the socket is still
            // writable (as far as we know). We will find out next time we attempt to write if the socket is writable
            // and set the write OP if necessary.
            clearOpWrite();

            // Schedule flush again later so other tasks can be picked up in the meantime
            eventLoop().execute(flushTask);
        }
    }

    /**
     * Write a {@link FileRegion}
     *
     * @param region        the {@link FileRegion} from which the bytes should be written
     * @return amount       the amount of written bytes
     */
    protected abstract long doWriteFileRegion(FileRegion region) throws Exception;

    /**
     * Read bytes into the given {@link ByteBuf} and return the amount.
     *
     * 读取字节到 给定的ByteBuf 并返回读取数量 ...
     */
    protected abstract int doReadBytes(ByteBuf buf) throws Exception;

    /**
     * Write bytes form the given {@link ByteBuf} to the underlying {@link java.nio.channels.Channel}.
     * @param buf           the {@link ByteBuf} from which the bytes should be written
     * @return amount       the amount of written bytes
     */
    protected abstract int doWriteBytes(ByteBuf buf) throws Exception;

    protected final void setOpWrite() {
        final SelectionKey key = selectionKey();
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (!key.isValid()) {
            return;
        }
        final int interestOps = key.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) == 0) {
            key.interestOps(interestOps | SelectionKey.OP_WRITE);
        }
    }

    protected final void clearOpWrite() {
        final SelectionKey key = selectionKey();
        // Check first if the key is still valid as it may be canceled as part of the deregistration
        // from the EventLoop
        // See https://github.com/netty/netty/issues/2104
        if (!key.isValid()) { // 首先检查它是否仍然是有效的(它也许可能被取消了 从事件循环中取消注册的一部分) ...
            return;
        }
        final int interestOps = key.interestOps(); // 感兴趣的 Ops ..
        if ((interestOps & SelectionKey.OP_WRITE) != 0) {
            key.interestOps(interestOps & ~SelectionKey.OP_WRITE); // 去掉 OP_WRITE OPS ...
        }
    }
}
