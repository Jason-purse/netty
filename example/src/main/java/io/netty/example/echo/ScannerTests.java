package io.netty.example.echo;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Scanner;
import java.util.function.Consumer;

public class ScannerTests {
    public static void main(String[] args) {

        InputStream in = System.in;
        InputStreamReader reader = new InputStreamReader(in);
        CharBuffer charBuffer = CharBuffer.allocate(256);
        //waitInput(reader, charBuffer);
    }

    public  static void waitInput(InputStreamReader reader, CharBuffer charBuffer, Consumer<String> consumer) {
        while (true) {
            // 线程被打断了 ...
            if(Thread.interrupted()) {
                break;
            }
            try {
                if (reader.ready()) {
                    int read = reader.read(charBuffer);
                    System.out.println("准备好处理");
                    char[] array = charBuffer.array();
                    StringBuilder builder = new StringBuilder();
                    for (int i = 0; i < read; i++) {
                        if (array[i] == '\n') {
                            String s = builder.toString();
                            if (!s.trim().isEmpty()) {
                                consumer.accept(s);
                                break;
                            }
                        }
                        else {
                            builder.append(array[i]);
                        }
                    }
                }
                else {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                break;
            }
        }
        try {
            reader.close();
        } catch (Exception ee) {
            //
        }
    }


}
