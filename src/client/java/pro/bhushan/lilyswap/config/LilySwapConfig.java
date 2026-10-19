package pro.bhushan.lilyswap.config;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Stored in config/lilyswap.json. */
public final class LilySwapConfig {
	public static final int MIN_DELAY = 0;
	public static final int MAX_DELAY = 20;
	private static final Logger LOGGER = LoggerFactory.getLogger("lilyswap");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static LilySwapConfig instance;

	/** Master switch. Off means lily pads place like vanilla. */
	public boolean enabled = true;

	/** Ticks between the lily pad appearing and the swap being sent (1 tick = 50 ms). */
	public int delayTicks = 0;

	public static LilySwapConfig get() {
		if (instance == null) instance = load();
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("lilyswap.json");
	}

	private static LilySwapConfig load() {
		Path path = path();
		LilySwapConfig config = null;
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				config = GSON.fromJson(reader, LilySwapConfig.class);
			} catch (Exception e) {
				LOGGER.warn("Could not read {}, using defaults", path, e);
			}
		}
		if (config == null) config = new LilySwapConfig();
		config.delayTicks = clamp(config.delayTicks);
		return config;
	}

	public static int clamp(int ticks) {
		return Math.max(MIN_DELAY, Math.min(MAX_DELAY, ticks));
	}

	public void save() {
		delayTicks = clamp(delayTicks);
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(this, writer);
			}
		} catch (Exception e) {
			LOGGER.warn("Could not write {}", path, e);
		}
	}
}
