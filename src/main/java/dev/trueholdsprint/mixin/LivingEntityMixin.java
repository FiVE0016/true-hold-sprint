package dev.trueholdsprint.mixin;

import dev.trueholdsprint.ModConfig;
import dev.trueholdsprint.SprintState;
import dev.trueholdsprint.TrueHoldSprint;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 两个互不相关的修正，都挂在 LivingEntity 上。
 *
 * ── 一、拦住原版在**这一帧中途**把疾跑取消掉 ──────────────
 * 现象：全向疾跑时，四个方向**都有**视野拉伸，但只有向前速度真的变快。
 *
 * 原因是「视野」和「速度」读的是不同时刻的状态：
 *   • 视野（FOV）在**渲染阶段**采样，也就是这一帧结束之后
 *     → 看到的是我们帧尾断言过的 isSprinting() == true → 拉伸 ✓
 *   • 速度在这一帧**中途的移动计算**里用掉
 *     → 那时原版已经把疾跑取消了 → 读到 false → 没加成 ✗
 *
 * 修法：在 setSprinting 上拦 —— 这一帧我们决定要疾跑，而原版想置 false，就取消这次调用。
 * 只拦「置 false」、只拦本地玩家。
 *
 * ── 二、修正疾跑跳跃的方向 ──────────────────────────
 * 现象：全向疾跑时，无论朝哪跑，按跳跃总是往**视角正前方**窜一下。
 * 修法：让原版照常加那脚加速，然后把增量从「朝向」旋转到「实际移动方向」，大小不变。
 *
 * 【2026-10-11 修复】
 * • B4：新增**姿态让路** —— 趴下（TACZ 爬行用 SWIMMING 姿态）/ 游泳 / 鞘翅 / 骑乘 / 死亡时
 *   一律放行原版的 setSprinting(false)。旧版会把**任何**想停疾跑的调用都拦下来，
 *   于是趴下、上船、落地等情形可能出现"趴着还在疾跑"这类状态打架。
 * • A6：两处空的 catch 补上日志（带一次去重，不会刷屏）。
 * • A5：置 SprintState.livingInjected，供启动自检。
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    // ══ 一、帧内维持疾跑 ══

    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true, require = 0)
    private void trueholdsprint$keepSprintDuringTick(boolean value, CallbackInfo ci) {
        try {
            SprintState.livingInjected = true;

            // 要开启疾跑 → 放行
            if (value) {
                return;
            }
            // 这一帧我们没在维持疾跑 → 放行（该停就停）
            if (!SprintState.wantSprint) {
                return;
            }
            ModConfig c = ModConfig.get();
            if (c == null || !c.enabled || !c.keepSprintDuringTick) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player != (Object) this) {
                return;
            }
            // 【B4】姿态不对（趴下/游泳/鞘翅/骑乘/死亡）→ 放行，原版该停就停
            if (!SprintState.canTakeOver((LivingEntity) (Object) this)) {
                return;
            }
            ci.cancel();
        } catch (Throwable t) {
            // 兜底：抛异常会让原版崩 —— 只放过这次调用，但要留下线索（A6：不再静默）
            TrueHoldSprint.warnOnce("keepSprint", "[真正的按住疾跑] 维持疾跑时出错，本次放行。", t);
        }
    }

    // ══ 二、跳跃方向修正 ══

    @Inject(method = "jumpFromGround", at = @At("HEAD"), require = 0)
    private void trueholdsprint$jumpHead(CallbackInfo ci) {
        SprintState.jumpActive = false;
        try {
            ModConfig c = ModConfig.get();
            if (c == null || !c.enabled || !c.fixSprintJumpDirection) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player != (Object) this) {
                return;
            }
            // 只在确实处于疾跑时才需要处理（原版那段加速也只在疾跑时加）
            if (!SprintState.wantSprint) {
                return;
            }

            Entity self = (Entity) (Object) this;
            // 用 .x 直接取字段，避免 import 具体向量类型（名字在不同版本间变过）
            double vx = self.getDeltaMovement().x;
            double vz = self.getDeltaMovement().z;
            double speed = Math.sqrt(vx * vx + vz * vz);
            if (speed < 1.0e-4) {
                return; // 站着不动，没有「实际移动方向」可言，交回原版
            }

            SprintState.jumpPrevX = vx;
            SprintState.jumpPrevZ = vz;
            SprintState.jumpDirX = vx / speed;
            SprintState.jumpDirZ = vz / speed;
            SprintState.jumpActive = true;
            TrueHoldSprint.logJumpOnce();
        } catch (Throwable t) {
            SprintState.jumpActive = false;
            TrueHoldSprint.warnOnce("jumpHead", "[真正的按住疾跑] 跳跃方向修正（起跳前）出错，已跳过。", t);
        }
    }

    @Inject(method = "jumpFromGround", at = @At("TAIL"), require = 0)
    private void trueholdsprint$jumpTail(CallbackInfo ci) {
        if (!SprintState.jumpActive) {
            return;
        }
        SprintState.jumpActive = false;
        try {
            Entity self = (Entity) (Object) this;
            double vx = self.getDeltaMovement().x;
            double vy = self.getDeltaMovement().y;
            double vz = self.getDeltaMovement().z;

            // 起跳过程中水平速度的变化量 —— 这就是原版那脚「朝向」加速
            double dx = vx - SprintState.jumpPrevX;
            double dz = vz - SprintState.jumpPrevZ;
            double mag = Math.sqrt(dx * dx + dz * dz);
            if (mag < 1.0e-6) {
                return; // 原版没加（比如没在疾跑），不动
            }

            // 减去朝向量，加上同样大小的移动向量
            self.setDeltaMovement(
                    vx - dx + SprintState.jumpDirX * mag,
                    vy,
                    vz - dz + SprintState.jumpDirZ * mag);
        } catch (Throwable t) {
            // 兜底，不改就不会更糟（A6：但要说出来）
            TrueHoldSprint.warnOnce("jumpTail", "[真正的按住疾跑] 跳跃方向修正（起跳后）出错，已保持原版行为。", t);
        }
    }
}
