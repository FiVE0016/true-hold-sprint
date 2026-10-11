# 真正的按住疾跑（Fabric / Minecraft Java 26.3）

> W 走路，Shift+W 疾跑，松开 Shift 立刻停止疾跑但继续前进。
> 作者：FiVE

一个极小的客户端 mod：**W 走路，Shift+W 疾跑，松开 Shift 立刻停止疾跑但继续前进。**

| 操作 | 原版 26.3（Shift 也是潜行键时） | 装了这个 mod |
| --- | --- | --- |
| 按住 W / A / S / D | 走路 / 侧移 / 后退 | 同左 |
| 移动键 + 左 Shift | 蹲下，疾跑起不来 | **疾跑（全向，前后左右都行）** |
| 松开 Shift，移动键还按着 | 状态不明确 / 仍可能保持疾跑 | **立刻停止疾跑，继续移动** |
| 移动键 + 左 Ctrl（原版疾跑键） | 只有按 W 能疾跑 | 全向疾跑，松开立刻停 |

## 为什么不自己改按键绑定就行

两个坑叠在一起：

1. **Shift 冲突**：左 Shift 默认是潜行键。你就算在游戏里把「疾跑」绑到左 Shift，按下也不会疾跑——因为同时触发了潜行，而**潜行会取消疾跑**。
2. **松键不停**：原版的停跑条件不总是包含「松开疾跑键」，某些设置（比如无障碍里的 Sprint: Toggle）下松键后仍会保持疾跑。

## 怎么解决的

只有一个 mixin（`LocalPlayerMixin`），挂在玩家每帧逻辑上：

**帧首（aiStep 开头）**

1. 读按键绑定：`keyUp`（W）、`keyShift`（潜行，也是我们的疾跑键）、`keySprint`（原版疾跑键）
2. 按住 Shift 时临时把潜行置为松开 —— 否则一按就蹲下，蹲下会取消疾跑
3. **把原版疾跑键也置为按下** ← 这是保住视野拉伸的关键（下面单独讲）
4. 直接 `setSprinting()`，双保险

**帧尾（aiStep 结尾）**

1. 还原被临时改过的按键状态
2. **再断言一次疾跑状态** —— 渲染和 FOV 采样在帧之后发生，只在帧首设一次的话中间可能被原版覆盖

另外，只清理「自己开起来」的疾跑，原版双击 W 的疾跑完整保留。

## 依赖

- **只依赖 Fabric Loader**，不依赖 Fabric API，也不依赖 Mod Menu。
- 纯客户端，服务端不需要装。联机时走的还是原版那套发包通道，没有额外自定义包。

## 编译（从源码到 jar）

> **完全没写过代码？** 直接看 [`怎么编译.md`](怎么编译.md)，那是给零基础的三步版说明。

### 最省事：跑脚本

| 系统 | 怎么做 |
| --- | --- |
| Windows | 双击 `build.bat` |
| Linux / macOS | `bash build.sh` |

脚本只做一件事：用你系统里已安装的 `gradle` 跑 `gradle build`。**不需要 Gradle Wrapper**，所以你已有的 Gradle 9.8.0 直接能用。

跑完的 jar 在 `build/libs/true-hold-sprint-1.0.0.jar`。

### 手动编译

```bash
cd true-hold-sprint
gradle build          # Windows 加不加 .bat 都行
```

前提：**JDK 25**（26.3 起 Mojang 要求 Java 25+，<https://adoptium.net/temurin/releases/?version=25>）。

### 关于 Gradle 版本

本项目的 `build.gradle` 用的是 **`net.fabricmc.fabric-loom` 1.18-SNAPSHOT**，这是 Fabric 给 **Minecraft 26.1 及以上（未混淆版）** 用的插件 ID —— 老教程里的 `fabric-loom` 是给 1.21.11 及以下的老版本用的，在 26.3 上不对。

26.1+ 的 Minecraft 本身就是未混淆的官方命名，**不需要任何映射**（不用 Yarn，也不用 `officialMojangMappings()`），所以 `dependencies` 里只有 `minecraft` 和 `fabric-loader` 两项。

Gradle 版本方面，Loom 1.18 支持 Gradle 9.x，你手上的 9.8.0 没问题。

### 第一次构建会下载什么

- Minecraft 26.3 的官方 jar + 依赖库（来自 `libraries.minecraft.net`）
- Fabric Loader

几百 MB，**首次 5~15 分钟**，之后是秒级增量编译。

### 国内下载慢 / 拉不到依赖

在 `~/.gradle/gradle.properties`（Windows：`C:\Users\你的用户名\.gradle\gradle.properties`）里加代理：

```properties
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

`libraries.minecraft.net` 和 `maven.fabricmc.net` 基本没有可用的国内镜像，走代理是最稳的。

### 常见编译报错

| 报错 | 原因 / 解决 |
| --- | --- |
| `Unsupported class file major version` / `release 25 not supported` | Java 版本太低，装 JDK 25 |
| `Could not resolve com.mojang:minecraft:26.3` | 网络问题，配代理；确认 `gradle.properties` 里 `minecraft_version=26.3` 没写错 |
| `Plugin [id: 'net.fabricmc.fabric-loom' ...] was not found` | 拉不到 Fabric maven，配代理 |
| `Could not find method modImplementation()` | 26.1+ 的不重映射 Loom 没有这个配置，一律用标准 `implementation`（本项目已改好） |
| 游戏崩 `IllegalClassLoadError: ... cannot be referenced directly` | 普通类不能放在 mixin 配置声明的那个包里。本项目已把共用逻辑类挪到主包，并给两层逻辑加了异常兜底 |
| 找不到 `screen` / `getWindow()` / `isKeyDown` 签名对不上 | 26.x 这一片的 API 改过好几轮。本项目对「当前界面字段」和「物理按键状态」都改成了运行时反射兜底，多种写法挨个试，不会再因为改名编译不过 |
| 找不到 `isDown` / `keySprint` / `keyShift` / `aiStep` | 26.3 的官方命名和我写的不一致，按报错改成对应名字（Mixin 都设了 `require = 0`，最坏情况是注入被跳过并警告，不会崩游戏） |
| 构建卡在 `:remapJar` 很久 | 正常，等就行 |

> 说明：我这边（沙盒）拿不到 Mojang / Fabric 的 Maven 仓库，没法帮你编译成 jar，只交付源码 + 脚本。拉依赖、编译都在你本地执行。

## 安装

把构建出来的 jar 丢进 `.minecraft/mods/`，和 Fabric Loader 0.19.5+ 一起用。首次启动会在 `.minecraft/config/true-hold-sprint.json` 生成配置。

## 配置

默认配置（Shift 疾跑）：

```json
{
  "enabled": true,
  "requireMovement": true,
  "allowBackwardSprint": true,
  "keepSprintDuringTick": true,
  "fixSprintJumpDirection": true,
  "suppressSneakWhileSprinting": true,
  "stopSprintOnRelease": true,
  "debugLog": false
}
```

| 字段 | 含义 |
| --- | --- |
| `enabled` | 总开关。`false` = 完全不介入，等同原版 |
| `requireMovement` | 是否必须按着移动键才疾跑，默认 `true`。移动键 = W / A / S / D 任意一个 |
| `allowBackwardSprint` | 后退（按 S）时是否也能疾跑，默认 `true`。关掉则退回原版行为：侧移可以，后退不行 |
| `sprintKey` | 疾跑键的 GLFW 键码，默认 340（左 Shift） |
| `altSprintKey` | 备用疾跑键，`-1` 关闭。想 Ctrl 也能疾跑就填 341 |
| `keepSprintDuringTick` | 拦住原版在帧中途取消疾跑，默认 `true`。关掉则侧移/后退只有视野变、速度不变 |
| `fixSprintJumpDirection` | 把疾跑跳跃的加速方向从「视角朝向」转到「实际移动方向」，默认 `true`。详见下面「跳跃方向」 |
| `suppressSneakWhileSprinting` | 按住疾跑键时屏蔽潜行（否则一按 Shift 就蹲下，疾跑被取消） |
| `altSneakKey` | 替代潜行键，`>0` 时完全接管潜行。默认 `-1` |
| `stopSprintOnRelease` | 松开疾跑键立刻停止疾跑（本 mod 的核心），默认 `true` |
| `forceSprintWhileKeyHeld` | 按住疾跑键时主动开启疾跑，默认 `true`。补上「原版收到按键却没起跑」的情况 |
| `preserveSprintFov` | 保留原版疾跑视野（FOV）拉伸，默认 `true`。本 mod 从不修改任何 FOV 代码，这项只是显式声明该保证（详见下节） |
| `debugLog` | 在日志里打印「疾跑键状态 + 实际疾跑状态」，用来确认键码和整条链路有没有通 |

## 疾跑由哪个键触发

**只认「疾跑」这个按键绑定**，也就是你在

> 选项 → 控制 → 按键绑定 → 疾跑

里设的那个键。mod 不硬编码任何具体按键。

**潜行键完全不参与触发。** 它绑在哪个键上，按哪个键就正常潜行，
疾跑和潜行互不干扰 —— 前提是两者**别绑到同一个键**上。

| 游戏内绑定 | 建议 |
| --- | --- |
| 疾跑 | 左 Shift（或你顺手的任何键） |
| 潜行 | 另一个键，别和疾跑撞 |

如果两个键撞在同一个物理键上，mod 会在按住时临时屏蔽潜行
（因为蹲下会取消疾跑），由 `suppressSneakWhileSprinting` 控制。

## 跳跃方向

原版有个硬编码：疾跑时跳跃会加一脚水平加速，方向取自**视角朝向**——
因为它假定「疾跑 = 向前」。于是我们让侧移/后退也能疾跑后，
这脚加速就加错方向了：不管朝哪跑，跳跃都往正前方窜。

本 mod 的做法是**让原版照常加，然后把这股增量从「朝向」转到「实际移动方向」**，
大小原样保留：

```
原版：增量 = 朝向 × 0.2
本 mod：减去 (朝向 × m)，加上 (移动方向 × m)    ← m 是量出来的，不硬编码 0.2
```

向前跑时两者重合，增量不变 —— **原版向前疾跑跳的手感是零改动的**。
移动方向取自起跳前的水平速度，不用去推 yaw 和侧移的数学，也不怕原版改常数。

`fixSprintJumpDirection` 设成 `false` 可关掉。

## 疾跑视野变化（FOV）

**原版的疾跑 FOV 拉伸完整保留，一个像素都没动。** 这是怎么保证的：

原版疾跑的视野变化是一条**下游链路**：

```
按住疾跑键
   ↓
原版判定 → isSprinting() = true
   ↓
原版给移动速度属性加上疾跑加成
   ↓
原版 FOV 计算读到速度变快 → 视野拉宽
   ↓
原版插值动画平滑推上去（不是瞬间跳变）
```

本 mod 只动了链条最上游那一环——**「疾跑键算不算按下」**，用的是原版自己的 `setSprinting()` / `isSprinting()`，**完全没有碰 `getFov`、`fovModifier` 或任何渲染代码**。所以：

| 你的操作 | 疾跑状态 | 视野（FOV） |
| --- | --- | --- |
| 按住 W + Shift | 疾跑 | 拉宽（和原版一模一样，带平滑过渡） |
| 松开 Shift，W 还按着 | 停 | 平滑收回（原版的回落动画也在） |
| 撞墙 / 饥饿值不足 | 停 | 收回（原版限制全部保留） |

反过来说，如果哪天你想**关掉**疾跑的视野变化，别改这个 mod，用原版自带的开关：

> 选项 → 无障碍设置 → **视野变化效果**（FOV Effects）→ 滑块拉到最左边

那里控制的是所有 FOV 效果的总强度，拉到 0 就完全没有视野变化了。

### 怎么确认视野变化还在

进世界，按住 W 走几步盯着画面边缘，然后按下左 Shift——画面应该明显「推远」一点。

想更确定可以把 `debugLog` 设成 `true`，进世界按 Shift，然后看 `.minecraft/logs/latest.log`，会看到类似：

```
[真正的按住疾跑] 疾跑键=按下 实际疾跑=是（原版 FOV 拉伸已生效）
[真正的按住疾跑] 疾跑键=松开 实际疾跑=否
```

有这两行，说明「键 → 疾跑状态 → FOV」整条链路是通的。

### 常用 GLFW 键码

| 键码 | 键 | 键码 | 键 |
| --- | --- | --- | --- |
| 340 | 左 Shift | 344 | 右 Shift |
| 341 | 左 Ctrl | 345 | 右 Ctrl |
| 342 | 左 Alt | 346 | 右 Alt |
| 32 | 空格 | 87 / 65 / 83 / 68 | W / A / S / D |
| 67 | C | 86 | V |

完整表：<https://www.glfw.org/docs/latest/group__keys.html>

### 三种常用预设

**A. Shift 疾跑，不要潜行（默认）**
```json
{"sprintKey": 340, "altSneakKey": -1}
```

**B. Shift 疾跑 + Ctrl 潜行**（中文输入法注意 `Ctrl + 空格` 会切输入法，可换成 86=V 或 67=C）
```json
{"sprintKey": 340, "altSneakKey": 341}
```

**C. 保留原版 Ctrl 疾跑，同时 Shift 也能疾跑**
```json
{"sprintKey": 340, "altSprintKey": 341}
```

## 排障

- **编译报「找不到 keySprint / aiStep / isDown」**：26.3 的 Mojang 映射名和我写的不一致。改对应名字即可——`KeyMappingMixin` 里是 `isDown` 与 `options.keySprint` / `options.keySneak`，`LocalPlayerMixin` 里是 `aiStep`。两个注入都写了 `require = 0`，方法名对不上最多是这条注入被跳过（启动日志会有 Mixin 警告），不会直接崩游戏。
- **装了没反应**：把 `debugLog` 设成 `true`，进世界后按 Shift，看 `logs/latest.log` 里有没有 `[真正的按住疾跑] 疾跑键=按下`。没有说明键码不对（试试 344）；有说明键码没问题，检查是不是开了别的疾跑类 mod 在打架。
- **双击 W 只跑一下就没了**：已修复。原因是本 mod 早先会无差别清掉任何疾跑，包括原版双击 W 触发的。现在只清理「自己开起来」的疾跑，原版双击 W 的疾跑会完整保留（不想要的话去「选项 → 无障碍设置」里关掉双击疾跑）。
- **按疾跑键没反应**：确认 `forceSprintWhileKeyHeld` 是 `true`。还可以在游戏里把「疾跑」这个按键绑定**清空**，避免原版绑定和本 mod 打架。
- **疾跑时视野没变化**：先确认原版就有——把本 mod 的 `enabled` 设成 `false` 重启游戏，用原版的 Ctrl 疾跑看看 FOV 动不动。如果原版也不动，去「选项 → 无障碍设置 → 视野变化效果」，滑块可能被拉到最左边了。
- **双击 W 还是会疾跑**：这是原版行为，本 mod 没动它。想只认 Shift 就把游戏里「疾跑」那个绑定清空（Esc → 选项 → 控制 → 按键绑定 → 疾跑 → 取消绑定），双击 W 的疾跑是写死在玩家逻辑里的，需要额外处理，可以再提需求。
- **背包里 Shift 点物品正常吗**：正常。mod 只在「人在世界里、没开任何界面」时接管，GUI 里一律走原版。

## 已知取舍

- 用 Shift 当疾跑键且没配 `altSneakKey` 时，游戏内就没法潜行了（潜行被屏蔽）。要么配 `altSneakKey`，要么把疾跑键换成别的键。
- 饥饿值 ≤ 6、撞墙、正在用物品等原版疾跑限制全部保留，mod 只决定「键有没有按住」。
---

## 视野拉伸（FOV）是怎么保住的

**这是本 mod 最容易踩的坑，单独说清楚。**

只在外面调用 `player.setSprinting(true)` —— 能跑起来，但**视野不会变**。原因：

原版疾跑是一整套链：

```
原版疾跑键按下
   ↓
原版自己判定（要前进、不潜行、饥饿够…）
   ↓
给 MOVEMENT_SPEED 属性加上疾跑修正
   ↓
FOV 读这个属性 → 视野拉宽
   ↓
平滑插值推上去
```

**FOV 读的是速度属性，不是那个布尔标记。** 从外部把标记置 true，只覆盖了链条下游的一半：
移动速度是变了，但原版那套修正没走，FOV 就一直是 1.0。

**本 mod 的做法：把原版疾跑键也置为按下**，让原版自己走完整套流程。
速度、FOV、给服务端发包全是原版原生的，手感一模一样。

再加两道保险：
1. 帧尾（aiStep 结束时）**再断言一次**疾跑状态 —— 渲染和 FOV 采样在帧之后发生，
   只在帧首设一次的话中间可能被原版覆盖，视野又没了。
2. 只清理「自己开起来」的疾跑 —— 否则会把原版双击 W 的疾跑一起干掉。

---

## 与「直接 setSprinting」那版实现的对比

有人用下面这个极简实现也能跑起来：

```java
// 帧首
player.setSprinting(forward && shift);
```

它能冲刺，但**没有视野拉伸**——原因如上。

本 mod 保留了它的骨架（读按键绑定 → 直接定结果，不猜原版内部），
补上了三件事：

| | 极简版 | 本 mod |
| --- | --- | --- |
| 冲刺速度 | ✅ | ✅ |
| 视野拉伸 FOV | ❌ | ✅（让原版自己走完整套） |
| 帧内状态被原版覆盖 | 会丢 FOV | 帧尾再断言一次 |
| 原版双击 W 疾跑 | 不受影响 | 不受影响（只清理自己的） |
| Shift 蹲下取消疾跑 | 需自己处理 | 临时屏蔽潜行 |

### 设计原则

**别骗原版，让原版自己干活。**

最早一版走的是「改 `KeyMapping#isDown()` 让原版以为疾跑键按下了」——
这条路完全依赖对 26.3 内部的猜测，结果一连串失败（字段名对不上、签名变了、
清理逻辑误杀原版疾跑）。改成「读按键绑定 → 直接定结果 + 把原版疾跑键也置位」之后就稳了。

顺带两个教训：
- **不要为了零依赖而拒绝 Fabric API**。用稳定钩子（`ClientTickEvents`）比自己 Mixin 猜方法名可靠得多。
- **验证要看结果，不要看中间量**。日志里"疾跑标记=true"只说明标记设上了，
  不代表角色真的跑快了 —— 曾经据此误判"成功了"。
