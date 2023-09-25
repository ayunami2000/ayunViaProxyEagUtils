package me.ayunami2000.ayunViaProxyEagUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.viaproxy.proxy.util.ExceptionUtil;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EaglerXSkinHandler extends ChannelInboundHandlerAdapter {
    private final ConcurrentHashMap<String, byte[]> profileData;
    public static final SkinService skinService;
    private String user;
    private int pluginMessageId;

    public EaglerXSkinHandler() {
        this.profileData = new ConcurrentHashMap<>();
        this.pluginMessageId = -1;
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ExceptionUtil.handleNettyException(ctx, cause, null);
    }

    public void channelRead(final ChannelHandlerContext ctx, final Object obj) throws Exception {
        final EaglercraftHandler.State state = ((EaglercraftHandler) ctx.pipeline().get("eaglercraft-handler")).state;
        if (state == EaglercraftHandler.State.LOGIN && obj instanceof BinaryWebSocketFrame) {
            final ByteBuf bb = ((BinaryWebSocketFrame) obj).content();
            if (bb.readUnsignedByte() == 7) {
                if (this.profileData.size() > 12) {
                    ctx.close();
                    bb.release();
                    return;
                }
                int strlen = bb.readUnsignedByte();
                final String dataType = bb.readCharSequence(strlen, StandardCharsets.US_ASCII).toString();
                strlen = bb.readUnsignedShort();
                final byte[] readData = new byte[strlen];
                bb.readBytes(readData);
                if (bb.isReadable()) {
                    ctx.close();
                    bb.release();
                    return;
                }
                if (this.profileData.containsKey(dataType)) {
                    ctx.close();
                    bb.release();
                    return;
                }
                this.profileData.put(dataType, readData);
            }
            bb.resetReaderIndex();
        }
        if (state != EaglercraftHandler.State.LOGIN_COMPLETE) {
            super.channelRead(ctx, obj);
            return;
        }
        if (this.user == null) {
            this.user = ((EaglercraftHandler) ctx.pipeline().get("eaglercraft-handler")).username;
            final UUID clientUUID = UUID.nameUUIDFromBytes(("OfflinePlayer:" + this.user).getBytes(StandardCharsets.UTF_8));
            if (this.profileData.containsKey("skin_v1")) {
                try {
                    SkinPackets.registerEaglerPlayer(clientUUID, this.profileData.get("skin_v1"), EaglerXSkinHandler.skinService);
                } catch (Throwable ex) {
                    SkinPackets.registerEaglerPlayerFallback(clientUUID, EaglerXSkinHandler.skinService);
                }
            } else {
                SkinPackets.registerEaglerPlayerFallback(clientUUID, EaglerXSkinHandler.skinService);
            }
        }
        if (this.pluginMessageId <= 0) {
            this.pluginMessageId = ((EaglercraftHandler) ctx.pipeline().get("eaglercraft-handler")).pluginMessageId;
        }
        if (obj instanceof BinaryWebSocketFrame) {
            final ByteBuf bb = ((BinaryWebSocketFrame) obj).content();
            try {
                if (PacketTypes.readVarInt(bb) == this.pluginMessageId && PacketTypes.readString(bb, 32767).equals("EAG|Skins-1.8")) {
                    final byte[] data = new byte[bb.readableBytes()];
                    bb.readBytes(data);
                    SkinPackets.processPacket(data, ctx, EaglerXSkinHandler.skinService);
                    bb.release();
                    return;
                }
            } catch (Exception ignored) {
            }
            bb.resetReaderIndex();
        }
        super.channelRead(ctx, obj);
    }

    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        if (this.user != null) {
            EaglerXSkinHandler.skinService.unregisterPlayer(UUID.nameUUIDFromBytes(("OfflinePlayer:" + this.user).getBytes(StandardCharsets.UTF_8)));
        }
    }

    static {
        skinService = new SkinService();
    }
}
