package dev.trueholdsprint.mixin;

import dev.trueholdsprint.SprintState;
import dev.trueholdsprint.TrueHoldSprint;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 两件杂事，都挂在客户端主循环上。
 *
 * ── 一、启动自检（A5）──────────────────────────────
 * 本 mod 所有 {@code @Inject} 都写了 {@code require = 0}，好处是版本不匹配时不会崩，
 * 坏处是<b>可能整个 mod 变成空操作</b>：日志干净、游戏正常，就是"没效果"，
 * 玩家只会觉得这 mod 没用。
 *
 * 所以这里做个看门狗：进世界约 5 秒后检查关键注入是否真的跑过，
 * 没跑过就<b>大声报错</b>并说明可能的原因，而不是继续假装正常工作。
 *
 * ── 二、换世界时清状态（B2）──────────────────────────
 * SprintState 里是"这一帧的临时约定"，跨局残留会让下一局的第一帧行为诡异。
 */
@Mixin(Minecraft.class)
public class MinecraftMixin {

    /** 进世界后等这么多 tick（约 5 秒）再自检，避免过早误报。 */
    private static final int WATCHDOG_DELAY_TICKS = 100;

    @Inject(method = "tick", at = @At("TAIL"))
    private void trueholdsprint$tickTail(CallbackInfo ci) {
        try {
            Minecraft mc = (Minecraft) (Object) this;

            // 不在世界里：清掉每帧状态，别把上一局的东西带进下一局
            if (mc.player == null || mc.level == null) {
                SprintState.reset();
                SprintState.watchdogTicks = 0;
                return;
            }

            if (SprintState.watchdogDone) {
                return;
            }
            if (++SprintState.watchdogTicks < WATCHDOG_DELAY_TICKS) {
                return;
            }
            SprintState.watchdogDone = true;

            boolean accessorOk = mc.options != null
                    && mc.options.keySprint instanceof KeyMappingAccessor;

            if (!SprintState.localInjected || !SprintState.livingInjected || !accessorOk) {
                TrueHoldSprint.LOGGER.error("""
                        [真正的按住疾跑] ⚠ 启动自检未通过：注入没有全部生效，本 mod 现在是【空操作】！
                           LocalPlayerMixin 生效: {}
                           LivingEntityMixin 生效: {}
                           KeyMapping 访问器生效: {}
                        常见原因：Minecraft 版本与 mod 不匹配（本 mod 针对 26.3 编译）。
                        请核对 logs/latest.log 里是否有 mixin 应用失败的警告，或换用对应版本的 mod。""",
                        SprintState.localInjected, SprintState.livingInjected, accessorOk);
            } else {
                TrueHoldSprint.LOGGER.info("[真正的按住疾跑] 启动自检通过：注入已全部生效（按 {} 疾跑，松开即停）。",
                        dev.trueholdsprint.BoundKey.describe(mc.options.keySprint));
            }
        } catch (Throwable t) {
            // 看门狗自己绝不能添乱
            TrueHoldSprint.warnOnce("watchdog", "[真正的按住疾跑] 自检逻辑出错（不影响游戏）。", t);
        }
    }
}
