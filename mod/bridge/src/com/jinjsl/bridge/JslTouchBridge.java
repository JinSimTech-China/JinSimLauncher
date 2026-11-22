package com.jinjsl.bridge;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 桥接契约类（启动器侧实现参考，不属于模组）。
 *
 * <p>启动器在 JVM 初始化后、MC 启动前，把本类（打成 jinsim-bridge.jar）放进 MC 的系统类加载器，
 * 模组用 VarHandle 读下面两个 public static volatile 字段。全程同进程、无 IPC、无 Socket、不轮询文件。
 *
 * <p>字段契约（改这里的类型/修饰符 = 改协议，模组会拒绝加载）：
 * <ul>
 *   <li>lastInputSource：TOUCH / HARDWARE_KBM / NONE</li>
 *   <li>lastInputTimeNs：System.nanoTime() 口径的纳秒时间戳</li>
 * </ul>
 *
 * <p>写入时机：启动器触摸层收到触摸 → reportInput(TOUCH)；
 * Android 检测到实体键鼠（含蓝牙 HID）事件 → reportInput(HARDWARE_KBM)。
 */
public final class JslTouchBridge {

    public enum InputSource {
        TOUCH,
        HARDWARE_KBM,
        NONE
    }

    public static volatile InputSource lastInputSource = InputSource.NONE;
    public static volatile long lastInputTimeNs = 0L;

    /** 仅用于启动器侧排查，模组不读取 */
    private static final AtomicLong TOUCH_COUNT = new AtomicLong();
    private static final AtomicLong KBM_COUNT = new AtomicLong();

    private JslTouchBridge() { }

    /** 由启动器调用：上报一次输入，刷新时间戳 */
    public static void reportInput(InputSource source) {
        if (source == null) return;
        lastInputSource = source;
        lastInputTimeNs = System.nanoTime();
        if (source == InputSource.TOUCH) TOUCH_COUNT.incrementAndGet();
        else if (source == InputSource.HARDWARE_KBM) KBM_COUNT.incrementAndGet();
    }

    /** 设备热插拔：只改输入源，不刷新时间戳（避免误触发“刚有输入”而切状态） */
    public static void noteHardwarePresence(boolean keyboardOrMousePresent) {
        lastInputSource = keyboardOrMousePresent ? InputSource.HARDWARE_KBM : InputSource.NONE;
    }

    public static long touchCount() { return TOUCH_COUNT.get(); }
    public static long kbmCount() { return KBM_COUNT.get(); }
}
