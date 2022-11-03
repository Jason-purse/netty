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

package io.netty.channel;

import io.netty.channel.ChannelHandlerMask.Skip;
import io.netty.util.internal.InternalThreadLocalMap;

import java.util.Map;

/**
 * Skeleton implementation of a {@link ChannelHandler}.
 * 这是一个ChannelHandler的骨架实现 ...
 */
public abstract class ChannelHandlerAdapter implements ChannelHandler {

    // 不需要使用volatile , 因为它仅仅 进行健全性检查 ...
    // 但是其本质上 放在了一个锁加持的情况下进行判断的 ...
    // @see io.netty.channel.DefaultChannelPipeline.addLast(io.netty.util.concurrent.EventExecutorGroup, java.lang.String, io.netty.channel.ChannelHandler)
    // Not using volatile because it's used only for a sanity check.
    boolean added;

    /**
     * Throws {@link IllegalStateException} if {@link ChannelHandlerAdapter#isSharable()} returns {@code true}
     */
    protected void ensureNotSharable() {
        if (isSharable()) {
            throw new IllegalStateException("ChannelHandler " + getClass().getName() + " is not allowed to be shared");
        }
    }

    /**
     * Return {@code true} if the implementation is {@link Sharable} and so can be added
     * to different {@link ChannelPipeline}s.
     */
    public boolean isSharable() {
        /**
         *
         * 缓存 Sharable注解的 检测(解决 一个条件), 使用ThreadLocal 以及 WeakHashMap 消除 volatile 写 / 读 ...
         * 为每一个线程使用不同的WeakHashMap 是足够好的,并且 线程的数量能够得到限制 ...
         *
         * Cache the result of {@link Sharable} annotation detection to workaround a condition. We use a
         * {@link ThreadLocal} and {@link WeakHashMap} to eliminate the volatile write/reads. Using different
         * {@link WeakHashMap} instances per {@link Thread} is good enough for us and the number of
         * {@link Thread}s are quite limited anyway.
         *
         * See <a href="https://github.com/netty/netty/issues/2289">#2289</a>.
         */
        Class<?> clazz = getClass();
        // 从线程的安全变量上去获取 SharableCache, 操作是安全的 ...
        Map<Class<?>, Boolean> cache = InternalThreadLocalMap.get().handlerSharableCache();
        Boolean sharable = cache.get(clazz);

        if (sharable == null) {
            // 有人说 java7中,这个方法下面是同步方法调用,导致多个线程阻塞 ...
            // 相当于每一个线程,同样还是可能需要去调用一次, 但是每个线程仅仅被阻塞一次 ..
            sharable = clazz.isAnnotationPresent(Sharable.class);
            cache.put(clazz, sharable);
        }
        return sharable;
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    /**
     * Do nothing by default, sub-classes may override this method.
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // NOOP
    }

    /**
     * Calls {@link ChannelHandlerContext#fireExceptionCaught(Throwable)} to forward
     * to the next {@link ChannelHandler} in the {@link ChannelPipeline}.
     *
     * Sub-classes may override this method to change behavior.
     *
     * @deprecated is part of {@link ChannelInboundHandler}
     */
    @Skip
    @Override
    @Deprecated
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        ctx.fireExceptionCaught(cause);
    }
}
