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
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A special {@link ChannelInboundHandler} which offers an easy way to initialize a {@link Channel} once it was
 * registered to its {@link EventLoop}.
 *
 * 他是一个特定的ChannelInboundHandler 它提供了以一种简单的方式初始化一个Channel(一旦它已经注册到自己的EventLoop上) ...
 *
 * Implementations are most often used in the context of {@link Bootstrap#handler(ChannelHandler)} ,
 * {@link ServerBootstrap#handler(ChannelHandler)} and {@link ServerBootstrap#childHandler(ChannelHandler)} to
 * setup the {@link ChannelPipeline} of a {@link Channel}.
 *
 * 实现一般使用在Bootstrap#handler(ChannelHandler)的上下文中 ...
 * 那这里的上下文所的是 Bootstrap ... (有可能是服务器端或者客户端) ...
 *
 * 例如ServerBootstrap#handler(ChannelHandler) /  ServerBootstrap#childHandler(ChannelHandler) 用来配置一个Channel 的ChannelPipeline ...
 *
 *
 * <pre>
 *
 * public class MyChannelInitializer extends {@link ChannelInitializer} {
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("myHandler", new MyHandler());
 *     }
 * }
 *
 * {@link ServerBootstrap} bootstrap = ...;
 * ...
 * bootstrap.childHandler(new MyChannelInitializer());
 * ...
 * </pre>
 *
 * 切记这里标记为一个 Sharable(那么实现必须时线程安全的,为了能够重用) ...
 * Be aware that this class is marked as {@link Sharable} and so the implementation must be safe to be re-used.
 *
 *
 * 那么从这里我们完全已知,Sharable 本身是一种共享的行为,也就是说它同时可以添加到多个事件循环组中,不会产生竞争条件 ...
 * 并且它将对所有的Channel 共享,这是我所理解的Sharable ...
 *
 *
 * 总结:
 * ChannelInitializer 是用来配置一个Channel的Pipeline 如何工作 ...
 *
 * @param <C>   A sub-type of {@link Channel}
 */
@Sharable
public abstract class ChannelInitializer<C extends Channel> extends ChannelInboundHandlerAdapter {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ChannelInitializer.class);
    // We use a Set as a ChannelInitializer is usually shared between all Channels in a Bootstrap /
    // ServerBootstrap. This way we can reduce the memory usage compared to use Attributes.

    // 由于通常ChannelInitializer 在一个Bootstrap / ServerBootstrap中的所有管道之间进行共享,所以使用一个Set进行记录 .
    // 这种方式能够减少内存使用(相对于使用Attributes来说)...
    // 也就是通过Set 记录的方式能够比使用上下文中的属性,减少更多的内存 ...
    private final Set<ChannelHandlerContext> initMap = Collections.newSetFromMap(
            new ConcurrentHashMap<ChannelHandlerContext, Boolean>());

    /**
     * This method will be called once the {@link Channel} was registered.
     * After the method returns this instance
     * will be removed from the {@link ChannelPipeline} of the {@link Channel}.
     *
     * 当Channel 已经被注册之后,则开始调用 ..
     * 在这个方法返回之后,这个实例将会从ChannelPipeline中进行 移除 ..
     *
     * @param ch            the {@link Channel} which was registered.
     * @throws Exception    is thrown if an error occurs. In that case it will be handled by
     *                      {@link #exceptionCaught(ChannelHandlerContext, Throwable)} which will by default close
     *                      the {@link Channel}.
     */
    protected abstract void initChannel(C ch) throws Exception;

    @Override
    @SuppressWarnings("unchecked")
    public final void channelRegistered(ChannelHandlerContext ctx) throws Exception {
        // Normally this method will never be called as handlerAdded(...) should call initChannel(...) and remove
        // the handler.

        // 也就是说,通常这个方法不会被调用,因为handlerAdded(..)应该调用initChannel(..) 并移除处理器 ...

        if (initChannel(ctx)) {
            // 如果被调用了 ,那么由于增加了新的ChannelHandler ,则现在立即调用 管道的fireChannelRegistered()方法 确保不会丢失
            // 事件
            // we called initChannel(...) so we need to call now pipeline.fireChannelRegistered()
            // to ensure we not
            // miss an event.

            ctx.pipeline().fireChannelRegistered();

            // 现在初始化管道完毕,则移除这个管道的所有状态 ..
            // 有一个疑问,为什么还需要让当前处理器接收 fireChannelRegistered 事件 ..
            // 回想了一下,一次性事件并且应该被处理,丢弃后也无所谓了,相反不处理好像不是一件好事情 ...
            // We are done with init the Channel, removing all the state for the Channel now.
            removeState(ctx);
        } else {
            // 否则表示 已经初始化管道结束了 ..
            // 直接转发事件即可 ...

            // 但是它的翻译意思是 调用这个initChannel(...) 之前这是预期的行为,直接转发 ..
            // 那我直接认为是初始化管道已经结束了 ...
            // Called initChannel(...) before which is the expected behavior, so just forward the event.
            ctx.fireChannelRegistered();
        }
    }

    /**
     * Handle the {@link Throwable} by logging and closing the {@link Channel}. Sub-classes may override this.
     *
     * 通过记录和关闭管道来阻止 管道的重新初始化 ...
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (logger.isWarnEnabled()) {
            logger.warn("Failed to initialize a channel. Closing: " + ctx.channel(), cause);
        }
        ctx.close();
    }

    /**
     * {@inheritDoc} If override this method ensure you call super!
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // 管道在注册的情况下
        if (ctx.channel().isRegistered()) {
            // This should always be true with our current DefaultChannelPipeline implementation.
            // The good thing about calling initChannel(...) in handlerAdded(...) is that there will be no ordering
            // surprises if a ChannelInitializer will add another ChannelInitializer. This is as all handlers
            // will be added in the expected order.
            // 这个方法应该总是被执行(handlerAdded), 根据默认的DefaultChannelPipeline 实现 ...来说 ..
//            在handlerAdded中调用initChannel(...)的好处是 (没有无序的差异), 如果一个ChannelInitializer 增加到另一个ChannelInitializer 中 ..
            // 总是能够按照期待的顺序增加所有的处理器 ...
            if (initChannel(ctx)) {

                // 调用完成,移除初始化器 ...
                // We are done with init the Channel, removing the initializer now.
                removeState(ctx);
            }
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        initMap.remove(ctx);
    }

    /**
     * 在初始化一个管道的情况下,底层是一个ConcurrentHashMap,防止并发操作 ...
     * @param ctx
     * @return
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    private boolean initChannel(ChannelHandlerContext ctx) throws Exception {
        // 一旦存在一个进入此流程,则不可能重新进入 ..
        if (initMap.add(ctx)) { // Guard against re-entrance. (进行并发操作处理),防止重新进入 ..
            try {
                initChannel((C) ctx.channel());
            } catch (Throwable cause) {
                // 说明有可能在初始化管道之前都已经移除了 这个handler (有可能)
                // Explicitly call exceptionCaught(...) as we removed the handler before calling initChannel(...).
                // We do so to prevent multiple calls to initChannel(...).
                exceptionCaught(ctx, cause);
            } finally {
                // 如果上下文没有被移除 ... 则移除当前处理器 ...
                if (!ctx.isRemoved()) {
                    ctx.pipeline().remove(this);
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 同样,从初始化Map中移除这个上下文的对应Channel的初始化 ...
     * @param ctx
     */
    private void removeState(final ChannelHandlerContext ctx) {
        // The removal may happen in an async fashion if the EventExecutor we use does something funky.
        // 也就是可能通过异步的形式完成了移除 ... (以防万一) ..
        if (ctx.isRemoved()) {
            initMap.remove(ctx);
        } else {
            // The context is not removed yet which is most likely the case because a custom EventExecutor is used.
            // Let's schedule it on the EventExecutor to give it some more time to be completed in case it is offloaded.
            ctx.executor().execute(new Runnable() {
                @Override
                public void run() {
                    initMap.remove(ctx);
                }
            });
        }
    }
}
