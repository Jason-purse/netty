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
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

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

    private ChannelHandlerContext ctx;


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
                                    ctx.close();
                                }
                                else {
                                    ctx.write(val);
                                    System.out.println("发送数据到服务器");
                                }
                            }
                    );
            }
        });

        thread.start();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (this.ctx == null) {
            this.ctx = ctx;
        }
        //ctx.write(msg);

        // 打印记录 ..

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
        this.ctx = null;
    }
}
