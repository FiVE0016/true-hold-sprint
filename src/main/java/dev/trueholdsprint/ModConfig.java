package dev.trueholdsprint;

/**
 * 配置文件：.minecraft/config/true-hold-sprint.json
 *
 * 改完重启游戏生效。
 */
public class ModConfig {

    /** 总开关。false 时 mod 完全不介入，行为等同原版。 */
    public boolean enabled = true;

    /**
     * 是否要求「必须按着移动键」才疾跑，默认 true。
     *
     * 移动键 = W / A / S / D 任意一个，前后左右都算 —— 也就是全向疾跑。
     * 关掉的话，单按疾跑键（不按任何移动键）也会进入疾跑状态。
     */
    public boolean requireMovement = true;

    /**
     * 是否允许后退时疾跑，默认 true。
     *
     * 原版只允许向前疾跑（按 W）。关掉这一项会退回原版行为：
     * 按 A / D 侧移可以疾跑，但按 S 后退不行。
     */
    public boolean allowBackwardSprint = true;

    /**
     * 修正疾跑跳跃的方向，默认 true。
     *
     * 原版的疾跑跳跃加速硬编码用了**视角朝向**（它假定疾跑 = 向前），
     * 于是侧移 / 后退时按跳跃会往正前方窜一下。
     *
     * 打开这一项会把那脚加速转到**实际移动方向**上，大小不变。
     * 向前跑时两者重合，等于零改动，原版手感完全不动。
     */
    public boolean fixSprintJumpDirection = true;

    /**
     * 当「疾跑键」和「潜行键」被绑到同一个物理键时，按住时临时屏蔽潜行，默认 true。
     *
     * 蹲下会取消疾跑，所以两个键撞在一起时需要屏蔽一下。
     * 分开绑时完全用不到 —— 按疾跑键根本碰不到潜行键，潜行照常工作。
     */
    public boolean suppressSneakWhileSprinting = true;

    /**
     * 维持这一帧内的疾跑状态，默认 true。
     *
     * 原版只允许向前疾跑，它会在每帧中途检查「是不是还在往前进」，
     * 不是就把疾跑关掉。结果侧移/后退时：视野拉伸有（渲染时才采样，那时我们已修正），
     * 但速度没加成（移动计算在中途，读到的是已取消的状态）。
     *
     * 打开这一项会拦住原版这种中途取消，让速度也跟着生效。
     * 关掉则回到「只有视野变、速度不变」的状态。
     */
    public boolean keepSprintDuringTick = true;

    /**
     * 松开疾跑键立刻停止疾跑，默认 true（本 mod 的核心功能）。
     * 关掉的话就只剩「全向能起跑」，停不停由原版说了算。
     */
    public boolean stopSprintOnRelease = true;

    /** 是否在日志里打印每帧的判定结果，排障用。 */
    public boolean debugLog = false;

    /** 【D】原先包级私有而类是 public，API 不一致；改为 public。 */
    public static ModConfig createDefault() {
        return new ModConfig();
    }

    /** 当前生效的配置（由 TrueHoldSprint 在启动时载入）。 */
    public static ModConfig get() {
        return TrueHoldSprint.get();
    }
}
