import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.RecursiveAction;

/**
 * 解决ThreadPool在上述场景问题的原理：
 * 线程不做父任务的监工，而是直接去做子任务
 * 这样就能提升并法度了
 */
public class ForkJoinPoolTest {
    private static int numThread = 4;
    private static int numFork = numThread;
    private static ForkJoinPool pool = new ForkJoinPool(numThread, pool -> {
        final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
        worker.setName("pool-" + worker.getPoolIndex());
        return worker;
    }, null, false);
    private static final int time2work = 1000;
    private static final Logger log = LoggerFactory.getLogger("");

    public static void main(String[] args) {
        long now = System.currentTimeMillis();
        List<Action> actions = new ArrayList<>();
        for (int i = 0; i < numFork; i++) {
            ParentAction parentAction = new ParentAction(String.valueOf(i));
            actions.add(parentAction);
            pool.submit(parentAction);
        }
        for (Action action : actions) {
            action.join();
        }
        pool.shutdown();
        log.info("耗时:{}", System.currentTimeMillis() - now);
    }

    private static abstract class Action extends RecursiveAction {
        private String name;

        public String getName() {
            return name;
        }

        public Action(String name) {
            this.name = name;
        }

        protected abstract void doCompute() throws InterruptedException;

        @Override
        protected void compute() {
            log.info("{} {} start", this.getClass().getSimpleName(), this.getName());
            try {
                doCompute();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            log.info("{} {} done", this.getClass().getSimpleName(), this.getName());
        }
    }

    private static final class ParentAction extends Action {

        public ParentAction(String name) {
            super(name);
        }

        @Override
        protected void doCompute() throws InterruptedException {
            Thread.sleep(time2work);
            List<Action> actions = new ArrayList<>();
            for (int i = 0; i < numFork; i++) {
                SonAction son = new SonAction(getName() + "/" + String.valueOf(i));
                actions.add(son);
            }
            // 或者for action.fork()；推荐多任务时使用invokeAll()
            ForkJoinTask.invokeAll(actions);
            for (Action action : actions) {
                action.join();
            }
            Thread.sleep(time2work);
        }
    }

    private static final class SonAction extends Action {

        public SonAction(String name) {
            super(name);
        }

        @Override
        protected void doCompute() throws InterruptedException {
            Thread.sleep(time2work);
        }
    }

}
