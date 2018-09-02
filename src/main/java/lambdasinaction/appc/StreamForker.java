package lambdasinaction.appc;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * Adapted from http://mail.openjdk.java.net/pipermail/lambda-dev/2013-November/011516.html
 * 对原始的流进行封装，在此基础上可以继续定义希望执行的各种操作。
 */
public class StreamForker<T> {

    private final Stream<T> stream;
    private final Map<Object, Function<Stream<T>, ?>> forks = new HashMap<>();

    public StreamForker(Stream<T> stream) {
        this.stream = stream;
    }

    /**
     * @param key 通过它可以取得操作的结果，并将这些键/函数对累积到一个内部的Map中
     * @param f 对流进行处理，将流转变为代表这些操作结果的任何类型
     * @return 返回StreamForker自身，可以通过复制多个操作构造一个流水线
     */
    public StreamForker<T> fork(Object key, Function<Stream<T>, ?> f) {
        forks.put(key, f);    //使用一个键对流上的函数进行索引
        return this;          //返回this从而保证多次流畅地调用fork方法
    }

    /**
     * 通过此方法的调用触发fork
     * @return Results接口的实现
     */
    public Results getResults() {
        ForkingStreamConsumer<T> consumer = build();
        try {
            stream.sequential().forEach(consumer);
        } finally {
            consumer.finish();
        }
        return consumer;
    }

    /**
     * 主要任务是处理流中的元素，将它们分发到多个BlockingQueues中处理
     * @return
     */
    private ForkingStreamConsumer<T> build() {
        //创建由队列组成的列表，每个队列对应一个操作
        List<BlockingQueue<T>> queues = new ArrayList<>();

        Map<Object, Future<?>> actions =
            forks.entrySet().stream().reduce(
                new HashMap<>(),
                (map, e) -> {
                    map.put(e.getKey(),
                          getOperationResult(queues, e.getValue()));
                    return map;
                },
                (m1, m2) -> {
                    m1.putAll(m2);
                    return m1;
                }
            );

        return new ForkingStreamConsumer<>(queues, actions);
    }

    private Future<?> getOperationResult(List<BlockingQueue<T>> queues, Function<Stream<T>, ?> f) {
        BlockingQueue<T> queue = new LinkedBlockingQueue<>();
        //创建队列，并将其添加到队列的列表中
        queues.add(queue);
        //创建一个Spliterator，遍历队列中的元素
        Spliterator<T> spliterator = new BlockingQueueSpliterator<>(queue);
        //创建一个流，将Spliterator作为数据源
        Stream<T> source = StreamSupport.stream(spliterator, false);
        //创建一个Future对象，以异步方式计算在流上执行特定函数的结果
        return CompletableFuture.supplyAsync( () -> f.apply(source) );
    }

    public static interface Results {
        public <R> R get(Object key);
    }

    private static class ForkingStreamConsumer<T> implements Consumer<T>, Results {
        static final Object END_OF_STREAM = new Object();

        private final List<BlockingQueue<T>> queues;
        private final Map<Object, Future<?>> actions;

        ForkingStreamConsumer(List<BlockingQueue<T>> queues, Map<Object, Future<?>> actions) {
            this.queues = queues;
            this.actions = actions;
        }

        @Override
        public void accept(T t) {
            queues.forEach(q -> q.add(t));
        }

        @Override
        public <R> R get(Object key) {
            try {
                return ((Future<R>) actions.get(key)).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        //将最后一个元素添加到队列中，表明该流已经结束
        void finish() {
            accept((T) END_OF_STREAM);
        }
    }

    private static class BlockingQueueSpliterator<T> implements Spliterator<T> {
        private final BlockingQueue<T> q;

        BlockingQueueSpliterator(BlockingQueue<T> q) {
            this.q = q;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            T t;
            while (true) {
                try {
                    t = q.take();
                    break;
                }
                catch (InterruptedException e) {
                }
            }

            if (t != ForkingStreamConsumer.END_OF_STREAM) {
                action.accept(t);
                return true;
            }

            return false;
        }

        @Override
        public Spliterator<T> trySplit() {
            return null;
        }

        @Override
        public long estimateSize() {
            return 0;
        }

        @Override
        public int characteristics() {
            return 0;
        }
    }
}
