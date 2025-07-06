package com.flechazo.jinchancygale.event;

import com.flechazo.jinchancygale.command.ConfigCommand;
import net.minecraftforge.common.MinecraftForge;

public class EventManager {
    public static void register() {
        // Command register
        MinecraftForge.EVENT_BUS.register(ConfigCommand.class);
    }
}
