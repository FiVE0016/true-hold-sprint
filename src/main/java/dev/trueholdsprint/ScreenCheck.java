package dev.trueholdsprint;

import net.minecraft.client.Minecraft;

/**
 * 判断当前有没有打开任何界面（背包、箱子、暂停菜单等）。
 *
 * 【2026-10-11 修复（A1）】
 * 旧版用反射在 {@link Minecraft} 上找 {@code screen} / {@code currentScreen}，两个名字都不存在：
 * 26.3 里当前屏幕在 {@code Minecraft.gui}（{@code public final Gui gui}）里的
 * {@code Gui.screen()}。反射两个名字都查不到 → 永远返回 false →
 * 这个类形同虚设，开着背包时 mod 依然每帧改按键状态。
 *
 * 26.1 起 Minecraft 已不再混淆，直接调 API 即可，反射在这里没有任何必要，
 * 所以整个类的反射代码都删掉了（也就不会再出现"名字猜错 = 静默失效"）。
 */
public final class ScreenCheck {

    private ScreenCheck() {
    }

    /** @return 当前是否有界面打开；拿不到信息时保守返回 false（不阻断 mod）。 */
    public static boolean isScreenOpen(Minecraft mc) {
        if (mc == null) {
            return false;
        }
        try {
            // Gui 在极早期可能还没建好，做个判空
            return mc.gui != null && mc.gui.screen() != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
