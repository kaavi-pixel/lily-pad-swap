package pro.bhushan.lilyswap.test;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import pro.bhushan.lilyswap.config.LilySwapConfig;
import pro.bhushan.lilyswap.config.LilySwapConfigScreen;
import pro.bhushan.lilyswap.config.LilySwapModMenu;

@SuppressWarnings("UnstableApiUsage")
public class LilySwapClientGameTest implements FabricClientGameTest {
	private static volatile BlockPos watched;
	private static final List<String> timeline = new ArrayList<>();
	private static final List<String> results = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			BlockPos p = watched;
			if (p != null && mc.level != null) {
				synchronized (timeline) {
					timeline.add(BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(p).getBlock()).getPath());
				}
			}
		});

		ctx.runOnClient(mc -> mc.gui.setScreen(new LilySwapModMenu().getModConfigScreenFactory().create(null)));
		ctx.waitTicks(5);
		ctx.takeScreenshot("lilyswap-config");
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_TAB);
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN);
		ctx.waitTicks(2);
		ctx.takeScreenshot("lilyswap-config-off");
		ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_TAB);
		for (int i = 0; i < 7; i++) ctx.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT);
		ctx.waitTicks(2);
		ctx.takeScreenshot("lilyswap-config-7");
		ctx.runOnClient(mc -> mc.gui.screen().onClose());
		ctx.waitTicks(2);
		try {
			results.add("toggle+slider: enabled=" + LilySwapConfig.get().enabled + " delayTicks=" + LilySwapConfig.get().delayTicks + " file=" + java.nio.file.Files.readString(
				net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lilyswap.json")).replaceAll("\\s+", " "));
		} catch (java.io.IOException e) {
			throw new RuntimeException(e);
		}
		results.add("config screen opened via ModMenu factory; file exists=" + java.nio.file.Files.exists(
			net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("lilyswap.json")));

		LilySwapConfig.get().enabled = true;

		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();
			setDelay(0);
			scenarios(ctx, sp.getServer(), sp.getConnection(), "singleplayer d0");
			setDelay(5);
			scenarios(ctx, sp.getServer(), sp.getConnection(), "singleplayer d5");
			delayScenarios(ctx, sp.getServer(), sp.getConnection(), "singleplayer d5");
			setDelay(10);
			holdTest(ctx, sp.getServer(), sp.getConnection(), "singleplayer d10 D hold");
			setDelay(0);
		}

		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer();
			 TestDedicatedServerConnection conn = server.connect()) {
			conn.waitForChunksRender();
			setDelay(0);
			scenarios(ctx, server, conn, "dedicated d0");
			setDelay(5);
			scenarios(ctx, server, conn, "dedicated d5");
			delayScenarios(ctx, server, conn, "dedicated d5");
			setDelay(0);
		}

		System.out.println("LILYSWAP-RESULTS-BEGIN");
		results.forEach(r -> System.out.println("LILYSWAP " + r));
		System.out.println("LILYSWAP-RESULTS-END");
	}

	private static void setDelay(int ticks) {
		LilySwapConfig.get().delayTicks = ticks;
	}

	/** Actions during the delay window that must not break the swap-before-use order. */
	private static void delayScenarios(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn, String label) {
		BlockPos water = new BlockPos(102, 149, 100);
		BlockPos pad = water.above();
		// I: scroll hotbar 2 ticks into the delay.
		setup(ctx, server, conn, 100.5, 100.5, "lily_pad", 16);
		server.runCommand("item replace entity @a hotbar.1 with stick 1");
		ctx.waitTicks(3);
		windowTest(ctx, server, conn, label + " I hotbar change mid-delay", water, () -> ctx.getInput().pressKey(o -> o.keyHotbarSlots[1]));
		// J: second right click 2 ticks into the delay.
		setup(ctx, server, conn, 100.5, 100.5, "lily_pad", 16);
		windowTest(ctx, server, conn, label + " J second click mid-delay", water, () -> ctx.getInput().pressKey(o -> o.keyUse));
		// K: press F 2 ticks into the delay.
		setup(ctx, server, conn, 100.5, 100.5, "lily_pad", 16);
		windowTest(ctx, server, conn, label + " K F mid-delay", water, () -> ctx.getInput().pressKey(o -> o.keySwapOffhand));
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
	}

	private static void windowTest(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn, String name, BlockPos water, Runnable midAction) {
		BlockPos pad = water.above();
		ctx.getInput().lookAt(water);
		ctx.waitTicks(2);
		synchronized (timeline) { timeline.clear(); }
		watched = pad;
		ctx.getInput().pressKey(o -> o.keyUse);
		ctx.waitTicks(1);
		String mid = "client[" + clientHands(ctx) + "] server[" + serverHands(conn, server) + "]";
		midAction.run();
		ctx.waitTicks(20);
		conn.waitForClientboundPackets();
		ctx.waitTicks(5);
		watched = null;
		String tl;
		synchronized (timeline) { tl = String.join(",", timeline.subList(0, Math.min(16, timeline.size()))); }
		String inv = server.computeOnServer(s -> {
			int lily = 0;
			var inventory = conn.getServerPlayer().getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				if (inventory.getItem(i).is(net.minecraft.world.item.Items.LILY_PAD)) lily += inventory.getItem(i).getCount();
			}
			return "serverLilyTotal=" + lily + " selected=" + inventory.getSelectedSlot();
		});
		results.add(name + " | mid " + mid + " | after client[" + clientHands(ctx) + "] server[" + serverHands(conn, server) + "] " + inv
			+ " | clientTicks " + tl + " | serverBlock " + serverBlock(server, conn, pad) + " | clientBlockNow " + clientBlock(ctx, pad));
		server.runCommand("setblock " + pad.getX() + " " + pad.getY() + " " + pad.getZ() + " air");
		ctx.waitTicks(3);
	}

	private static void scenarios(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn, String label) {
		server.runCommand("gamerule spawn_mobs false");
		server.runCommand("gamemode survival @a");
		server.runCommand("difficulty peaceful");
		// Shallow pool with platform: walls/floor stone, water 101..104 x 98..102 at y149.
		server.runCommand("fill 98 148 96 107 149 104 stone");
		server.runCommand("fill 101 149 98 104 149 102 water");
		server.runCommand("setblock 100 149 102 ice");
		// Deep pool: water column y121..149, floor far out of reach.
		server.runCommand("forceload add 90 90 130 115");
		server.runCommand("fill 112 120 96 121 149 104 stone");
		server.runCommand("fill 115 121 98 118 149 102 water");
		ctx.waitTicks(10);

		// A: shallow water, lily in main hand, offhand empty (crosshair hits pool floor: useItemOn then useItem path).
		lilyClick(ctx, server, conn, label + " A shallow main->off", 100.5, 100.5, new BlockPos(102, 149, 100), "main");
		// B: click again: lily now in offhand, main empty. Should glitch again and come back to main.
		lilyClickNoSetup(ctx, server, conn, label + " B shallow off->main", new BlockPos(102, 149, 100), "off");
		// C: deep water (crosshair MISS path).
		lilyClick(ctx, server, conn, label + " C deep main->off", 114.5, 100.5, new BlockPos(116, 149, 100), "main");
		// D: hold right click 21 ticks on shallow water.
		holdTest(ctx, server, conn, label + " D hold");
		// E: ice -> normal placement, no swap.
		normalPlace(ctx, server, conn, label + " E ice", "lily_pad", new BlockPos(100, 149, 102), "lily_pad");
		// F: dirt on stone -> normal placement, no swap.
		normalPlace(ctx, server, conn, label + " F dirt", "dirt", new BlockPos(100, 149, 98), "dirt");
		// G: frogspawn on water -> normal placement, no swap.
		normalPlace(ctx, server, conn, label + " G frogspawn", "frogspawn", new BlockPos(101, 149, 98), "frogspawn");
		// H: lily pad aimed at stone (cannot place) -> nothing, no swap.
		normalPlace(ctx, server, conn, label + " H lily on stone", "lily_pad", new BlockPos(99, 149, 100), "stone_none");
		// L: sneaking -> normal placement on water, no swap.
		ctx.getInput().holdKey(o -> o.keyShift);
		ctx.waitTicks(3);
		normalPlace(ctx, server, conn, label + " L sneak", "lily_pad", new BlockPos(102, 149, 100), "lily_pad placed, x15 main");
		ctx.getInput().releaseKey(o -> o.keyShift);
		ctx.waitTicks(3);
		// M: turned off -> normal placement on water, no swap.
		LilySwapConfig.get().enabled = false;
		normalPlace(ctx, server, conn, label + " M disabled", "lily_pad", new BlockPos(102, 149, 100), "lily_pad placed, x15 main");
		LilySwapConfig.get().enabled = true;
		// N: back on -> glitch again.
		lilyClick(ctx, server, conn, label + " N re-enabled", 100.5, 100.5, new BlockPos(102, 149, 100), "main");
	}

	private static void setup(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn, double x, double z, String mainItem, int count) {
		server.runCommand("tp @a " + x + " 150 " + z + " 0 0");
		server.runCommand("clear @a");
		server.runCommand("item replace entity @a hotbar.0 with " + mainItem + " " + count);
		ctx.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		ctx.waitTicks(10);
		conn.waitForClientboundPackets();
	}

	private static String playerState(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> "hp=" + mc.player.getHealth() + " pos=" + mc.player.blockPosition().toShortString()
			+ " hit=" + mc.hitResult.getType());
	}

	private static String hands(ItemStack main, ItemStack off) {
		return "main=" + desc(main) + " off=" + desc(off);
	}

	private static String desc(ItemStack s) {
		return s.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(s.getItem()).getPath() + "x" + s.getCount();
	}

	private static String serverHands(TestServerConnection conn, TestServerContext server) {
		return server.computeOnServer(s -> hands(conn.getServerPlayer().getMainHandItem(), conn.getServerPlayer().getOffhandItem()));
	}

	private static String clientHands(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> hands(mc.player.getMainHandItem(), mc.player.getOffhandItem()));
	}

	private static String serverBlock(TestServerContext server, TestServerConnection conn, BlockPos p) {
		return server.computeOnServer(s -> BuiltInRegistries.BLOCK.getKey(conn.getServerLevel().getBlockState(p).getBlock()).getPath());
	}

	private static String clientBlock(ClientGameTestContext ctx, BlockPos p) {
		return ctx.computeOnClient(mc -> BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(p).getBlock()).getPath());
	}

	private static void lilyClick(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn, String name, double x, double z, BlockPos water, String expectHand) {
		setup(ctx, server, conn, x, z, "lily_pad", 16);
		lilyClickNoSetup(ctx, server, conn, name, water, expectHand);
	}

	private static void lilyClickNoSetup(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn, String name, BlockPos water, String startHand) {
		BlockPos pad = water.above();
		ctx.getInput().lookAt(water);
		ctx.waitTicks(2);
		String before = "client[" + clientHands(ctx) + "] server[" + serverHands(conn, server) + "]";
		synchronized (timeline) { timeline.clear(); }
		watched = pad;
		ctx.getInput().pressKey(o -> o.keyUse);
		ctx.waitTicks(25);
		conn.waitForClientboundPackets();
		ctx.waitTicks(5);
		watched = null;
		String tl;
		synchronized (timeline) { tl = String.join(",", timeline.subList(0, Math.min(12, timeline.size()))); }
		String after = "client[" + clientHands(ctx) + "] server[" + serverHands(conn, server) + "]";
		results.add(name + " | " + playerState(ctx) + " | before " + before + " | after " + after
			+ " | clientTicks " + tl + " | serverBlock " + serverBlock(server, conn, pad) + " | clientBlockNow " + clientBlock(ctx, pad));
	}

	private static void holdTest(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn, String name) {
		BlockPos water = new BlockPos(102, 149, 100);
		BlockPos pad = water.above();
		setup(ctx, server, conn, 100.5, 100.5, "lily_pad", 16);
		ctx.getInput().lookAt(water);
		ctx.waitTicks(2);
		synchronized (timeline) { timeline.clear(); }
		watched = pad;
		ctx.getInput().holdKeyFor(o -> o.keyUse, 21);
		ctx.waitTicks(15);
		conn.waitForClientboundPackets();
		ctx.waitTicks(5);
		watched = null;
		String tl;
		int ghosts = 0;
		synchronized (timeline) {
			tl = String.join(",", timeline.subList(0, Math.min(30, timeline.size())));
			String prev = "";
			for (String t : timeline) { if (t.equals("lily_pad") && !prev.equals("lily_pad")) ghosts++; prev = t; }
		}
		results.add(name + " | " + playerState(ctx) + " | ghostAppearances " + ghosts + " | after client[" + clientHands(ctx) + "] server[" + serverHands(conn, server) + "]"
			+ " | clientTicks " + tl + " | serverBlock " + serverBlock(server, conn, pad) + " | clientBlockNow " + clientBlock(ctx, pad));
		server.runCommand("setblock " + pad.getX() + " " + pad.getY() + " " + pad.getZ() + " air");
		ctx.waitTicks(3);
	}

	private static void normalPlace(ClientGameTestContext ctx, TestServerContext server, TestServerConnection conn, String name, String item, BlockPos target, String expect) {
		setup(ctx, server, conn, 100.5, 100.5, item, 16);
		BlockPos placed = target.above();
		ctx.getInput().lookAt(target);
		ctx.waitTicks(2);
		ctx.getInput().pressKey(o -> o.keyUse);
		ctx.waitTicks(15);
		conn.waitForClientboundPackets();
		ctx.waitTicks(5);
		results.add(name + " | " + playerState(ctx) + " | after client[" + clientHands(ctx) + "] server[" + serverHands(conn, server) + "]"
			+ " | serverBlock " + serverBlock(server, conn, placed) + " | clientBlock " + clientBlock(ctx, placed) + " | expected " + expect);
		server.runCommand("setblock " + placed.getX() + " " + placed.getY() + " " + placed.getZ() + " air");
		ctx.waitTicks(3);
	}
}
