# Lily Pad Swap 1.1.0

Client-side Fabric mod for Minecraft 26.3 (Java 25, Fabric Loader 0.19.5+). Fabric API is not required. Mod Menu (21.0.0 for 26.3) is optional and adds the settings button.

## What it does

When you right-click water with a lily pad, the mod performs the ghost lily pad glitch. The lily pad appears on your screen, the server never places it, you keep the item, and the lily pad moves to your other hand. The next click moves it back.

It only triggers when all of these are true:

- The item being used is a lily pad (main hand or offhand).
- The lily pad actually got placed on your client.
- The block under it is water (including waterlogged blocks). Lily pads on ice place normally.

Every other block, frogspawn, and lily pads that can't be placed behave like vanilla. Right click is the vanilla Use key, so it follows your Controls setting.

## Swap delay

Mod Menu > Lily Pad Swap > Swap delay, 0 to 20 ticks (1 tick = 50 ms). Saved in `config/lilyswap.json`. Default 0.

The delay holds back both packets, the swap and then the place, for that many ticks. Only delaying the swap would let the server place the pad for real, because it would handle the place before the swap. The lily pad shows right away on your screen and stays until the server confirms the click. That's roughly the delay plus 1 to 2 ticks.

While a glitch click is waiting, right clicks with a lily pad in either hand are skipped. Holding right click repeats once each click is confirmed, so a longer delay means fewer, longer ghost pads. If you do something else during the delay that sends a request to the server, the held swap and place go first so the order stays right. That includes changing hotbar slot, pressing F, dropping an item, or opening your inventory.

## How it works

Vanilla handles the swap-hands key before right click within one tick (`Minecraft.handleKeybinds`). The swap only sends `ServerboundPlayerActionPacket(SWAP_ITEM_WITH_OFFHAND)`; the client doesn't swap locally. The client then predicts the lily pad placement and sends `ServerboundUseItemPacket`. The server swaps first, then uses whatever is now in that hand, so nothing is placed. Its block-change acknowledgement rolls back the client's predicted lily pad. This is Mojang bug MC-256440 (duplicate MC-278794).

The mod hooks `PlaceOnWaterBlockItem.use` on the client to detect a successful predicted lily pad on water. It then routes that click's use packet through `LilySwapState`, which sends the swap packet in front of it, now or after the delay. The client's predicted -1 lily pad is undone, since the server never consumes it.

## Notes

- Keep the other hand empty, or hold an item with no right-click action. The server uses whatever ends up in that hand, so food, ender pearls, shields, buckets, or a second lily pad stack would be used or placed for real.
- With a delay, the server receives the click later, aimed in the direction you were looking when you clicked.
- Many servers treat this as an exploit, and longer ghost pads are easier for anti-cheat to notice. Check the rules before using it in multiplayer.

## Testing

`./gradlew runClientGameTest` runs a real 26.3 client against both an integrated and a dedicated server, at 0, 5 and 10 tick delays. It checks shallow and deep water, both hands, holding right click, ice, dirt, frogspawn, a lily pad aimed at stone, and changing hotbar slot, clicking again or pressing F during the delay. It also checks the config screen and saving. On headless Linux, run it under Xvfb with `SDL_VIDEO_FORCE_EGL=1`.
