package com.flechazo.jinchancygale.network.module;


import com.flechazo.jinchancygale.client.gui.ConfigScreen;
import com.flechazo.jinchancygale.config.ConfigManager;
import com.flechazo.jinchancygale.network.NetworkHandler;
import com.flechazo.jinchancygale.network.PacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.network.NetworkEvent;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ConfigPacket extends PacketHandler.AbstractPacket {
    private final Map<String, Object> configData = new HashMap<>();
    private int type;

    public ConfigPacket(int type) {
        this.type = type;
    }

    public static ConfigPacket createForUpdate(Map<String, Object> serverConfig) { // post to server to save config
        ConfigPacket packet = new ConfigPacket(0b0000); // type: last one for 0: from client,1: from server
        packet.configData.putAll(serverConfig);
        return packet;
    }

    public static ConfigPacket createForSync(Map<ForgeConfigSpec.ConfigValue, Field> map) { // send config to client
        ConfigPacket packet = new ConfigPacket(0b0001);
        map.forEach((configValue, field) -> {
            packet.configData.put(field.getName(), configValue.get());
        });
        return packet;
    }

    public static ConfigPacket reSyncRequest() { // resync in gui
        return new ConfigPacket(0b0010);
    }

    public static ConfigPacket reSyncResponse(Map<String, Object> config) { // resync in gui
        ConfigPacket packet = new ConfigPacket(0b0011);
        packet.configData.putAll(config);
        return packet;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(type);
        buf.writeInt(configData.size());

        configData.forEach((key, value) -> {
            buf.writeUtf(key);
            encodeValue(buf, value);
        });
    }

    private void encodeValue(FriendlyByteBuf buf, Object value) {
        if (value instanceof Boolean) {
            buf.writeByte(0); // 类型标记: boolean
            buf.writeBoolean((Boolean) value);
        } else if (value instanceof Integer) {
            buf.writeByte(1); // 类型标记: int
            buf.writeInt((Integer) value);
        } else if (value instanceof Long) {
            buf.writeByte(2); // 类型标记: long
            buf.writeLong((Long) value);
        } else if (value instanceof Float) {
            buf.writeByte(3); // 类型标记: float
            buf.writeFloat((Float) value);
        } else if (value instanceof Double) {
            buf.writeByte(4); // 类型标记: double
            buf.writeDouble((Double) value);
        } else if (value instanceof String) {
            buf.writeByte(5); // 类型标记: string
            buf.writeUtf((String) value);
        }
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        type = buf.readInt();
        int size = buf.readInt();

        configData.clear();

        for (int i = 0; i < size; i++) {
            decodeValue(buf);
        }
    }

    private void decodeValue(FriendlyByteBuf buf) {
        String key = buf.readUtf();
        byte type = buf.readByte();

        switch (type) {
            case 0 -> configData.put(key, buf.readBoolean());
            case 1 -> configData.put(key, buf.readInt());
            case 2 -> configData.put(key, buf.readLong());
            case 3 -> configData.put(key, buf.readFloat());
            case 4 -> configData.put(key, buf.readDouble());
            case 5 -> configData.put(key, buf.readUtf());
            default -> throw new IllegalArgumentException("Unknown config type: " + type);
        }
    }

    @Override
    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            if (type == -1) return;
            if (type % 2 == 1) {
                handleClientSide();
            } else if (type % 2 == 0) {
                handleServerSide(context);
            }
        });
        context.get().setPacketHandled(true);
    }

    private void handleServerSide(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        } else if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("You don't have permission to update the config"));
            return;
        }
        if ((type >> 1) % 2 == 0) {
            ConfigManager.syncValue(configData, true);
        } else {
            NetworkHandler.sendToClient(reSyncResponse(ConfigManager.defaultValues), player);
        }
    }

    private void handleClientSide() {
        if ((type >> 1) % 2 == 0) {
            Minecraft.getInstance().setScreen(new ConfigScreen(configData, false));
        } else {
            Screen screen = Minecraft.getInstance().screen;
            if (screen instanceof ConfigScreen) {
                ((ConfigScreen) screen).updateConfig(configData);
            }
        }
    }
}
