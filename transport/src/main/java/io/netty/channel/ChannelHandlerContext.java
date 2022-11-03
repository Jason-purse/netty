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
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.AttributeMap;
import io.netty.util.concurrent.EventExecutor;

/**
 * 让ChannelHandler 能够与它的ChannelPipeline 以及其他处理器 进行交互 ...
 *
 * 包含其他事情,例如一个handler 可以通知Pipeline中的下一个处理器,或者动态的修改ChannelPipeline ...
 * Enables a {@link ChannelHandler} to interact with its {@link ChannelPipeline}
 * and other handlers. Among other things a handler can notify the next {@link ChannelHandler} in the
 * {@link ChannelPipeline} as well as modify the {@link ChannelPipeline} it belongs to dynamically.
 *
 * 通知
 * 你能够通知最近的一个处理器(在相同ChannelPipeline) - 通过调用这里所提供的各种方法之一 ..
 * 请参考 ChannelPipeline 理解如何处理一个事件流 ...
 * <h3>Notify</h3>
 *
 * You can notify the closest handler in the same {@link ChannelPipeline} by calling one of the various methods
 * provided here.
 *
 * Please refer to {@link ChannelPipeline} to understand how an event flows.
 *
 * 修改一个pipeline ..
 * 你能够在handler中通过调用#pipeline()获得它所在的pipeline ...
 * 你能够执行复杂的插入、删除、替换handlers(在运行时动态替换pipeline中的handlers)
 * <h3>Modifying a pipeline</h3>
 *
 * You can get the {@link ChannelPipeline} your handler belongs to by calling
 * {@link #pipeline()}.  A non-trivial application could insert, remove, or
 * replace handlers in the pipeline dynamically at runtime.
 *
 * 抓取(保留)为了后续使用 ..
 * 能够保留ChannelHandlerContext 为了后续使用,例如在hander 方法之外触发一个事件,甚至是来自不同线程 ...
 * <h3>Retrieving for later use</h3>
 *
 * You can keep the {@link ChannelHandlerContext} for later use, such as
 * triggering an event outside the handler methods, even from a different thread.
 * // 例如以下代码，当登录的情况下触发一个事件 ...
 * <pre>
 * public class MyHandler extends {@link ChannelDuplexHandler} {
 *
 *     <b>private {@link ChannelHandlerContext} ctx;</b>
 *
 *     public void beforeAdd({@link ChannelHandlerContext} ctx) {
 *         <b>this.ctx = ctx;</b>
 *     }
 *
 *     public void login(String username, password) {
 *         ctx.write(new LoginMessage(username, password));
 *     }
 *     ...
 * }
 * </pre>
 *
 *
 * // 存储有状态信息 ...
 * <h3>Storing stateful information</h3>
 *
 * {@link #attr(AttributeKey)} allow you to
 * store and access stateful information that is related with a {@link ChannelHandler} / {@link Channel} and its
 * context. Please refer to {@link ChannelHandler} to learn various recommended
 * ways to manage stateful information.
 *
 *
 * attr(AttributeKey) 允许我们存储并访问 有状态数据(这些特定于ChanelHandler 或者Channel) 并与上下文关联 ...
 * 也可以使用非共享的ChannelHandler 成员变量 管理有状态信息,这是更推荐的 ...
 *
 * 一个处理器可以有多个ChannelHandlerContext ...
 * <h3>A handler can have more than one {@link ChannelHandlerContext}</h3>
 *
 * 由于ChannelHandler 实例能够增加到多个 ChannelPipeline ...
 * 这意味着单个ChannelHandler 实例能够共享多个ChannelHandlerContext ,因此这个handler 可以被复用 ...
 * 同样注意到 一个ChannelHandler 如果增加到多个ChannelPipeline (必须标记为 Sharable)
 * Please note that a {@link ChannelHandler} instance can be added to more than
 * one {@link ChannelPipeline}.  It means a single {@link ChannelHandler}
 * instance can have more than one {@link ChannelHandlerContext} and therefore
 * the single instance can be invoked with different
 * {@link ChannelHandlerContext}s if it is added to one or more {@link ChannelPipeline}s more than once.
 * Also note that a {@link ChannelHandler} that [is supposed to](应该) be added to multiple {@link ChannelPipeline}s should
 * be marked as {@link io.netty.channel.ChannelHandler.Sharable}.
 *
 * 额外的资源值得阅读
 * 请参考: ChannelHandler ... ChannelPipeline 去发现更多有关 inbound / outbound 操作, 它们的本质区别,channelhandler 如何在pipeline中进行流动
 * 并且如何在应用中处理操作 ...
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelHandler}, and
 * {@link ChannelPipeline} to find out more about inbound and outbound operations,
 * what fundamental differences they have, how they flow in a  pipeline,  and how to handle
 * the operation in your application.
 */
public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker {

    /**
     * Return the {@link Channel} which is bound to the {@link ChannelHandlerContext}.
     */
    Channel channel();

    /**
     * Returns the {@link EventExecutor} which is used to execute an arbitrary task.
     */
    EventExecutor executor();

    /**
     * The unique name of the {@link ChannelHandlerContext}.The name was used when then {@link ChannelHandler}
     * was added to the {@link ChannelPipeline}. This name can also be used to access the registered
     * {@link ChannelHandler} from the {@link ChannelPipeline}.
     */
    String name();

    /**
     * The {@link ChannelHandler} that is bound this {@link ChannelHandlerContext}.
     *
     * 返回和这个上下文绑定的ChannelHandler
     */
    ChannelHandler handler();

    /**
     * Return {@code true} if the {@link ChannelHandler} which belongs to this context was removed
     * from the {@link ChannelPipeline}. Note that this method is only meant to be called from with in the
     * {@link EventLoop}.
     */
    boolean isRemoved();

    @Override
    ChannelHandlerContext fireChannelRegistered();

    @Override
    ChannelHandlerContext fireChannelUnregistered();

    @Override
    ChannelHandlerContext fireChannelActive();

    @Override
    ChannelHandlerContext fireChannelInactive();

    @Override
    ChannelHandlerContext fireExceptionCaught(Throwable cause);

    @Override
    ChannelHandlerContext fireUserEventTriggered(Object evt);

    @Override
    ChannelHandlerContext fireChannelRead(Object msg);

    @Override
    ChannelHandlerContext fireChannelReadComplete();

    @Override
    ChannelHandlerContext fireChannelWritabilityChanged();

    @Override
    ChannelHandlerContext read();

    @Override
    ChannelHandlerContext flush();

    /**
     * Return the assigned {@link ChannelPipeline}
     */
    ChannelPipeline pipeline();

    /**
     * Return the assigned {@link ByteBufAllocator} which will be used to allocate {@link ByteBuf}s.
     */
    ByteBufAllocator alloc();

    /**
     * @deprecated Use {@link Channel#attr(AttributeKey)}
     */
    @Deprecated
    @Override
    <T> Attribute<T> attr(AttributeKey<T> key);

    /**
     * @deprecated Use {@link Channel#hasAttr(AttributeKey)}
     */
    @Deprecated
    @Override
    <T> boolean hasAttr(AttributeKey<T> key);
}
