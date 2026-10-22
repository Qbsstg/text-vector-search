import java.io.BufferedWriter;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Set;
import sun.misc.Unsafe;

/**
 * 仅用于固定JDK17、Boot4.0.3独立实验的只读队列观测，不是生产功能。
 * 不转换字节码、不创建Bean、不控制调度、不连接数据库、不读取环境秘密。
 */
public final class QueueReadOnlyAgent {
    private QueueReadOnlyAgent() { }

    /** 校验独立实验边界后，仅启动最多五分钟的守护观察线程。 */
    public static void premain(String outputArgument, Instrumentation instrumentation) throws Exception {
        if (!"1".equals(System.getenv("QUEUE_READ_ONLY_EXPERIMENT"))) {
            throw new IllegalStateException("仅允许显式只读实验标志");
        }
        String dataArgument = System.getenv("APP_DATA_DIR");
        if (dataArgument == null) {
            throw new IllegalStateException("实验数据目录缺失");
        }
        Path data = Path.of(dataArgument).toRealPath();
        if (!data.toString().matches("/var/lib/vector-search/perf-queue-d016-[A-Za-z0-9-]+/data")) {
            throw new IllegalStateException("拒绝生产或非专用实验数据目录");
        }
        Path output = Path.of(outputArgument).toAbsolutePath().normalize();
        if (!output.getParent().toRealPath().equals(data.getParent().resolve("logs").toRealPath())
                || !output.getFileName().toString().equals("queue-observations.jsonl")) {
            throw new IllegalStateException("输出必须是实验日志目录中的新文件");
        }
        // 只调用此只读初始化状态查询，禁止用Unsafe读写内存或强制初始化应用类。
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Files.setPosixFilePermissions(output, PosixFilePermissions.fromString("rw-------"));
        Thread observer = new Thread(() -> observe(instrumentation, unsafe, writer), "queue-read-only-observer");
        observer.setDaemon(true);
        observer.start();
    }

    /** 发现类和读取已有对象；所有失败只记录类型，不介入启动、恢复或业务门禁。 */
    private static void observe(Instrumentation instrumentation, Unsafe unsafe, BufferedWriter writer) {
        long deadline = System.nanoTime() + 300_000_000_000L;
        Class<?> applicationClass = null;
        try (writer) {
            while (System.nanoTime() < deadline) {
                try {
                    if (applicationClass == null) {
                        // Instrumentation只使用已加载类枚举，不注册任何转换器或重定义。
                        for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
                            if (loaded.getName().equals("org.springframework.boot.SpringApplication")) {
                                applicationClass = loaded;
                                break;
                            }
                        }
                    }
                    // Field.get(null)会初始化静态类；必须先确认Boot已自行完成初始化。
                    if (applicationClass == null || unsafe.shouldBeInitialized(applicationClass)) {
                        writeWaiting(writer, "WAIT_BOOT_INITIALIZATION");
                    } else {
                        Object dispatcher = existingDispatcher(applicationClass);
                        if (dispatcher == null) {
                            writeWaiting(writer, "WAIT_EXISTING_SINGLETON");
                        } else {
                            Class<?> type = dispatcher.getClass();
                            int queued = (Integer) type.getMethod("queuedCount").invoke(dispatcher);
                            int inFlight = (Integer) type.getMethod("inFlightCount").invoke(dispatcher);
                            boolean running = (Boolean) type.getMethod("isRunning").invoke(dispatcher);
                            // 两项为相邻只读调用，非原子联合快照；峰值是50ms采样峰而非连续极值。
                            writer.write("{\"time\":\"" + Instant.now() + "\",\"state\":\"OBSERVED\",\"queuedCount\":"
                                    + queued + ",\"inFlightCount\":" + inFlight + ",\"isRunning\":" + running + "}\n");
                            writer.flush();
                        }
                    }
                } catch (ReflectiveOperationException | RuntimeException exception) {
                    writeWaiting(writer, "READ_UNAVAILABLE_" + exception.getClass().getSimpleName());
                }
                Thread.sleep(50);
            }
        } catch (Exception exception) {
            // 不输出对象、配置或异常消息；观察失败不控制应用生命周期。
            System.err.println("只读队列观测停止：" + exception.getClass().getSimpleName());
        }
    }

    /** 根据实际Boot字节码同步策略复制注册上下文，锁内不调用BeanFactory。 */
    private static Object existingDispatcher(Class<?> applicationClass) throws ReflectiveOperationException {
        Field hookField = applicationClass.getDeclaredField("shutdownHook");
        hookField.setAccessible(true);
        Object hook = hookField.get(null);
        Class<?> hookClass = hook.getClass();
        Field contextsField = hookClass.getDeclaredField("contexts");
        contextsField.setAccessible(true);
        Object[] contexts;
        synchronized (hookClass) {
            contexts = ((Set<?>) contextsField.get(hook)).toArray();
        }
        for (Object context : contexts) {
            if (!Boolean.TRUE.equals(context.getClass().getMethod("isActive").invoke(context))) {
                continue;
            }
            Object factory = context.getClass().getMethod("getBeanFactory").invoke(context);
            Method contains = factory.getClass().getMethod("containsSingleton", String.class);
            if (Boolean.TRUE.equals(contains.invoke(factory, "taskDispatcher"))) {
                // 不使用getBean；getSingleton只取已经完整注册的现有单例，不触发构建。
                Object singleton = factory.getClass().getMethod("getSingleton", String.class)
                        .invoke(factory, "taskDispatcher");
                if (singleton != null && singleton.getClass().getName()
                        .equals("dev.twelvetimes.vector.task.TaskDispatcher")) {
                    return singleton;
                }
            }
        }
        return null;
    }

    private static void writeWaiting(BufferedWriter writer, String state) throws java.io.IOException {
        writer.write("{\"time\":\"" + Instant.now() + "\",\"state\":\"" + state + "\"}\n");
        writer.flush();
    }
}
