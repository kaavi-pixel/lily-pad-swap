package pro.bhushan.lilyswap.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.bhushan.lilyswap.LilySwapState;

/** Keeps the held swap + use ahead of any later packet that could touch the hands. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
	@Inject(method = "send", at = @At("HEAD"))
	private void lilyswap$flushBeforeOtherPackets(Packet<?> packet, CallbackInfo ci) {
		LilySwapState.beforeSend(packet);
	}
}
