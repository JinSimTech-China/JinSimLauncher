package com.jinjsl.bridge;

/**
 * 桥接写入方（跑在 MC 那个 JVM 里，由启动器的 JNI 回调调用）。
 *
 * <p>为什么单独拆一个类：Android 侧（ART）和 MC 侧（JVM）是两个运行时，静态字段不共享。
 * 启动器触摸层通过已有的 JNI 触摸注入通道（FCL 里是 JREUtils 那条链路）调到这里，
 * 再由这里写 JslTouchBridge 的静态 volatile 字段，模组只读不写。
 *
 * <p>需要新增的 JNI 桥（示例）：
 * <pre>
 * Java_com_tungsten_fcl_util_JREUtils_reportTouch(JNIEnv*, jclass) {
 *     jclass c = env-&gt;FindClass("com/jinjsl/bridge/JslTouchReporter");
 *     env-&gt;CallStaticVoidMethod(c, env-&gt;GetStaticMethodID(c, "touch", "()V"));
 * }
 * </pre>
 */
public final class JslTouchReporter {

    private JslTouchReporter() { }

    /** 触摸层收到手指按下/滑动时调用 */
    public static void touch() {
        JslTouchBridge.reportInput(JslTouchBridge.InputSource.TOUCH);
    }

    /** 实体键鼠真实按键/移动事件时调用 */
    public static void hardwareKeyMouse() {
        JslTouchBridge.reportInput(JslTouchBridge.InputSource.HARDWARE_KBM);
    }

    /**
     * 设备热插拔：只改输入源，不动时间戳。
     * 拔掉键鼠时 present=false → 输入源回到 NONE，虚拟按键恢复默认显示。
     */
    public static void setHardwarePresent(boolean present) {
        JslTouchBridge.noteHardwarePresence(present);
    }
}
