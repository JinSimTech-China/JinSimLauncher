# JinSimLauncher

JinSimLauncher 工程的本地工作区。启动器本体是 Fold Craft Launcher（FCL）的改版，基于 FCL-Team 开源的 FCL 源码修改；触控模组是本仓库新增的配套件。

## 目录

```
launcher/   FCL 源码（git 仓库，remote 指向 FCL-Team/FoldCraftLauncher，改动都在工作区未提交）
mod/        JinSimTouch 触控模组源码（core + Fabric/Forge × 1.16.5/1.20.1 + 桥接契约 + 启动器侧示例）
dist/       已导出的补丁、包和示意图
```

## 当前状态

**启动器（launcher/）**

- UI 主题改版：主色 `#5046E4`、淡薰衣草渐变背景、卡片圆角 16dp、弹窗白底 18dp、输入框 `#E8EFFD`（已改 24 个文件）
- 改名 JinSimLauncher：`app_name`、关于页标题、关于页说明（中英文，明确写了"是 FCL 的改版"）、崩溃报告头、Shell 启动日志
- 账号模块：已建 `FCL/src/main/java/com/jinsim/account/`，目前有 `AccountModels.kt`（Jinsim/微软/离线三种模型的加密字段约束）和 `AccountCrypto.kt`（Android Keystore AES/GCM，落盘全密文）。仓库、云端同步、PKCE、Edge Custom Tabs、WebView 回退、UI 还在写

**模组（mod/）**

- `core`：桥接读取（VarHandle 缓存）、状态机（AUTO/FORCE_TOUCH/FORCE_KBM + GUI 锁 + 800ms 防抖 + 防闪烁）、布局（十字键/EXIT/动作键 + 归一化坐标给启动器对齐热区）、配置热重载
- 四个子工程：1.20.1 Fabric/Forge、1.16.5 Fabric/Forge，共用 core
- 启动器侧示例：`JslInputDeviceWatcher.kt`（InputManager 检测实体键鼠含蓝牙 HID，排除虚拟设备与带触摸屏的内置键盘）、`JslTouchReporter.java`（JVM 内的桥接写入落点，需要 JNI 接进触摸注入链）

## 构建环境（本机已具备）

- SDK：`/home/user/android-sdk`（platforms android-34 / android-35，build-tools 34.0.0 / 35.0.0）
- JDK：`/home/user/jdk17`（Temurin 17.0.20）
- Gradle：8.4，`~/.gradle` 有缓存

FCL 本体 gradle wrapper 是 8.14.4，首次构建需要联网拉依赖。1.16.5 的两个模组子工程需要 JDK 8。

## dist 里的东西

| 文件 | 内容 |
|---|---|
| `JinSimLauncher-当前改动.patch` | 26 个文件的完整改动（含新增账号模块文件），可直接 `git apply` |
| `FCL-UI改版-改动文件.zip` | 同上的文件级版本（24 个 UI/改名文件，不含账号模块） |
| `FoldCraftLauncher-源码-20261121.zip` | 拿到源码当天的原始快照（main @ 722e236），留作对照 |
| `JinSimTouchMod.zip` | 模组源码打包 |
| `FCL改版示意-主页.png` / `FCL改版示意-登录弹窗.png` | 改版后的界面示意（非实机截图） |

## 待办

1. 账号模块剩下的部分：AccountRepository（加密落盘 + 归属校验）、JslApi（登录/同步/上传 token）、MicrosoftPkce、Edge Custom Tabs 唤起、OAuth 回调 Activity、WebView 回退页、离线 UUID 生成、启动参数拼装、个人中心 UI
2. 编译验证：`launcher` 出 APK、模组 1.20.1 Fabric 出 jar，按报错修映射名
3. 模组 1.21.1 的两份入口（`ClientTickEvent.Post` / `RegisterGuiLayersEvent` / `HudRenderCallback` 参数换成 `RenderTickCounter`）
4. JNI 接线：把 `JslTouchReporter` 挂到 FCL 的触摸注入链路（`JREUtils` 那一层）

## 协议与开源

- 本项目整体按 **GNU Affero General Public License v3.0（AGPL-3.0）** 分发，全文见 `LICENSE`。
- 本项目是 **Fold Craft Launcher（GPL-3.0）** 的改版。依据 GPL-3.0 第 13 条，GPLv3 代码与 AGPLv3 代码组合后整体按 AGPLv3 分发；上游版权与 GPL 文本继续有效。来源与修改摘要见 `NOTICE`。
- `backend/`、`web/`、`site/` 为自有实现，可作为独立组件单独授权。
- 对应源码随发布提供；若通过二进制分发，源码以本仓库为准。
- 「Minecraft」为 Mojang Studios 商标；本项目非 Mojang / Microsoft / FCL 团队官方产品。
