package club.smileboy.test;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.InternalThreadLocalMap;

/**
 * @author FLJ
 * @date 2022/10/25
 * @time 13:05
 * @Description typeParameter Matcher test
 */
public class TypeParameterMatcherTests {
    public static void main(String[] args) {

        class MyChannelReader extends SimpleChannelInboundHandler<String> {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {

            }
        }

        class LongChannelReader extends SimpleChannelInboundHandler<Long> {
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Long msg) throws Exception {

            }
        }

        new MyChannelReader();

        new LongChannelReader();

        InternalThreadLocalMap.get().typeParameterMatcherFindCache().forEach((key,value) -> System.out.println("key: " + key + ", value: " + value));

        System.out.println("------------------------------------------ get map----------------------------------");

        InternalThreadLocalMap.get().typeParameterMatcherGetCache().forEach((key,value) -> System.out.println("key: " + key + ", value: " + value));
    }
}
