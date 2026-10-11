package dev.trueholdsprint;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

/**
 * 每帧的状态 + 自检标记。
 *
 * ⚠️ 不能把这些字段写在 mixin 类里：mixin 类会被合并进目标类（LocalPlayer / LivingEntity），
 * 静态字段就变成目标类的字段了，既污染又不安全。所以单独放在主包下。
 *
 * 【2026-10-11 修复（B2 + A5）】
 * • 新增 {@link #reset()}：退出世界/切换世界时清掉每帧状态，
 *   避免上一局的 wantSprint / weStarted 泄漏到下一局（B2 的"没有重置时机"）。
 * • 新增三个 injected 标记与 {@link #watchdogDone}：给 MinecraftMixin 里的启动自检用（A5）。
 */
public final class SprintState {

    /** 这一帧我们是否把潜行键临时按住了（帧尾要还原）。 */
    public static boolean shiftSuppressed = false;

    /** 这一帧我们是否接管了原版疾跑键（帧尾要还原）。 */
    public static boolean sprintKeyForced = false;

    /** 接管前，原版疾跑键真实的按下状态（帧尾按原样还原）。 */
    public static boolean origSprintDown = false;

    /** 这一帧我们希望的最终疾跑状态。 */
    public static boolean wantSprint = false;

    /** 这次疾跑是不是我们引起的 —— 用来避免误杀原版双击 W 的疾跑。 */
    public static boolean weStarted = false;

    // ── 跳跃方向修正用 ──────────────────────────────

    /** 这次跳跃是否要做方向修正。 */
    public static boolean jumpActive = false;

    /** 起跳前的水平速度（用来算实际移动方向）。 */
    public static double jumpPrevX = 0.0;
    public static double jumpPrevZ = 0.0;

    /** 起跳前的移动方向单位向量。 */
    public static double jumpDirX = 0.0;
    public static double jumpDirZ = 0.0;

    // ── 启动自检（A5）──────────────────────────────

    /** LocalPlayerMixin 的注入真的跑过。 */
    public static boolean localInjected = false;

    /** LivingEntityMixin 的注入真的跑过。 */
    public static boolean livingInjected = false;

    /** 自检已经报告过（只报一次）。 */
    public static boolean watchdogDone = false;

    /** 进世界后累计的 tick 数，用于延迟自检。 */
    public static int watchdogTicks = 0;

    private SprintState() {
    }

    /**
     * 清掉"每帧状态"。
     *
     * 在退出世界 / 切换世界时调用（见 MinecraftMixin）：这些状态是"这一帧的临时约定"，
     * 跨局残留会让下一局的第一帧行为诡异（比如 weStarted 还是 true → 拦截器不让停疾跑）。
     *
     * 注意<b>不</b>清 injected / watchdogDone —— 那是"注入是否挂上"的永久事实。
     */
    public static void reset() {
        shiftSuppressed = false;
        sprintKeyForced = false;
        origSprintDown = false;
        wantSprint = false;
        weStarted = false;
        jumpActive = false;
        jumpPrevX = 0.0;
        jumpPrevZ = 0.0;
        jumpDirX = 0.0;
        jumpDirZ = 0.0;
    }

    /**
     * 当前是否处于"可以接管疾跑"的姿态。
     *
     * 【B4】趴下（TACZ 的爬行会用 SWIMMING 姿态）、游泳、鞘翅飞行、骑乘、死亡时一律不接管：
     * 这些状态下"停疾跑"是必须的，强行维持会出现"趴着还在疾跑"这类状态打架。
     */
    public static boolean canTakeOver(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        return entity.getPose() == Pose.STANDING
                && !entity.isFallFlying()
                && !entity.isPassenger()
                && !entity.isDeadOrDying();
    }
}
