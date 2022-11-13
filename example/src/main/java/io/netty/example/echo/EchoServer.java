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
package io.netty.example.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.example.util.ServerUtil;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Echoes back any received data from a client.
 */
public final class EchoServer {

    static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));

    static final InternalLogger logger = InternalLoggerFactory.getInstance(EchoServer.class);

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx = ServerUtil.buildSslContext();

        // Configure the server.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);

        EventLoopGroup workerGroup = new NioEventLoopGroup();


        final EchoServerHandler serverHandler = new EchoServerHandler();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 100)
                    .handler(new ChannelInitializer<ServerSocketChannel>() {
                        @Override
                        protected void initChannel(ServerSocketChannel p) throws Exception {
                            p
                                    .pipeline()
                                    .addLast(new LoggingHandler(LogLevel.INFO))
                                    .addLast(new ChannelInboundHandlerAdapter() {
                                        @Override
                                        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {
                                            logger.info("channelHandler in parent event loop invoked, channelRegistered !!!");
                                        }

                                        @Override
                                        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
                                            logger.info("channelHandler in parent event loop invoked, handlerAdded !!!");
                                        }
                                    });
                        }
                    })
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            if (sslCtx != null) {
                                p.addLast(sslCtx.newHandler(ch.alloc()));
                            }
                            //p.addLast(new LoggingHandler(LogLevel.INFO));
                            p.addLast(serverHandler);

                        }
                    });


            // Start the server.
            //ChannelFuture f = b.bind(PORT).sync();

            // 在register方法中(初始化管道的时候已经分配好了 事件循环组) ..
            // 也就是说,从register中出来,我们就可以开始做其他额外的动作 ...
            // 也就是说,这里我们拿取channel 进行bind,必然是非事件循环组内调用
            // 但是bind方法需要在对应的事件循环组内进行调度 ..

            // 当我们没有给定对应的事件循环组时,register 永远是最先调度 (bind 由于线程安全,也需要放入对应的事件循环组中调度) //

            // 现在我们直接给定,那么它将如何处理 ...
            ChannelFuture future = b.bind(PORT).sync();

            // Wait until the server socket is closed.
            future.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
