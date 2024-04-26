package me.ayunami2000.ayunViaProxyEagUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.vialegacy.protocols.release.protocol1_7_2_5to1_6_4.types.Types1_6_4;
import net.raphimc.viaproxy.proxy.util.ExceptionUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class EaglerVoiceHandler extends ChannelInboundHandlerAdapter {

    public static final VoiceServerImpl voiceService = new VoiceServerImpl();

    public String user;
    public UUID uuid;
    public final boolean old;
    private int pluginMessageId = -1;

    public EaglerVoiceHandler(final String username) {
        this.user = username;
        if (this.user == null) {
            this.uuid = null;
            old = false;
        } else {
            old = true;
            this.uuid = nameToUUID(this.user);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ExceptionUtil.handleNettyException(ctx, cause, null);
    }

    private static UUID nameToUUID(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private boolean sent = false;

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object obj) throws Exception {
        if (!FunnyConfig.eaglerVoice) {
            super.channelRead(ctx, obj);
            return;
        }
        if (((EaglercraftHandler) ctx.pipeline().get("eaglercraft-handler")).state == EaglercraftHandler.State.LOGIN_COMPLETE && !sent) {
            if (this.user == null) {
                this.user = ((EaglercraftHandler) ctx.pipeline().get("eaglercraft-handler")).username;
                this.uuid = nameToUUID(this.user);
            }
            sent = true;
            voiceService.handlePlayerLoggedIn(ctx);
        }
        if (obj instanceof BinaryWebSocketFrame) {
            final ByteBuf bb = ((BinaryWebSocketFrame) obj).content();
            if (old) {
                if (bb.readableBytes() >= 3 && bb.readByte() == -6) {
                    String tag;
                    byte[] msg;
                    try {
                        tag = Types1_6_4.STRING.read(bb);
                        msg = new byte[bb.readShort()];
                        bb.readBytes(msg);
                    } catch (Exception e) {
                        bb.resetReaderIndex();
                        super.channelRead(ctx, obj);
                        return;
                    }
                    try {
                        if (!tag.equals("EAG|Voice")) {
                            bb.resetReaderIndex();
                            super.channelRead(ctx, obj);
                            return;
                        }
                        final DataInputStream streamIn = new DataInputStream(new ByteArrayInputStream(msg));
                        final int sig = streamIn.read();
                        switch (sig) {
                            case 0: {
                                voiceService.handleVoiceSignalPacketTypeRequest(nameToUUID(streamIn.readUTF()), ctx);
                                bb.release();
                                return;
                            }
                            case 1: {
                                voiceService.handleVoiceSignalPacketTypeConnect(ctx);
                                bb.release();
                                return;
                            }
                            case 2: {
                                try {
                                    voiceService.handleVoiceSignalPacketTypeDisconnect(nameToUUID(streamIn.readUTF()), ctx);
                                } catch (EOFException e) {
                                    voiceService.handleVoiceSignalPacketTypeDisconnect(null, ctx);
                                }
                                bb.release();
                                return;
                            }
                            case 3: {
                                voiceService.handleVoiceSignalPacketTypeICE(nameToUUID(streamIn.readUTF()), streamIn.readUTF(), ctx);
                                bb.release();
                                return;
                            }
                            case 4: {
                                voiceService.handleVoiceSignalPacketTypeDesc(nameToUUID(streamIn.readUTF()), streamIn.readUTF(), ctx);
                                bb.release();
                                return;
                            }
                            default: {
                                bb.release();
                                return;
                            }
                        }
                    } catch (Throwable var8) {
                        voiceService.handlePlayerLoggedOut(uuid);
                        bb.release();
                        return;
                    }
                }
                bb.resetReaderIndex();
            } else {
                if (this.pluginMessageId <= 0) {
                    this.pluginMessageId = ((EaglercraftHandler) ctx.pipeline().get("eaglercraft-handler")).pluginMessageId;
                }
                try {
                    if (PacketTypes.readVarInt(bb) == this.pluginMessageId && PacketTypes.readString(bb, 32767).equals("EAG|Voice-1.8")) {
                        final byte[] data = new byte[bb.readableBytes()];
                        bb.readBytes(data);
                        VoiceSignalPackets.processPacket(data, ctx);
                        bb.release();
                        return;
                    }
                } catch (Exception ignored) {
                }
                bb.resetReaderIndex();
            }
        }
        super.channelRead(ctx, obj);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        voiceService.handlePlayerLoggedOut(uuid);
    }
}
