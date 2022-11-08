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
import io.netty.buffer.Unpooled;
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
                                    System.out.println("发送数据到服务器");
                                    // 这里如果不进行flush ,则消息永远无法发出 ...
                                    // 因为我们这里是单独线程进行消息处理(不在pipeline的单次事件循环处理中) ...
                                    channel.writeAndFlush(Unpooled.wrappedBuffer(val.getBytes())).addListener((ChannelFutureListener) future -> {
                                        Throwable cause = future.cause();
                                        if(cause != null) {
                                            cause.printStackTrace();
                                        }

                                        Void unused = future.get();
                                        System.out.println("发送结果: " + unused);
                                    });
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
        ByteBuf buf = (ByteBuf) msg;
        int length = buf.readableBytes();
        byte[] bytes = new byte[length];
        buf.getBytes(0,bytes);
        System.out.println("client receive msg is " + msg);
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
