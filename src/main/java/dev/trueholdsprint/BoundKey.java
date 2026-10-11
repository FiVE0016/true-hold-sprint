package dev.trueholdsprint;

import com.mojang.blaze3d.platform.InputConstants;
import dev.trueholdsprint.mixin.KeyMappingAccessor;
import net.minecraft.client.KeyMapping;

/**
 * 读取一个按键绑定**实际绑在哪个物理键**上。
 *
 * 为什么需要它：本 mod 早先把「潜行键」直接当成疾跑触发键，
 * 于是玩家一旦把潜行改绑到别的键（比如 V），按 V 就会触发疾跑。
 * 有了这个就能判断「疾跑键和潜行键到底是不是同一个物理键」。
 *
 * 【2026-10-11 修复（A2 + A3）】
 * • A2：不再全反射，改用 {@link KeyMappingAccessor}（Mixin 访问器）。
 * • A3：新增 {@link #samePhysicalKey}，比较时<b>同时比键码和设备类型</b>。
 *   旧版只比 getValue()：键盘的 1 和鼠标左键的 1 都是 1，会被误判成"同一个键"，
 *   于是错误地屏蔽了潜行。键码要配合 {@link InputConstants.Type} 看才有意义。
 */
public final class BoundKey {

    private BoundKey() {
    }

    /** @return 该绑定当前绑定的物理键；读不到返回 null。 */
    public static InputConstants.Key of(KeyMapping mapping) {
        if (mapping == null) {
            return null;
        }
        try {
            if (mapping instanceof KeyMappingAccessor accessor) {
                return accessor.getKey();
            }
        } catch (Throwable ignored) {
            // 访问器没挂上（版本不匹配）时返回 null，调用方按保守方式处理
        }
        return null;
    }

    /** @return GLFW 键码；读不到返回 -1（日志用）。 */
    public static int value(KeyMapping mapping) {
        InputConstants.Key key = of(mapping);
        return key == null ? -1 : key.getValue();
    }

    /**
     * 两个绑定是否真的绑在**同一个物理键**上。
     *
     * 必须同时比较 {@code getValue()} 与 {@code getType()}：
     * 键盘 KEYSYM 的 1（比如数字键 1）与鼠标 MOUSE 的 1（左键）值相同但完全不是同一个键。
     */
    public static boolean samePhysicalKey(KeyMapping a, KeyMapping b) {
        InputConstants.Key ka = of(a);
        InputConstants.Key kb = of(b);
        if (ka == null || kb == null) {
            return false; // 读不到就不做任何"同键"假设，保守放行潜行
        }
        return ka.getValue() == kb.getValue() && ka.getType() == kb.getType();
    }

    /** @return 形如 {@code 341(KEYSYM)} 的可读描述；读不到返回 {@code 读取失败}。 */
    public static String describe(KeyMapping mapping) {
        InputConstants.Key key = of(mapping);
        return key == null ? "读取失败" : key.getValue() + "(" + key.getType() + ")";
    }
}
