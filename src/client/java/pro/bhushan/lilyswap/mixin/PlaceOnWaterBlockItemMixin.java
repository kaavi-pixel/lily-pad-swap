package pro.bhushan.lilyswap.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pro.bhushan.lilyswap.LilySwapState;
import pro.bhushan.lilyswap.config.LilySwapConfig;

/**
 * Lily pads are placed through PlaceOnWaterBlockItem.use(), which the client
 * runs inside MultiPlayerGameMode.useItem's prediction, before the
 * ServerboundUseItemPacket is sent. This marks the click so the swap packet
 * goes out ahead of the use packet, the same order vanilla produces when F and
 * right click land in one tick. The server swaps first, then tries to use the other hand's
 * item, so the lily pad the client drew is never placed or consumed and is
 * rolled back when the server acknowledges the click.
 */
@Mixin(PlaceOnWaterBlockItem.class)
public abstract class PlaceOnWaterBlockItemMixin {
	// Client thread only. In singleplayer the integrated server runs this same
	// method on its own thread, so every access is behind level.isClientSide().
	@Unique
	private static ItemStack lilyswap$stack = ItemStack.EMPTY;
	@Unique
	private static int lilyswap$count;

	@Inject(method = "use", at = @At("HEAD"))
	private void lilyswap$rememberCount(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
		if (!level.isClientSide()) return;
		lilyswap$stack = player.getItemInHand(hand);
		lilyswap$count = lilyswap$stack.getCount();
	}

	@Inject(method = "use", at = @At("RETURN"))
	private void lilyswap$requestGlitch(
		Level level,
		Player player,
		InteractionHand hand,
		CallbackInfoReturnable<InteractionResult> cir,
		@Local(ordinal = 0) BlockHitResult hitResult
	) {
		if (!level.isClientSide()) return;
		ItemStack stack = lilyswap$stack;
		int count = lilyswap$count;
		lilyswap$stack = ItemStack.EMPTY;

		// Lily pads only; frogspawn shares this class.
		if ((Object) this != Items.LILY_PAD) return;
		if (!LilySwapState.inClientUseItem || LilySwapState.glitchRequested) return;
		if (!(player instanceof LocalPlayer localPlayer) || player.isSpectator()) return;
		// Turned off in the config: place like vanilla.
		if (!LilySwapConfig.get().enabled) return;
		// Sneaking places the pad normally. The key is checked directly too,
		// because the player's sneak input only updates later in the tick.
		if (localPlayer.isShiftKeyDown() || Minecraft.getInstance().options.keyShift.isDown()) return;
		// Only when the client actually placed the lily pad.
		if (!(cir.getReturnValue() instanceof InteractionResult.Success)) return;

		BlockPos supportPos = hitResult.getBlockPos();
		BlockPos padPos = supportPos.above();
		// On water only (lily pads can also go on ice; those are left alone).
		if (!level.getFluidState(supportPos).is(FluidTags.WATER)) return;
		if (!level.getBlockState(padPos).is(Blocks.LILY_PAD)) return;

		// MultiPlayerGameModeMixin sends the swap packet in front of the use-item
		// packet, immediately or after the configured delay.
		LilySwapState.glitchRequested = true;

		// The server never consumes this pad, so undo the client's predicted -1.
		// Otherwise the count can stay wrong when the server's hands end up unchanged.
		if (stack != ItemStack.EMPTY && stack.getCount() != count) {
			stack.setCount(count);
		}
	}
}
