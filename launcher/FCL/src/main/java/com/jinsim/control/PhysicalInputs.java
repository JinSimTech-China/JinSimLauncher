package com.jinsim.control;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;

/**
 * 检测是否有真实的物理鼠标 / 键盘接入（外接蓝牙、USB、Type-C 都算）。
 *
 * 用途：接上物理鼠标键盘后，屏幕上那套虚拟按键和系统鼠标指针就是多余的，
 * 由 GameMenu 负责把它们收起来；拔出后原样恢复。
 *
 * 判定规则：只看“有真实输入能力”的设备，排除系统虚拟设备
 * （id < 0 或名字里带 Virtual 的，都是 Android 自己造的软键盘/软鼠标）。
 */
public final class PhysicalInputs {

    public interface Listener {
        void onPhysicalInputChanged(boolean mouse, boolean keyboard);
    }

    private PhysicalInputs() {
    }

    private static boolean isReal(InputDevice device) {
        if (device == null) return false;
        if (device.getId() < 0) return false;
        String name = device.getName();
        if (name != null && name.toLowerCase().contains("virtual")) return false;
        return true;
    }

    public static boolean hasMouse(Context context) {
        InputManager manager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        if (manager == null) return false;
        for (int id : manager.getInputDeviceIds()) {
            InputDevice device = manager.getInputDevice(id);
            if (!isReal(device)) continue;
            if ((device.getSources() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE) return true;
        }
        return false;
    }

    public static boolean hasKeyboard(Context context) {
        InputManager manager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        if (manager == null) return false;
        for (int id : manager.getInputDeviceIds()) {
            InputDevice device = manager.getInputDevice(id);
            if (!isReal(device)) continue;
            if ((device.getSources() & InputDevice.SOURCE_KEYBOARD) != InputDevice.SOURCE_KEYBOARD) continue;
            if (device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_NONE) continue;
            // 手柄自带的几个按键不算键盘
            if ((device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) continue;
            return true;
        }
        return false;
    }

    /** 注册监听，回调在主线程；注册时会先按当前状态回调一次 */
    public static void watch(Context context, Listener listener) {
        final InputManager manager = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        if (manager == null) return;
        final Context app = context.getApplicationContext();
        manager.registerInputDeviceListener(new InputManager.InputDeviceListener() {
            @Override
            public void onInputDeviceAdded(int deviceId) {
                notifyState();
            }

            @Override
            public void onInputDeviceRemoved(int deviceId) {
                notifyState();
            }

            @Override
            public void onInputDeviceChanged(int deviceId) {
                notifyState();
            }

            private void notifyState() {
                new Handler(Looper.getMainLooper()).post(
                        () -> listener.onPhysicalInputChanged(hasMouse(app), hasKeyboard(app)));
            }
        }, new Handler(Looper.getMainLooper()));
        listener.onPhysicalInputChanged(hasMouse(app), hasKeyboard(app));
    }
}
