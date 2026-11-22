# JinSimTouch

JinSimLauncher（FCL 改版）配套的手机触控显示策略模组。客户端专用，无服务端代码。

## 它干什么

启动器知道玩家在用触摸还是实体键鼠，模组只知道怎么把虚拟按键画出来、什么时候画。
两边靠一个同进程的桥接类沟通：启动器在 JVM 里注入 `com.jinjsl.bridge.JslTouchBridge`，
模组用缓存好的 VarHandle 读它的两个静态 volatile 字段，不 IPC、不开 Socket、不轮询文件。

## 目录

```
core/                     纯逻辑，不依赖 Minecraft，两个加载器共用
  TouchMode.java          三种模式
  TouchConfig.java        config/jinsimtouch.json 读写 + 2 秒热重载
  BridgeReader.java       VarHandle 缓存 + 契约校验 + 失败节流重试
  TouchStateMachine.java  显示/隐藏状态机（GUI 锁、防抖、防闪烁）
  TouchLayout.java        上下左右 + EXIT + 动作键的矩形布局
  TouchRuntime.java       把上面几件组装成一个实例
versions/1.20.1/fabric/   Fabric 端（DrawContext + HudRenderCallback）
versions/1.20.1/forge/    Forge 端（GuiGraphics + RegisterGuiOverlaysEvent）
versions/1.16.5/fabric/   Fabric 端（MatrixStack + HudRenderCallback）
versions/1.16.5/forge/    Forge 端（MatrixStack + RenderGameOverlayEvent.Post）
bridge/                   JslTouchBridge 契约类（启动器侧实现参考，不是模组代码）
launcher-src/             启动器侧的键鼠检测与桥接写入示例
```

同一份 `core` 通过 Gradle `sourceSets.main.java.srcDirs` 挂进四个子工程，改逻辑只改一处。

## 版本矩阵

| MC | Fabric | Forge | 状态 |
|---|---|---|---|
| 1.16.5 | 0.42.0+1.16 | 36.2.34（MCP 20210309） | 已写 |
| 1.20.1 | 0.92.2+1.20.1 | 47.2.20 | 已写 |
| 1.18.2 / 1.19.2 / 1.19.4 | 换 `gradle.properties` 三行 | 换 Forge 版本 + 映射 | 直接可套，渲染 API 与 1.20.1 同族 |
| 1.20.2 ~ 1.20.4 | 同上 | 同上 | Fabric 的 `HudRenderCallback` 参数变 `RenderTickCounter`，改一个方法签名 |
| 1.21.x | 改签名 + `ClientTickEvents` 不变 | `ClientTickEvent.Post` + `RegisterGuiLayersEvent` | 事件模型换了，需要另写一份 glue |

模组没覆盖的版本，启动器按“仅键鼠模拟”处理：不注入桥接、不显示虚拟按键，游戏照常跑。

## 桥接契约

```java
public static volatile InputSource lastInputSource;   // TOUCH / HARDWARE_KBM / NONE
public static volatile long lastInputTimeNs;          // System.nanoTime() 口径
```

`BridgeReader` 会校验：字段必须 `public static volatile`、时间戳必须是 `long`。
不满足或找不到类，都只记 `lastError`，不抛异常，2 秒后重试一次。

## 状态机规则

1. 桥接不存在（桌面环境、别的启动器）→ 等效 `FORCE_KBM`，按键隐藏，不报错。
2. `FORCE_TOUCH` 恒显示，`FORCE_KBM` 恒隐藏，这两个不看 GUI 也不看防抖。
3. `AUTO`：
   - 任一 GUI 打开（背包、聊天、箱子、设置……判断条件就是 `mc.currentScreen != null`）→ 锁住当前状态，禁止自动切换；
   - 正常游戏时，`now - lastInputTimeNs < debounceMs` 才按输入源切换：`TOUCH` 显示、`HARDWARE_KBM` 隐藏；
   - 超过防抖阈值就一直保持上一次状态，不再自动变，专门防静止时闪。
4. 第一次进入、桥接里还没输入记录时，默认显示（手机场景优先）。

## 配置

`config/jinsimtouch.json`，首次启动自动生成：

```json
{
  "mode": "AUTO",
  "debounceMs": 800,
  "showDebug": true,
  "showActionKeys": true,
  "keyScale": 1.0
}
```

`mode` 支持 `AUTO` / `FORCE_TOUCH` / `FORCE_KBM`（小写、带连字符也认）。
文件改动后 2 秒内生效，不用重启游戏。

## 点击由谁处理

模组只画。上下左右、EXIT、JUMP/SNEAK/INV 的命中区由启动器触摸层接管，
所以 `TouchLayout` 除了画，还能把矩形换算成 0~1 归一化坐标：

```java
float[] n = TouchLayout.normalized(layout.exit, guiWidth, guiHeight);
```

启动器拿到这四个数就能对齐自己的触摸热区，缩放、分辨率变化都不用重新算比例。

## 启动器侧怎么接

1. 把 `bridge/` 打成 `jinsim-bridge.jar`，塞进 MC 启动命令的 classpath（系统类加载器可见），放在 MC 之前。
2. 触摸层收到手指事件 → JNI 调 `JslTouchReporter.touch()`。
3. `JslInputDeviceWatcher` 监听 `InputManager` 的插拔回调，结果 → `JslTouchReporter.setHardwarePresent(present)`。
   没接键鼠时 `lastInputSource` 回到 `NONE`，配合“无输入记录默认显示”，就是你要的默认打开。
4. 蓝牙键鼠：`SOURCE_KEYBOARD` / `SOURCE_MOUSE` 判断已经覆盖，蓝牙 HID 走同一套 InputDevice 枚举，不用额外分支。

JNI 那一步是必须的：Android 的 ART 和 MC 的 JVM 是两个运行时，静态字段不共享。
`launcher-src` 里给的是示例类和现成写法，实际要接到 FCL 的触摸注入链路上（`JREUtils` 那一层）。

## 构建

```bash
cd versions/1.20.1/fabric && ./gradlew build      # -> build/libs/*.jar
cd versions/1.20.1/forge  && ./gradlew build
cd versions/1.16.5/fabric && ./gradlew build      # JDK 8
cd versions/1.16.5/forge  && ./gradlew build      # JDK 8
```

各子工程没带 gradle wrapper，用你本机的 `gradlew` 或 `gradle wrapper` 生成一个即可。
子工程根目录需要先 `gradle wrapper --gradle-version 8.6`（1.16.5 的两个用 7.6）。

## 已知限制

- 这四个工程没在真实环境编译过（这台机器没有 Android SDK / Forge 依赖缓存），代码按 1.20.1 与 1.16.5 的 API 写的，首次构建可能要按报错微调映射名。
- 叠加层收不到触摸事件，所以 EXIT/方向键必须由启动器触摸层命中，模组不接管点击。
- 模组只控制“显不显示”，不负责把触摸变成按键；移动、跳跃这些还是走启动器原来的控件。
