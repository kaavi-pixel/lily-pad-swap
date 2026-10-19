package pro.bhushan.lilyswap.config;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

public class LilySwapConfigScreen extends Screen {
	private final Screen parent;
	private int delayTicks;

	public LilySwapConfigScreen(Screen parent) {
		super(Component.translatable("lilyswap.config.title"));
		this.parent = parent;
		this.delayTicks = LilySwapConfig.get().delayTicks;
	}

	@Override
	protected void init() {
		int x = this.width / 2 - 100;
		int y = this.height / 2 - 24;
		this.addRenderableWidget(new DelaySlider(x, y, 200, 20));
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
			.bounds(x, y + 32, 200, 20)
			.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 52, 0xFFFFFFFF);
	}

	@Override
	public void onClose() {
		LilySwapConfig config = LilySwapConfig.get();
		config.delayTicks = this.delayTicks;
		config.save();
		this.minecraft.gui.setScreen(this.parent);
	}

	static Component label(int ticks) {
		return Component.translatable("lilyswap.config.delay", ticks, ticks * 50);
	}

	private class DelaySlider extends AbstractSliderButton {
		DelaySlider(int x, int y, int width, int height) {
			super(x, y, width, height, label(delayTicks),
				(double) (delayTicks - LilySwapConfig.MIN_DELAY) / (LilySwapConfig.MAX_DELAY - LilySwapConfig.MIN_DELAY));
		}

		private int ticks() {
			return LilySwapConfig.MIN_DELAY + (int) Math.round(this.value * (LilySwapConfig.MAX_DELAY - LilySwapConfig.MIN_DELAY));
		}

		/** Snap to whole ticks so the handle sits on the value shown. */
		@Override
		protected void setValue(double newValue) {
			double steps = LilySwapConfig.MAX_DELAY - LilySwapConfig.MIN_DELAY;
			super.setValue(Math.round(newValue * steps) / steps);
		}

		/** Arrow keys move one tick per press. */
		@Override
		public boolean keyPressed(KeyEvent event) {
			if (this.canChangeValue && (event.isLeft() || event.isRight())) {
				double steps = LilySwapConfig.MAX_DELAY - LilySwapConfig.MIN_DELAY;
				this.setValue(this.value + (event.isLeft() ? -1.0 : 1.0) / steps);
				return true;
			}
			return super.keyPressed(event);
		}

		@Override
		protected void updateMessage() {
			this.setMessage(label(ticks()));
		}

		@Override
		protected void applyValue() {
			delayTicks = ticks();
		}
	}
}
