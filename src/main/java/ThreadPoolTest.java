import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * 场景：
 * 大任务拆小任务，并且需要等待小任务都完成，才完成（使用countdownLatch）
 * <p>
 * 现象：
 * 1. 拿到大任务的线程，必须等待其他执行小任务的线程完成任务。这期间不做任何事情。
 * 2. 一次提交的的大任务数大于线程数量时，直接死锁，不能完成
 * <p>
 * 将小任务提交到另一个线程池能解决第二个问题，但是如果大任务拆小任务是多层的，甚至是递归的（不确定多少层），就没好办法了
 * 第一个问题则怎么都不能解决
 */
public class ThreadPoolTest {
    private static final Logger log = LoggerFactory.getLogger("");
    // 如果numFork >= numThreads 则会观察到无法执行子任务
    private static final int numFork = 4;
    private static final int numThreads = 5;
    private static ThreadPoolExecutor pool = new ThreadPoolExecutor(numThreads, numThreads, 3, TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(100));
    private static final int time2work = 1000;


    public static void main(String[] args) {
        long now = System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(numFork);
        for (int i = 0; i < numFork; i++) {
            pool.submit(new ParentRunnable(String.valueOf(i), latch));
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 执行完毕，关闭线程池
        pool.shutdown();
        log.info("耗时:{}", System.currentTimeMillis() - now);
    }

    private static abstract class MyRunnable implements Runnable {
        protected String name;
        protected CountDownLatch latch;

        public MyRunnable(String name, CountDownLatch latch) {
            this.name = name;
            this.latch = latch;
        }

        protected abstract void doRun() throws InterruptedException;

        @Override
        public void run() {
            log.info("{} {} start", this.getClass().getSimpleName(), name);
            try {
                doRun();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
            log.info("{} {} done", this.getClass().getSimpleName(), name);
        }
    }

    private static final class SonRunnable extends MyRunnable {

        public SonRunnable(String name, CountDownLatch latch) {
            super(name, latch);
        }

        @Override
        protected void doRun() throws InterruptedException {
            Thread.sleep(time2work);
        }
    }

    private static final class ParentRunnable extends MyRunnable {

        public ParentRunnable(String name, CountDownLatch latch) {
            super(name, latch);
        }

        @Override
        protected void doRun() throws InterruptedException {
            Thread.sleep(time2work);
            CountDownLatch latch = new CountDownLatch(numFork);
            for (int i = 0; i < numFork; i++) {
                pool.submit(new SonRunnable(name + "/" + i, latch));
            }
            // 等待所有子任务完成，以便进行聚合操作
            latch.await();
            // 模拟做些操作
            Thread.sleep(time2work);
        }
    }

}
