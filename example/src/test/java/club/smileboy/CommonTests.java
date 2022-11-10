package club.smileboy;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import sun.misc.VM;

public class CommonTests {
    /**
     * 字符串分割测试
     */
    @Test
    public void stringTest() {
        String value = ",";
        System.out.println(value.split(",").length);
    }

    /**
     * jvm 运行时最大内存
     * 以及 jvm 最大直接内存
     * <p>
     * 事实证明 -XX:MaxDirectMemorySize
     * 这两者不一样 ..
     * 如果不设置最大直接内存 ..
     * 那么两者一样,否则 以-XX:MaxDirectMemorySize 为准
     * <p>
     * 假设 -XX:MaxDirectMemorySize = 300m,你能够看出差距
     */
    @Test
    public void maxMemoryTest() {
        System.out.println(Runtime.getRuntime().maxMemory());

        System.out.println(PlatformDependent.maxDirectMemory());

        System.out.println(VM.maxDirectMemory());
    }


    @Test
    public void directAcquireMaxDirectMemoryTests() {
        System.out.println(VM.maxDirectMemory());
    }

    /**
     * 最大chunk 2 两个G
     */
    @Test
    public void systemPageSizeAmount() {
        long value = (long) Integer.MAX_VALUE + 1;
        System.out.println(value);
        System.out.println(value / 2);

        // 转换为 M
        System.out.println((value / 1024 / 1024));

        System.out.println(value / 1024 / 1024 / 1024);
    }

    @Test
    public void maxPageSizeAmountCaculate() {
        // 最大 14 ..
        long value = (long) Integer.MAX_VALUE + 1;
        System.out.println(value);
        System.out.println(value / 2);

        // 确保最终的chunkSize 不会移除 ...
        // Ensure the resulting chunkSize does not overflow.
        int chunkSize = 8192;
        for (int i = 14; i > 0; i --) {
            if (chunkSize > value / 2) {
                throw new IllegalArgumentException(String.format(
                        "pageSize (%d) << maxOrder (%d) must not exceed %d", 8192, 14, value));
            }
            chunkSize <<= 1;
        }

        System.out.println(chunkSize);
    }
}
