package com.flechazo.jinchancygale.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.flechazo.jinchancygale.JinchancyGale.MODID;

public abstract class PacketHandler {
    // Main channel instance
    private final SimpleChannel channel;
    // Packet ID counter
    private int packetId = 0;

    /**
     * Initialize network manager with mod ID and protocol version.
     *
     * @param protocolVersion Network protocol version string
     */
    public PacketHandler(String protocolVersion) {
        this.channel = NetworkRegistry.newSimpleChannel(
                ResourceLocation.parse(MODID),
                () -> protocolVersion,
                protocolVersion::equals,
                protocolVersion::equals
        );
    }

    /**
     * Register all packets during mod initialization.
     * Should be called in common setup.
     */
    public abstract void registerPackets();

    /**
     * Send packet to server.
     *
     * @param packet Packet instance to send
     */
    public <T extends AbstractPacket> void sendToServer(T packet) {
        channel.sendToServer(packet);
    }

    /**
     * Send packet to specific client.
     *
     * @param packet Packet instance
     * @param player Target player
     */
    public <T extends AbstractPacket> void sendToClient(T packet, ServerPlayer player) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Internal packet registration method.
     *
     * @param packetClass Class of the packet
     * @param encoder     Encoder function
     * @param decoder     Decoder function
     * @param handler     Packet handler
     */
    protected <T extends AbstractPacket> void registerPacket(
            Class<T> packetClass,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, Supplier<NetworkEvent.Context>> handler
    ) {
        channel.registerMessage(
                packetId++,
                packetClass,
                encoder,
                decoder,
                (packet, contextSupplier) -> {
                    NetworkEvent.Context context = contextSupplier.get();
                    context.enqueueWork(() -> handler.accept(packet, contextSupplier));
                    context.setPacketHandled(true);
                }
        );
    }

    /**
     * Base class for all custom packets.
     * Implementations must provide encode/decode/handle methods.
     */
    public static abstract class AbstractPacket {
        /**
         * Encode packet data into byte buffer.
         *
         * @param buf Target byte buffer
         */
        public abstract void encode(FriendlyByteBuf buf);

        /**
         * Decode packet data from byte buffer.
         *
         * @param buf Source byte buffer
         */
        public abstract void decode(FriendlyByteBuf buf);

        /**
         * Handle packet on receiving side.
         *
         * @param context Network context
         */
        public abstract void handle(Supplier<NetworkEvent.Context> context);
    }
}
