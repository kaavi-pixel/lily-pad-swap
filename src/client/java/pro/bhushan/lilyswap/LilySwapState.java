package pro.bhushan.lilyswap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;
import pro.bhushan.lilyswap.config.LilySwapConfig;

/**
 * Client-thread state for the lily pad glitch.
 *
 * The glitch needs the server to receive the swap-hands packet before the
 * use-item packet. With a delay, both are held back together (swap first,
 * then use), so the server still never places the pad while the client keeps
 * showing it until the server acknowledges the click.
 */
public final class LilySwapState {
	/** True while MultiPlayerGameMode.useItem runs for a real client right click. */
	public static boolean inClientUseItem;
	/** Set when the client just predicted a lily pad on water during this click. */
	public static boolean glitchRequested;

	private static Packet<?> pendingUse;
	private static int pendingTicks;
	private static ClientPacketListener connectionRef;
	private static boolean sending;

	/** A glitch click whose packets are held or not yet acknowledged by the server. */
	private static boolean inFlight;
	private static int inFlightSequence = -1;
	private static boolean acked;
	private static boolean ackedLastTick;
	private static int ageTicks;
	private static final int TIMEOUT_TICKS = 100;

	private LilySwapState() {
	}

	/**
	 * While a glitch click is held back or awaiting the server's acknowledgement,
	 * the client's view of the hands and of the ghost block is stale, so another
	 * lily pad click would be judged on wrong data. Such clicks are skipped.
	 */
	public static boolean isBusy() {
		return inFlight;
	}

	/** Called with the use-item packet that ends the prediction of a glitch click. */
	public static void handleGlitchUsePacket(ClientPacketListener connection, Packet<?> usePacket) {
		inFlight = true;
		inFlightSequence = usePacket instanceof ServerboundUseItemPacket use ? use.getSequence() : Integer.MAX_VALUE;
		acked = false;
		ackedLastTick = false;
		ageTicks = 0;
		connectionRef = connection;
		int delay = LilySwapConfig.get().delayTicks;
		if (delay <= 0) {
			sendPair(connection, usePacket);
		} else {
			pendingUse = usePacket;
			pendingTicks = delay;
		}
	}

	/** ClientboundBlockChangedAckPacket: the server has handled clicks up to this sequence. */
	public static void onBlockChangedAck(int sequence) {
		if (inFlight && pendingUse == null && sequence >= inFlightSequence) acked = true;
	}

	/** Once per client tick, before key handling. */
	public static void tick(Minecraft minecraft) {
		if (!inFlight) return;
		if (minecraft.player == null || minecraft.getConnection() != connectionRef) {
			reset();
			return;
		}
		if (pendingUse != null) {
			if (--pendingTicks <= 0) flush();
			return;
		}
		// The server sends its inventory update in the same tick as the ack, right
		// after it, so wait one more client tick before allowing the next click.
		if (ackedLastTick || ++ageTicks > TIMEOUT_TICKS) {
			reset();
		} else if (acked) {
			ackedLastTick = true;
		}
	}

	/** Send the held swap + use now. Safe to call when nothing is pending. */
	public static void flush() {
		if (pendingUse == null) return;
		Packet<?> use = pendingUse;
		pendingUse = null;
		pendingTicks = 0;
		if (connectionRef != null && Minecraft.getInstance().getConnection() == connectionRef) {
			sendPair(connectionRef, use);
		} else {
			reset();
		}
	}

	private static void reset() {
		pendingUse = null;
		pendingTicks = 0;
		connectionRef = null;
		inFlight = false;
		inFlightSequence = -1;
		acked = false;
		ackedLastTick = false;
		ageTicks = 0;
	}

	/**
	 * Any outgoing packet that could change inventory, hands or world state has to
	 * reach the server after the held swap + use, so flush before it. Movement,
	 * input, tick-end, arm swing and keep-alive style packets do not affect the hands and may
	 * pass without flushing.
	 */
	public static void beforeSend(Packet<?> packet) {
		if (pendingUse == null || sending) return;
		if (packet instanceof ServerboundMovePlayerPacket
			|| packet instanceof ServerboundPlayerInputPacket
			|| packet instanceof ServerboundClientTickEndPacket
			|| packet instanceof ServerboundPlayerCommandPacket
			|| packet instanceof ServerboundChunkBatchReceivedPacket
			|| packet instanceof ServerboundKeepAlivePacket
			|| packet instanceof ServerboundPongPacket
			|| packet instanceof ServerboundPingRequestPacket
			// 26.2 only: the client sends an arm swing right after the click. It only animates.
			|| packet instanceof ServerboundSwingPacket) {
			return;
		}
		flush();
	}

	private static void sendPair(ClientPacketListener connection, Packet<?> usePacket) {
		sending = true;
		try {
			connection.send(new ServerboundPlayerActionPacket(
				ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
			connection.send(usePacket);
		} finally {
			sending = false;
		}
	}
}
