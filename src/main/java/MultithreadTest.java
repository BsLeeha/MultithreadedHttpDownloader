import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/*
 * list -> toString ok
 */

public class MultithreadTest {
    private static int counter = 0;

    private volatile boolean fire = false;

    public synchronized void incr() {
        counter++;
    }

    public synchronized int getCounter() {
        return counter;
    }

    public synchronized void fire() {
        this.fire = true;
        notify();
    }

    public static void main(String[] args) throws InterruptedException {
        MultithreadTest test = new MultithreadTest();

        int num = 1000;

        Thread[] threads = new Thread[num];
        IntStream.range(0, num).forEach(i -> {
            threads[i] = test.new CountThread();
            threads[i].start();
        });

        // yep, otherwise we will need to do try-catch inside lambda body
        for (int i = 0; i < num; i++) {
            threads[i].join();
        }

        System.out.println(counter);
    }

    class CountThread extends Thread {
        @Override
        public void run() {
            IntStream.range(0, 1000).forEach(i -> incr());
        }
    }
}
