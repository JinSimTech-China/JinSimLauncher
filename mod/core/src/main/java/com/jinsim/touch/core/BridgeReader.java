package com.jinsim.touch.core;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * 桥接读取模块：只负责从 com.jinjsl.bridge.JslTouchBridge 读两个静态 volatile 字段。
 *
 * <p>要点：
 * <ul>
 *   <li>用 VarHandle 而不是每帧 Field.get()，句柄解析一次后常驻缓存；</li>
 *   <li>桥接类由启动器注入系统类加载器，这里用 ClassLoader.getSystemClassLoader() 查找；</li>
 *   <li>找不到类 / 字段类型不符 / 模块访问受限，都只记日志并返回 false，绝不抛出到游戏主循环。</li>
 * </ul>
 */
public final class BridgeReader {

    public static final String BRIDGE_CLASS_NAME = "com.jinjsl.bridge.JslTouchBridge";
    public static final String FIELD_SOURCE = "lastInputSource";
    public static final String FIELD_TIME = "lastInputTimeNs";

    public static final String SRC_TOUCH = "TOUCH";
    public static final String SRC_KBM = "HARDWARE_KBM";
    public static final String SRC_NONE = "NONE";

    /** 解析失败后的重试间隔：2 秒重试一次，避免每帧反射 */
    private static final long RETRY_INTERVAL_NS = 2_000_000_000L;

    private static volatile VarHandle sourceHandle;
    private static volatile VarHandle timeHandle;
    private static volatile boolean available;
    private static volatile long nextResolveNs;
    private static volatile String lastError = "not resolved yet";

    private BridgeReader() { }

    public static boolean isAvailable() {
        return available;
    }

    public static String lastError() {
        return lastError;
    }

    /**
     * 确保桥接句柄已解析。可每帧调用，内部有节流；解析成功后直接返回 true，不再做任何反射。
     */
    public static boolean ensure(long nowNs) {
        if (available) return true;
        if (nowNs < nextResolveNs) return false;
        synchronized (BridgeReader.class) {
            if (available) return true;
            try {
                ClassLoader loader = ClassLoader.getSystemClassLoader();
                Class<?> bridge = Class.forName(BRIDGE_CLASS_NAME, false, loader);
                Field sourceField = bridge.getField(FIELD_SOURCE);
                Field timeField = bridge.getField(FIELD_TIME);

                // 契约校验：必须是 public static volatile
                requireStaticVolatile(sourceField);
                requireStaticVolatile(timeField);
                if (timeField.getType() != long.class) {
                    throw new IllegalStateException(FIELD_TIME + " must be long, got " + timeField.getType());
                }

                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                VarHandle src;
                VarHandle time;
                try {
                    src = lookup.findStaticVarHandle(bridge, FIELD_SOURCE, sourceField.getType());
                    time = lookup.findStaticVarHandle(bridge, FIELD_TIME, long.class);
                } catch (IllegalAccessException denied) {
                    // 某些加载器下 publicLookup 会拒绝，退一步用 privateLookupIn（模块开放时可用）
                    MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(bridge, MethodHandles.lookup());
                    src = privateLookup.findStaticVarHandle(bridge, FIELD_SOURCE, sourceField.getType());
                    time = privateLookup.findStaticVarHandle(bridge, FIELD_TIME, long.class);
                }

                sourceHandle = src;
                timeHandle = time;
                available = true;
                lastError = "";
                return true;
            } catch (Throwable t) {
                lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
                nextResolveNs = nowNs + RETRY_INTERVAL_NS;
                available = false;
                return false;
            }
        }
    }

    private static void requireStaticVolatile(Field f) {
        int mod = f.getModifiers();
        if (!Modifier.isStatic(mod) || !Modifier.isVolatile(mod) || !Modifier.isPublic(mod)) {
            throw new IllegalStateException(f.getName() + " must be public static volatile");
        }
    }

    /** 读输入源枚举名：TOUCH / HARDWARE_KBM / NONE。任何异常按 NONE 处理。 */
    public static String readSourceName() {
        VarHandle h = sourceHandle;
        if (!available || h == null) return SRC_NONE;
        try {
            Object value = h.get();
            if (value == null) return SRC_NONE;
            if (value instanceof Enum<?>) return ((Enum<?>) value).name();
            return String.valueOf(value);
        } catch (Throwable t) {
            return SRC_NONE;
        }
    }

    /** 读上次输入时间（纳秒，System.nanoTime 同源） */
    public static long readLastInputNs() {
        VarHandle h = timeHandle;
        if (!available || h == null) return 0L;
        try {
            return (long) h.get();
        } catch (Throwable t) {
            return 0L;
        }
    }
}
