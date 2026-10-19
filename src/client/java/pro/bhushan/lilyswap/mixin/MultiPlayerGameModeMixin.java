package pro.bhushan.lilyswap.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.bhushan.lilyswap.LilySwapState;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "useItem", at = @At("HEAD"))
	private void lilyswap$begin(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		LilySwapState.inClientUseItem = true;
		LilySwapState.glitchRequested = false;
	}

	@Inject(method = "useItem", at = @At("RETURN"))
	private void lilyswap$end(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		LilySwapState.inClientUseItem = false;
		LilySwapState.glitchRequested = false;
	}

	/**
	 * startPrediction runs the client-side placement, then sends the packet that
	 * concludes it. For a glitch click, that use-item packet is handed to
	 * LilySwapState, which sends the swap packet in front of it, now or after
	 * the configured delay.
	 */
	@WrapOperation(
		method = "startPrediction",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V")
	)
	private void lilyswap$routeUsePacket(ClientPacketListener connection, Packet<?> packet, Operation<Void> original) {
		if (LilySwapState.glitchRequested && LilySwapState.inClientUseItem && packet instanceof ServerboundUseItemPacket) {
			LilySwapState.glitchRequested = false;
			LilySwapState.handleGlitchUsePacket(connection, packet);
			return;
		}
		original.call(connection, packet);
	}
}
