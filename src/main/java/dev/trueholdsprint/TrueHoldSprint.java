package dev.trueholdsprint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TrueHoldSprint implements ClientModInitializer {

    public static final String MOD_ID = "true-hold-sprint";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static ModConfig config = ModConfig.createDefault();
    private static Path configPath;

    public static ModConfig get() {
        return config;
    }

    @Override
    public void onInitializeClient() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json");
        loadConfig();
        LOGGER.info("[真正的按住疾跑] 已加载配置 {}", configPath);
        LOGGER.info("[真正的按住疾跑] enabled={}, requireMovement={}, allowBackwardSprint={}, keepSprintDuringTick={}, fixSprintJumpDirection={}, suppressSneakWhileSprinting={}, stopSprintOnRelease={}, debugLog={}",
                config.enabled, config.requireMovement, config.allowBackwardSprint, config.keepSprintDuringTick,
                config.fixSprintJumpDirection, config.suppressSneakWhileSprinting,
                config.stopSprintOnRelease, config.debugLog);
    }

    /**
     * 同一个位置的异常只完整打一次（之后降到 debug）。
     *
     * 【A6】旧版有两处 catch 是空的，出问题时日志里一点线索都没有。
     * 但这些代码每帧都在跑，直接 {@code LOGGER.warn} 会刷屏，所以按 key 去重。
     */
    private static final Set<String> warned = ConcurrentHashMap.newKeySet();

    public static void warnOnce(String key, String message, Throwable t) {
        if (warned.add(key)) {
            LOGGER.warn(message, t);
        } else {
            LOGGER.debug(message, t);
        }
    }

    /** debugLog 打开时，状态翻转才打一条，避免刷屏。 */
    public static void logFrame(boolean up, boolean down, boolean left, boolean right,
                                boolean sprintDown, boolean sneakDown, boolean want,
                                boolean actuallySprinting, String sprintBoundKey, String sneakBoundKey) {
        if (!config.debugLog) {
            return;
        }
        // 【D】旧版把布尔编码成 1/2 再比较，可读性差；直接存布尔即可
        if (logged && lastWant == want && lastSprinting == actuallySprinting) {
            return;
        }
        logged = true;
        lastWant = want;
        lastSprinting = actuallySprinting;

        StringBuilder dir = new StringBuilder();
        if (up) dir.append("W");
        if (down) dir.append("S");
        if (left) dir.append("A");
        if (right) dir.append("D");
        if (dir.length() == 0) dir.append("无");

        LOGGER.info("[真正的按住疾跑] 移动={} 疾跑键={} 潜行键={} → 期望疾跑={} / 实际疾跑={}（疾跑键={} 潜行键={}）",
                dir,
                sprintDown ? "按下" : "松开",
                sneakDown ? "按下" : "松开",
                want ? "是" : "否",
                actuallySprinting ? "是（视野拉伸应已生效）" : "否",
                sprintBoundKey,
                sneakBoundKey);
    }

    private static boolean logged = false;
    private static boolean lastWant = false;
    private static boolean lastSprinting = false;

    /** 只打一次：确认「跳跃方向修正」这个注入点真的挂上了。 */
    public static void logJumpOnce() {
        if (jumpLogged) {
            return;
        }
        jumpLogged = true;
        LOGGER.info("[真正的按住疾跑] 跳跃方向修正已挂载（jumpFromGround 注入成功）");
    }

    private static boolean jumpLogged = false;

    public static void loadConfig() {
        config = ModConfig.createDefault();
        if (configPath == null || !Files.exists(configPath)) {
            saveConfig();
            return;
        }
        boolean loadedOk = false;
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
            if (loaded != null) {
                config = loaded;
                loadedOk = true;
            }
        } catch (Exception e) {
            // 【D】旧版用 e.toString() 把栈丢了，出问题查不出来
            LOGGER.warn("[真正的按住疾跑] 读配置失败，先用默认值：{}", configPath, e);
        }
        if (loadedOk) {
            // 【D】回写一次：字段增删后配置文件不会与代码长期漂移（新字段会被补进文件）
            saveConfig();
        }
    }

    public static void saveConfig() {
        if (configPath == null) {
            return;
        }
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            LOGGER.warn("[真正的按住疾跑] 写配置失败：{}", configPath, e);
        }
    }
}
