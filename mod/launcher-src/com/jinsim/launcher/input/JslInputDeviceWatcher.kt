package com.jinsim.launcher.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice

/**
 * 启动器层：检测是否接了实体键鼠（USB / 蓝牙 HID / OTG），并把结果通知给桥接写入方。
 *
 * 判定规则：
 * - 排除 isVirtual 设备（启动器的虚拟输入设备不算外接键鼠）
 * - 排除同时带 SOURCE_TOUCHSCREEN 的内置触摸屏（它常带 SOURCE_KEYBOARD 源，会误判）
 * - 剩余设备只要带 SOURCE_KEYBOARD 或 SOURCE_MOUSE 就算“接了键鼠”
 *
 * 没接键鼠 → onChange(false) → 启动器把桥接写成 TOUCH / 默认显示虚拟按键。
 */
class JslInputDeviceWatcher(
    context: Context,
    private val onChange: (present: Boolean) -> Unit
) : InputManager.InputDeviceListener {

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private var lastPresent = false

    fun start() {
        inputManager.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))
        refresh(force = true)
    }

    fun stop() {
        inputManager.unregisterInputDeviceListener(this)
    }

    override fun onInputDeviceAdded(deviceId: Int) = refresh()

    override fun onInputDeviceRemoved(deviceId: Int) = refresh()

    override fun onInputDeviceChanged(deviceId: Int) = refresh()

    private fun refresh(force: Boolean = false) {
        val present = hasHardwareKeyboardOrMouse()
        if (force || present != lastPresent) {
            lastPresent = present
            onChange(present)
        }
    }

    fun hasHardwareKeyboardOrMouse(): Boolean {
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            if (device.isVirtual) continue
            val sources = device.sources
            val isTouch = (sources and InputDevice.SOURCE_TOUCHSCREEN) == InputDevice.SOURCE_TOUCHSCREEN
            val isKeyboard = (sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
            val isMouse = (sources and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE
            if (isMouse) return true
            if (isKeyboard && !isTouch) return true
        }
        return false
    }
}
