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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.netty.channel.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.util.Scanner;

/**
 * Handler implementation for the echo client.  It initiates the ping-pong
 * traffic between the echo client and server by sending the first message to
 * the server.
 */
public class EchoClientHandler extends ChannelInboundHandlerAdapter {

    private final ByteBuf firstMessage;

    private Thread thread;

    private Channel channel;


    /**
     * Creates a client-side handler.
     */
    public EchoClientHandler() {
        firstMessage = Unpooled.buffer(EchoClient.SIZE);
        firstMessage.writeBytes("hello,server!!!".getBytes());
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        ctx.writeAndFlush(firstMessage);
        if (thread != null) {
            thread.interrupt();
        }

        thread = new Thread(new Runnable() {


            @Override
            public void run() {

                    ScannerTests.waitInput(
                            new InputStreamReader(System.in),
                            CharBuffer.allocate(256),
                            val -> {
                                if(val.trim().equals("bye")) {
                                    // 关闭上下文 ...
                                    channel.close();
                                }
                                else {
                                    // 注意到 byteBuf 最后将会被自动回收 ..
                                    channel.writeAndFlush(Unpooled.buffer().writeBytes(val.getBytes())).addListener(new ChannelFutureListener() {
                                        @Override
                                        public void operationComplete(ChannelFuture future) throws Exception {
                                            Throwable cause = future.cause();
                                            if (cause != null) {
                                                cause.printStackTrace();
                                            }
                                        }
                                    });
                                    System.out.printf("发送数据 %s 到服务器%n", val);
                                }
                            }
                    );
            }
        });

        thread.start();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (this.channel == null) {
            this.channel = ctx.channel();
        }
        //ctx.write(msg);


        // 打印记录 ..

        System.out.println("client receive msg is " + msg);
        ByteBuf message = (ByteBuf) msg;
        byte[] bytes = new byte[message.readableBytes()];
        message.readBytes(bytes);
        System.out.println(new String(bytes));
        ctx.fireChannelRead(msg);

        //ctx.write(msg);
    }


    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // Close the connection when an exception is raised.
        cause.printStackTrace();

        System.out.println("发生了异常 !!!!!");
        ctx.close();
        System.out.println("设置线程打断状态 ....");
        thread.interrupt();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        this.channel = null;
    }

}
