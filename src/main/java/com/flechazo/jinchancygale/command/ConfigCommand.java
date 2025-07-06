package com.flechazo.jinchancygale.command;

import com.flechazo.jinchancygale.client.gui.ConfigScreen;
import com.flechazo.jinchancygale.config.ConfigManager;
import com.flechazo.jinchancygale.network.NetworkHandler;
import com.flechazo.jinchancygale.network.module.ConfigPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;

public class ConfigCommand {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("jcgconfig").requires((ret) -> ret.hasPermission(2))
                .then(Commands.literal("open_screen").executes((ret) -> openScreen(ret))));
    }

    public static int openScreen(CommandContext<CommandSourceStack> ret) {
        ServerPlayer player = ret.getSource().getPlayer();
        if (player != null) {
            if (!player.hasPermissions(2)) {
                Minecraft.getInstance().setScreen(new ConfigScreen(Map.copyOf(ConfigManager.createSyncData(false)), true));
            }
            ConfigPacket packet = ConfigPacket.createForSync(Map.copyOf(ConfigManager.map));
            NetworkHandler.sendToClient(packet, player);
        }
        return 0;
    }
}
