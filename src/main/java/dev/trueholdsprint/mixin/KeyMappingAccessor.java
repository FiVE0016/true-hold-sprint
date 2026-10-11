package dev.trueholdsprint.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取 {@link KeyMapping} 里当前绑定的物理键。
 *
 * 【2026-10-11 修复（A2）】
 * 旧版 BoundKey 全反射，而且主路径 {@code getMethod("getKey")} 在 26.3 上<b>必然失败</b>
 * （KeyMapping 没有 getKey()，只有 {@code protected InputConstants.Key key} 字段），
 * 每次都走 setAccessible 的退路。
 *
 * 用 Mixin 访问器替代：编译期类型安全、不依赖名字猜测、也不会被模块系统拦。
 * 真正的 GameProfile 字段名 {@code key} 由 Mixin 在运行期核对，写错会立刻报错而不是静默失效。
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {

    @Accessor("key")
    InputConstants.Key getKey();
}
