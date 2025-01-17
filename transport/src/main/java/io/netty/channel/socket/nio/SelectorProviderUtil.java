/*
 * Copyright 2022 The Netty Project
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
package io.netty.channel.socket.nio;

import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.SuppressJava6Requirement;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.Channel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;

final class SelectorProviderUtil {
    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SelectorProviderUtil.class);

    // 从java 15开始,可以有新的方式 ...
    @SuppressJava6Requirement(reason = "Usage guarded by java version check")
    static Method findOpenMethod(String methodName) {
        if (PlatformDependent.javaVersion() >= 15) {
            try {
                // 大于 等于15的情况下,可以根据协议形式创建出 不同的SocketChannel ..
                return SelectorProvider.class.getMethod(methodName, java.net.ProtocolFamily.class);
            } catch (Throwable e) {
                logger.debug("SelectorProvider.{}(ProtocolFamily) not available, will use default", methodName, e);
            }
        }
        return null;
    }

    @SuppressJava6Requirement(reason = "Usage guarded by java version check")
    static <C extends Channel> C newChannel(Method method, SelectorProvider provider,
                                                    InternetProtocolFamily family) throws IOException {
        /**
         *  Use the {@link SelectorProvider} to open {@link SocketChannel} and so remove condition in
         *  {@link SelectorProvider#provider()} which is called by each SocketChannel.open() otherwise.
         *
         *  See <a href="https://github.com/netty/netty/issues/2308">#2308</a>.
         *
         *  使用SelectorProvider 去打开一个SocketChannel 并且 移除了 SelectorProvider#provider()条件 .
         *  否则每一个SocketChannel.open 都会调用一次 ..(官方解释是每秒5000个连接会损失至少百分之1的性能) ..
         *  这就是为什么直接cache provider ... 直接调用
         *
         *  https://docs.oracle.com/en/java/javase/15/docs/api/java.base/java/nio/channels/spi/SelectorProvider.html#openServerSocketChannel(java.net.ProtocolFamily)
         */
        if (family != null && method != null) {
            try {
                @SuppressWarnings("unchecked")
                C channel = (C) method.invoke(
                        provider, ProtocolFamilyConverter.convert(family));
                return channel;
            } catch (InvocationTargetException e) {
                throw new IOException(e);
            } catch (IllegalAccessException e) {
                throw new IOException(e);
            }
        }
        return null;
    }

    private SelectorProviderUtil() { }
}
