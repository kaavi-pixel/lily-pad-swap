package pro.bhushan.lilyswap.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.bhushan.lilyswap.LilySwapState;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "handleBlockChangedAck", at = @At("HEAD"))
	private void lilyswap$onAck(int sequence, CallbackInfo ci) {
		LilySwapState.onBlockChangedAck(sequence);
	}
}
