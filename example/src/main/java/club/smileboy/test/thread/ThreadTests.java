package club.smileboy.test.thread;

public class ThreadTests {

    /**
     * 在打断的状态下,它仍然能够运行 ...
     * @param args args
     */
    public static void main(String[] args) throws InterruptedException {
        Thread thread = Thread.currentThread();
        thread.interrupt();

        System.out.println("你好");

        // 在打断状态下执行await ...
        // 应该会抛出异常

       try {
           synchronized(thread) {
               thread.wait();
           }
       }catch (InterruptedException ex) {
           ex.printStackTrace();

           System.out.println("打断异常已经捕获 !!!!");
       }


        System.out.println("over !!!");
    }
}
