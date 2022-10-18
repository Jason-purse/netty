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

import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Handles an I/O event or intercepts an I/O operation, and forwards it to its next handler in
 * its {@link ChannelPipeline}.
 *
 * 处理一个 io 事件或者拦截一个 io 操作 , 并根据它所在的Channel Pipeline 转发到下一个 handler ...
 *
 *
 * 子类型:
 * ChannelHandler 本身并没有提供许多方法,但是你可以实现它的子类型之一 ..
 * 1.  ChannelInboundHandler 用来处理进入的 io 事件 ..
 * 2. ChannelOutboundHandler 用来处理输出的 io 事件 ..
 *
 *
 * 同样还提供了一些适配器的类,为了更加方便(这是为了适配 jdk 8以下的适配器写法,但是现在应该最小都需要支持 jdk8环境) ..
 * 1. ChannelInboundHandlerAdapter ...
 * 2. ChannelOutboundHandlerAdapter ..
 * 3. ChannelDuplexHandler ... 同时处理输入以及输出事件的 适配器 .
 * <h3>Sub-types</h3>
 * <p>
 * {@link ChannelHandler} itself does not provide many methods, but you usually have to implement one of its subtypes:
 * <ul>
 * <li>{@link ChannelInboundHandler} to handle inbound I/O events, and</li>
 * <li>{@link ChannelOutboundHandler} to handle outbound I/O operations.</li>
 * </ul>
 * </p>
 * <p>
 * Alternatively, the following adapter classes are provided for your convenience:
 * <ul>
 * <li>{@link ChannelInboundHandlerAdapter} to handle inbound I/O events,</li>
 * <li>{@link ChannelOutboundHandlerAdapter} to handle outbound I/O operations, and</li>
 * <li>{@link ChannelDuplexHandler} to handle both inbound and outbound events</li>
 * </ul>
 * </p>
 * <p>
 * For more information, please refer to the documentation of each subtype.
 * </p>
 *
 * 上下文对象 ..
 * <h3>The context object</h3>
 * <p>
 * A {@link ChannelHandler} is [ provided with](拥有) a {@link ChannelHandlerContext}
 * object.  A {@link ChannelHandler} is supposed to interact with the
 * {@link ChannelPipeline} it belongs to via a context object.  Using the
 * context object, the {@link ChannelHandler} can pass events upstream or
 * downstream, modify the pipeline dynamically, or store the information
 * (using {@link AttributeKey}s) which is specific to the handler.
 *
 * ChannelHandlerContext 对象 提供给 ChannelHandler ..
 * 一个ChannelHandler 假设会和ChannelPipeline 进行交互(那么它通过一个属于对应上下文对象的一个方式进行 这样的一个交互) ...
 * 使用这个上下文,那么ChannelHandler 能够传递事件到上游或者下游 .. 动态的修改pipeline, 或者说存储信息(特定于这个handler的AttributeKey进行存储)
 *
 *
 * 状态管理
 * <h3>State management</h3>
 *
 * 一个ChannelHandler 通常需要存储某些有状态的信息 ...
 * 最简单以及最推荐的方式是使用成员变量 ..
 * 以下是一个方式 ...
 *
 * A {@link ChannelHandler} often needs to store some stateful information.
 * The simplest and recommended approach is to use member variables:
 * <pre>
 * public interface Message {
 *     // your methods here
 * }
 *
 * public class DataServerHandler extends {@link SimpleChannelInboundHandler}&lt;Message&gt; {
 *
 *     <b>private boolean loggedIn;</b>
 *
 *     {@code @Override}
 *     public void channelRead0({@link ChannelHandlerContext} ctx, Message message) {
 *         if (message instanceof LoginMessage) {
 *             authenticate((LoginMessage) message);
 *             <b>loggedIn = true;</b>
 *         } else (message instanceof GetDataMessage) {
 *             if (<b>loggedIn</b>) {
 *                 ctx.writeAndFlush(fetchSecret((GetDataMessage) message));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 *
 * 因为这个handler 实例包含了一个状态变量(它是专用于一个连接的) ..
 * 你需要创建一个新的handler 实例(这样每一个新的channel 将不会存在竞争条件),
 * 当一个未认证的客户端去获取机密信息时 ...
 * Because the handler instance has a state variable which is dedicated to
 * one connection, you have to create a new handler instance for each new
 * channel to avoid a race condition where a unauthenticated client can get
 * the confidential information:
 * <pre>
 * // Create a new handler instance per channel.
 * // See {@link ChannelInitializer#initChannel(Channel)}.
 * public class DataServerInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *     {@code @Override}
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("handler", <b>new DataServerHandler()</b>);
 *     }
 * }
 *
 * </pre>
 *
 * <h4>Using {@link AttributeKey}s</h4>
 *
 * 使用属性Key(AttributeKeys) ..
 * 尽管推荐使用ChannelHandler的成员变量 存储一个handler的状态 ..
 * 由于某些原因你可能不想要创建许多的处理器实例 ...
 * 在这样的情况下,你可以使用由ChannelHandlerContext 提供的AttributeKey ....
 * Although it's recommended to use member variables to store the state of a
 * handler, for some reason you might not want to create many handler instances.
 * In such a case, you can use {@link AttributeKey}s which is provided by
 * {@link ChannelHandlerContext}:
 *
 * 个人更推荐这种方式, 那么每一个Channel的上下文必然不同,那么携带的AttributeKey 必然是线程安全的 ...
 * <pre>
 * public interface Message {
 *     // your methods here
 * }
 *
 * {@code @Sharable}
 * public class DataServerHandler extends {@link SimpleChannelInboundHandler}&lt;Message&gt; {
 *     private final {@link AttributeKey}&lt;{@link Boolean}&gt; auth =
 *           {@link AttributeKey#valueOf(String) AttributeKey.valueOf("auth")};
 *
 *     {@code @Override}
 *     public void channelRead({@link ChannelHandlerContext} ctx, Message message) {
 *         {@link Attribute}&lt;{@link Boolean}&gt; attr = ctx.attr(auth);
 *         if (message instanceof LoginMessage) {
 *             authenticate((LoginMessage) o);
 *             <b>attr.set(true)</b>;
 *         } else (message instanceof GetDataMessage) {
 *             if (<b>Boolean.TRUE.equals(attr.get())</b>) {
 *                 ctx.writeAndFlush(fetchSecret((GetDataMessage) o));
 *             } else {
 *                 fail();
 *             }
 *         }
 *     }
 *     ...
 * }
 * </pre>
 *
 * 现在handler的状态依附于 ChannelHandlerContext上,也就是说你可以将相同的handler实例增加到不同的pipeline(而不会引起 竞争条件) ...
 * 并且通过上下文的形式,其他channelHandler 还可以做依赖关系检查 ..
 *
 * 还需要注意一点(需要加上@Sharable 注解) ... 标识它是共享的 ...
 * // 例如 spring-security的 ....Configurer(SecurityBuilder ...)
 * Now that the state of the handler is attached to the {@link ChannelHandlerContext}, you can add the
 * same handler instance to different pipelines:
 * <pre>
 * public class DataServerInitializer extends {@link ChannelInitializer}&lt;{@link Channel}&gt; {
 *
 *     private static final DataServerHandler <b>SHARED</b> = new DataServerHandler();
 *
 *     {@code @Override}
 *     public void initChannel({@link Channel} channel) {
 *         channel.pipeline().addLast("handler", <b>SHARED</b>);
 *     }
 * }
 * </pre>
 *
 *
 * @Sharable 注解 ...
 * <h4>The {@code @Sharable} annotation</h4>
 *
 *  上面的例子中你可以也注意到了 @Sharable 注解 ...
 *  也就是这种ChannelHandler 你能够仅仅创建一次,并增加到一个或者多个ChannelPipeline中(多次) ... 而不会发生竞争条件 ...
 *  如果没有注释,那么它将每一次增加到pipeline的时候都会重新创建一个handler 实例(因为它可能存在非共享的成员变量) ..
 * <p>
 * In the example above which used an {@link AttributeKey},
 * you might have noticed the {@code @Sharable} annotation.
 * <p>
 * If a {@link ChannelHandler} is annotated with the {@code @Sharable}
 * annotation, it means you can create an instance of the handler just once and
 * add it to one or more {@link ChannelPipeline}s multiple times without
 * a race condition.
 * <p>
 *
 * If this annotation is not specified, you have to create a new handler
 * instance every time you add it to a pipeline because it has unshared state
 * such as member variables.
 * <p>
 * This annotation is provided for documentation purpose, just like
 * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
 *
 * 地址发生了更新: https://jcip.net/annotations/doc/index.html
 *
 * 额外的资源值得阅读:
 * 请参考ChannelHandler  以及 ChannelPipeline 去发现更多有关输入或者输出的操作 ...
 * 它们有哪些本质上的不同,它们怎么在pipeline中形成一个流,并且如何在你的应用中处理操作 ....
 * <h3>Additional resources worth reading</h3>
 * <p>
 * Please refer to the {@link ChannelHandler}, and
 * {@link ChannelPipeline} to find out more about inbound and outbound operations,
 * what fundamental differences they have, how they flow in a  pipeline,  and how to handle
 * the operation in your application.
 */
public interface ChannelHandler {

    /**
     * Gets called after the {@link ChannelHandler} was added to the actual context and it's ready to handle events.
     */
    void handlerAdded(ChannelHandlerContext ctx) throws Exception;

    /**
     * Gets called after the {@link ChannelHandler} was removed from the actual context and it doesn't handle events
     * anymore.
     */
    void handlerRemoved(ChannelHandlerContext ctx) throws Exception;

    /**
     * Gets called if a {@link Throwable} was thrown.
     *
     * @deprecated if you want to handle this event you should implement {@link ChannelInboundHandler} and
     * implement the method there.
     */
    @Deprecated
    void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception;

    /**
     * Indicates that the same instance of the annotated {@link ChannelHandler}
     * can be added to one or more {@link ChannelPipeline}s multiple times
     * without a race condition.
     * <p>
     * If this annotation is not specified, you have to create a new handler
     * instance every time you add it to a pipeline because it has unshared
     * state such as member variables.
     * <p>
     * This annotation is provided for documentation purpose, just like
     * <a href="http://www.javaconcurrencyinpractice.com/annotations/doc/">the JCIP annotations</a>.
     */
    @Inherited
    @Documented
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Sharable {
        // no value
    }
}
