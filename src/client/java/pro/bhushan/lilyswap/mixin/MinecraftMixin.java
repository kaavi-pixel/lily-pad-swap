package pro.bhushan.lilyswap.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.bhushan.lilyswap.LilySwapState;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	public LocalPlayer player;

	@Inject(method = "tick", at = @At("HEAD"))
	private void lilyswap$tick(CallbackInfo ci) {
		LilySwapState.tick((Minecraft) (Object) this);
	}

	/**
	 * While a glitch click is held or unconfirmed, the client's hands and the
	 * ghost block are stale, so right clicks with a lily pad in either hand are
	 * skipped. Any other right click first sends the held swap + use, so it
	 * reaches the server in the right order.
	 */
	@Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
	private void lilyswap$beforeUse(CallbackInfo ci) {
		if (!LilySwapState.isBusy()) return;
		LocalPlayer player = this.player;
		if (player != null && (player.getMainHandItem().is(Items.LILY_PAD) || player.getOffhandItem().is(Items.LILY_PAD))) {
			// Skipped without flushing, so holding right click keeps the full delay.
			ci.cancel();
			return;
		}
		LilySwapState.flush();
	}
}
