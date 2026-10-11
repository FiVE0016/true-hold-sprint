package dev.trueholdsprint.mixin;

import dev.trueholdsprint.BoundKey;
import dev.trueholdsprint.ModConfig;
import dev.trueholdsprint.ScreenCheck;
import dev.trueholdsprint.SprintState;
import dev.trueholdsprint.TrueHoldSprint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让疾跑严格跟随「疾跑键 + 移动键」，全向生效（W / A / S / D 都行）。
 *
 * 做法：
 *   帧首（aiStep HEAD）
 *     1. 读按键绑定的状态：keyUp/keyDown/keyLeft/keyRight（WASD）、keyShift、keySprint
 *     2. 需要时临时把潜行置为松开（仅当疾跑键与潜行键是同一个物理键）
 *     3. **把原版疾跑键也置为按下** ← 恢复视野拉伸的关键：让原版自己走一遍
 *        「给速度属性加疾跑修正 + FOV + 发包」的原生流程
 *     4. 直接 setSprinting()，保证结果
 *   帧尾（aiStep TAIL）
 *     1. 还原被我们改过的按键状态
 *     2. **再断言一次疾跑状态** —— 渲染与 FOV 采样发生在本帧之后
 *
 * 【2026-10-11 修复】
 * • A4/B3：<b>删掉了挂在 {@code tick} 上的那一对注入</b>。
 *   LocalPlayer.tick() 内部会调用 aiStep()，所以原来每 tick 实际执行两遍 head()：
 *   第二遍读到的 vanillaSprint 已经是第一遍伪造的值，于是 origSprintDown 被污染，
 *   "帧尾还原成真实状态"的语义被破坏。现在只挂 aiStep，每 tick 恰好一遍。
 * • A1：ScreenCheck 修好后，开着界面时那道门才真正拦得住。
 * • A3：改用 BoundKey.samePhysicalKey（键码 + 设备类型），不再把"键盘 1"和"鼠标左键"当成同一个键。
 * • B4：趴下 / 游泳 / 鞘翅 / 骑乘 / 死亡时不接管，避免与 TACZ 爬行等状态打架。
 * • A5：每次跑起来都置 SprintState.localInjected，供启动自检判断注入是否真的生效。
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Inject(method = "aiStep", at = @At("HEAD"), require = 0)
    private void trueholdsprint$headAiStep(CallbackInfo ci) {
        head();
    }

    @Inject(method = "aiStep", at = @At("TAIL"), require = 0)
    private void trueholdsprint$tailAiStep(CallbackInfo ci) {
        tail();
    }

    private void head() {
        try {
            SprintState.localInjected = true;

            Minecraft mc = Minecraft.getInstance();
            ModConfig c = ModConfig.get();
            if (mc == null || c == null || !c.enabled) {
                return;
            }
            // 只处理本地玩家；在世界里、没开界面时才接管
            if (mc.player != (Object) this || mc.level == null || mc.options == null) {
                return;
            }
            if (ScreenCheck.isScreenOpen(mc)) {
                return;
            }

            LocalPlayer self = (LocalPlayer) (Object) this;

            // 【B4】姿态不对（趴下/游泳/鞘翅/骑乘/死亡）就不接管：
            // 先清掉 wantSprint，让 LivingEntityMixin 的拦截器放行，原版该停疾跑就停。
            if (!SprintState.canTakeOver(self)) {
                SprintState.wantSprint = false;
                return;
            }

            // 全向移动：W / A / S / D 任意一个按着都算在移动
            boolean up = mc.options.keyUp.isDown();
            boolean down = mc.options.keyDown.isDown();
            boolean left = mc.options.keyLeft.isDown();
            boolean right = mc.options.keyRight.isDown();
            boolean sneakDown = mc.options.keyShift.isDown();
            boolean vanillaSprint = mc.options.keySprint.isDown();

            // 疾跑只由「原版疾跑键」触发 —— 也就是你在游戏按键绑定里给「疾跑」设的那个键。
            // 潜行键完全不参与触发，它该怎么潜行就怎么潜行。
            boolean trigger = vanillaSprint;

            // 没按疾跑键、而且这次疾跑也不是我们引起的 → 完全不插手
            // （这样原版双击 W 的疾跑能完整保留）
            if (!trigger && !SprintState.weStarted) {
                // 防御：确保不会残留 wantSprint=true，否则 LivingEntityMixin 会一直拦着不让停
                SprintState.wantSprint = false;
                return;
            }

            boolean moving = up || left || right || (down && c.allowBackwardSprint);
            boolean want = trigger && (!c.requireMovement || moving);
            SprintState.wantSprint = want;
            SprintState.origSprintDown = vanillaSprint;

            // 只有在「疾跑键和潜行键被绑到了同一个物理键」时，才临时屏蔽潜行。
            // 比较用 samePhysicalKey（键码 + 设备类型），不会把键盘 1 和鼠标左键混为一谈。
            if (want && sneakDown && c.suppressSneakWhileSprinting
                    && BoundKey.samePhysicalKey(mc.options.keySprint, mc.options.keyShift)) {
                mc.options.keyShift.setDown(false);
                SprintState.shiftSuppressed = true;
            }

            // 让原版自己也以为疾跑键按下了 → 速度 + FOV + 发包全是原版原生
            mc.options.keySprint.setDown(want);
            SprintState.sprintKeyForced = true;

            // 直接定结果，双保险
            self.setSprinting(want);
            SprintState.weStarted = want;

            if (c.debugLog) {
                TrueHoldSprint.logFrame(up, down, left, right, vanillaSprint, sneakDown,
                        want, self.isSprinting(),
                        BoundKey.describe(mc.options.keySprint), BoundKey.describe(mc.options.keyShift));
            }
        } catch (Throwable t) {
            TrueHoldSprint.warnOnce("head", "[真正的按住疾跑] 帧首逻辑出错，已跳过（游戏不会崩）。", t);
        }
    }

    private void tail() {
        try {
            SprintState.localInjected = true;

            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player != (Object) this || mc.options == null) {
                return;
            }
            if (!SprintState.sprintKeyForced && !SprintState.shiftSuppressed && !SprintState.weStarted) {
                return;
            }

            // 还原被我们临时改过的按键状态
            if (SprintState.shiftSuppressed) {
                mc.options.keyShift.setDown(true);
                SprintState.shiftSuppressed = false;
            }
            // 还原成接管前的真实状态，而不是无脑 false —— 否则玩家真的按着 Ctrl 时会被我们抹掉
            if (SprintState.sprintKeyForced) {
                mc.options.keySprint.setDown(SprintState.origSprintDown);
                SprintState.sprintKeyForced = false;
            }

            // 关键：渲染和 FOV 采样在这一帧之后，这里再断言一次，
            // 防止中间被原版覆盖导致视野拉伸丢失
            if (ModConfig.get().stopSprintOnRelease || SprintState.wantSprint) {
                LocalPlayer self = (LocalPlayer) (Object) this;
                if (self.isSprinting() != SprintState.wantSprint) {
                    self.setSprinting(SprintState.wantSprint);
                }
            }
        } catch (Throwable t) {
            TrueHoldSprint.warnOnce("tail", "[真正的按住疾跑] 帧尾逻辑出错，已跳过（游戏不会崩）。", t);
        }
    }
}
