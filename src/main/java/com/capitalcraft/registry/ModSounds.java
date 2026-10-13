package com.capitalcraft.registry;

import com.capitalcraft.CapitalCraft;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
    private ModSounds() {}

    public static final SoundEvent UI_CLICK = reg("ui_click");
    public static final SoundEvent SPIN_TICK = reg("spin_tick");
    public static final SoundEvent SPIN_START = reg("spin_start");
    public static final SoundEvent WIN = reg("win");
    public static final SoundEvent JACKPOT = reg("jackpot");
    public static final SoundEvent LOSE = reg("lose");
    public static final SoundEvent COIN = reg("coin");
    public static final SoundEvent CASH = reg("cash");
    public static final SoundEvent GPU_HUM = reg("gpu_hum");
    public static final SoundEvent SHIFT_START = reg("shift_start");
    public static final SoundEvent SHIFT_END = reg("shift_end");
    public static final SoundEvent TRADE_BUY = reg("trade_buy");
    public static final SoundEvent TRADE_SELL = reg("trade_sell");
    public static final SoundEvent ERROR = reg("error");
    public static final SoundEvent ALERT = reg("alert");

    private static SoundEvent reg(String name) {
        Identifier id = CapitalCraft.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {}
}
