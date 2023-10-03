package me.ayunami2000.ayunViaProxyEagUtils;

import com.google.common.primitives.Ints;
import com.viaversion.viaversion.util.ChatColorUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.websocketx.*;
import net.raphimc.netminecraft.constants.MCPackets;
import net.raphimc.netminecraft.netty.connection.NetClient;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.vialegacy.protocols.release.protocol1_6_1to1_5_2.ClientboundPackets1_5_2;
import net.raphimc.vialegacy.protocols.release.protocol1_6_1to1_5_2.ServerboundPackets1_5_2;
import net.raphimc.vialegacy.protocols.release.protocol1_7_2_5to1_6_4.types.Types1_6_4;
import net.raphimc.vialoader.util.VersionEnum;
import net.raphimc.viaproxy.proxy.session.LegacyProxyConnection;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import net.raphimc.viaproxy.proxy.util.ExceptionUtil;

import java.nio.charset.StandardCharsets;
import java.util.*;

public class EaglerServerHandler extends MessageToMessageCodec<BinaryWebSocketFrame, ByteBuf> {
    private final VersionEnum version;
    private final String password;
    private final NetClient proxyConnection;
    private final List<UUID> skinsBeingFetched = new ArrayList<>();
    private ByteBuf serverBoundPartialPacket = Unpooled.EMPTY_BUFFER;
    private ByteBuf clientBoundPartialPacket = Unpooled.EMPTY_BUFFER;
    public EaglerServerHandler(NetClient proxyConnection, String password) {
        this.version = proxyConnection instanceof ProxyConnection ? ((ProxyConnection) proxyConnection).getServerVersion() : VersionEnum.r1_5_2;
        this.password = password;
        this.proxyConnection = proxyConnection;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ExceptionUtil.handleNettyException(ctx, cause, null);
    }

    private int handshakeState = 0;

    @Override
    public void encode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (version.isNewerThan(VersionEnum.r1_6_4)) {
            if (handshakeState == 0) {
                handshakeState = 1;
                if (((ProxyConnection) proxyConnection).getGameProfile() == null) {
                    out.add(Unpooled.EMPTY_BUFFER);
                    ctx.close();
                    return;
                }
                ConnectionHandshake.attemptHandshake(out, ctx.channel(), (ProxyConnection) proxyConnection, password);
                if (out.isEmpty()) {
                    out.add(Unpooled.EMPTY_BUFFER);
                }
            } else if (handshakeState < 4) {
                out.add(Unpooled.EMPTY_BUFFER);
            } else {
                out.add(new BinaryWebSocketFrame(in.retain()));
            }
        } else {
            ByteBuf bb = ctx.alloc().buffer(serverBoundPartialPacket.readableBytes() + in.readableBytes());
            bb.writeBytes(serverBoundPartialPacket);
            serverBoundPartialPacket.release();
            serverBoundPartialPacket = Unpooled.EMPTY_BUFFER;
            bb.writeBytes(in);
            int readerIndex = 0;
            try {
                while (bb.isReadable()) {
                    readerIndex = bb.readerIndex();
                    ServerboundPackets1_5_2 pkt = ServerboundPackets1_5_2.getPacket(bb.readUnsignedByte());
                    pkt.getPacketReader().accept(null, bb);
                    int len = bb.readerIndex() - readerIndex;
                    ByteBuf packet = ctx.alloc().buffer(len);
                    bb.readerIndex(readerIndex);
                    bb.readBytes(packet, len);
                    encodeOld(ctx, packet, out);
                }
            } catch (Exception e) {
                bb.readerIndex(readerIndex);
                if (bb.readableBytes() > 65535) {
                    ctx.close();
                    out.add(Unpooled.EMPTY_BUFFER);
                    return;
                }
                serverBoundPartialPacket = ctx.alloc().buffer(bb.readableBytes());
                serverBoundPartialPacket.writeBytes(bb);
            }
        }
        if (out.isEmpty()) {
            out.add(Unpooled.EMPTY_BUFFER);
        }
    }

    public void encodeOld(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.readableBytes() >= 2 && in.getUnsignedByte(0) == 2) {
            in.setByte(1, in.getUnsignedByte(1) + 8);
        }
        if (in.readableBytes() >= 1 && in.getUnsignedByte(0) == 0xFD) {
            return;
        }
        if (in.readableBytes() >= 3 && in.getUnsignedByte(0) == 250) {
            in.skipBytes(1);
            String tag;
            try {
                tag = Types1_6_4.STRING.read(in);
                if (tag.equals("EAG|Skins-1.8")) {
                    return;
                }
            } catch (Exception ignored) {
            }
            in.resetReaderIndex();
        }
        out.add(new BinaryWebSocketFrame(in.retain()));
    }

    @Override
    public void decode(ChannelHandlerContext ctx, BinaryWebSocketFrame in, List<Object> out) {
        if (version.isNewerThan(VersionEnum.r1_6_4)) {
            if (handshakeState == 0) {
                out.add(Unpooled.EMPTY_BUFFER);
            } else if (handshakeState == 1) {
                handshakeState = 2;
                ConnectionHandshake.attemptHandshake2(ctx.channel(), ByteBufUtil.getBytes(in.content()), (ProxyConnection) proxyConnection, password);
                out.add(Unpooled.EMPTY_BUFFER);
            } else if (handshakeState == 2) {
                handshakeState = 3;
                ConnectionHandshake.attemptHandshake3(ctx.channel(), ByteBufUtil.getBytes(in.content()), (ProxyConnection) proxyConnection);
                ByteBuf bb = ctx.alloc().buffer();
                PacketTypes.writeVarInt(bb, MCPackets.S2C_LOGIN_SUCCESS.getId(version.getVersion()));
                PacketTypes.writeString(bb, ((ProxyConnection) proxyConnection).getGameProfile().getId().toString());
                PacketTypes.writeString(bb, ((ProxyConnection) proxyConnection).getGameProfile().getName());
                out.add(bb);
            } else if (handshakeState == 3) {
                handshakeState = 4;
                ConnectionHandshake.attemptHandshake4(ctx.channel(), ByteBufUtil.getBytes(in.content()), (ProxyConnection) proxyConnection);
                out.add(Unpooled.EMPTY_BUFFER);
            } else {
                if (in.content().getByte(0) == MCPackets.S2C_LOGIN_SUCCESS.getId(version.getVersion()) && in.content().getByte(1) == 0 && in.content().getByte(2) == 2) {
                    out.add(Unpooled.EMPTY_BUFFER);
                    return;
                }
                if (in.content().getByte(0) == 0) {
                    in.content().skipBytes(1);
                    PacketTypes.readVarInt(in.content());
                    if (in.content().readableBytes() > 0) {
                        in.content().setByte(0, 0x40);
                    }
                    in.content().resetReaderIndex();
                }
                out.add(in.content().retain());
            }
        } else {
            ByteBuf bb = ctx.alloc().buffer(clientBoundPartialPacket.readableBytes() + in.content().readableBytes());
            bb.writeBytes(clientBoundPartialPacket);
            clientBoundPartialPacket.release();
            clientBoundPartialPacket = Unpooled.EMPTY_BUFFER;
            bb.writeBytes(in.content());
            int readerIndex = 0;
            try {
                while (bb.isReadable()) {
                    readerIndex = bb.readerIndex();
                    ClientboundPackets1_5_2 pkt = ClientboundPackets1_5_2.getPacket(bb.readUnsignedByte());
                    pkt.getPacketReader().accept(null, bb);
                    int len = bb.readerIndex() - readerIndex;
                    ByteBuf packet = ctx.alloc().buffer(len);
                    bb.readerIndex(readerIndex);
                    bb.readBytes(packet, len);
                    decodeOld(ctx, packet, out);
                }
            } catch (Exception e) {
                bb.readerIndex(readerIndex);
                if (bb.readableBytes() > 65535) {
                    ctx.close();
                    out.add(Unpooled.EMPTY_BUFFER);
                    return;
                }
                clientBoundPartialPacket = ctx.alloc().buffer(bb.readableBytes());
                clientBoundPartialPacket.writeBytes(bb);
            }
        }
        if (out.isEmpty()) {
            out.add(Unpooled.EMPTY_BUFFER);
        }
    }
    public void decodeOld(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        if (in.getUnsignedByte(0) == 0x14) {
            in.skipBytes(5);
            try {
                String name = Types1_6_4.STRING.read(in);
                UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
                skinsBeingFetched.add(uuid);
                ByteBuf bb = ctx.alloc().buffer();
                bb.writeByte((byte) 250);
                Types1_6_4.STRING.write(bb, "EAG|FetchSkin");
                ByteBuf bbb = ctx.alloc().buffer();
                bbb.writeByte((byte) 0);
                bbb.writeByte((byte) 0);
                bbb.writeBytes(name.getBytes(StandardCharsets.UTF_8));
                bb.writeShort(bbb.readableBytes());
                bb.writeBytes(bbb);
                bbb.release();
                ctx.writeAndFlush(new BinaryWebSocketFrame(bb));
            } catch (Exception ignored) {
            }
            in.resetReaderIndex();
        }
        if (in.getUnsignedByte(0) == 0xFD) {
            in.writerIndex(0);
            in.writeByte((byte) 0xCD);
            in.writeByte((byte) 0x00);
            ctx.writeAndFlush(new BinaryWebSocketFrame(in.retain()));
            return;
        }
        if (in.readableBytes() >= 3 && in.getUnsignedByte(0) == 250) {
            in.skipBytes(1);
            String tag;
            byte[] msg;
            try {
                tag = Types1_6_4.STRING.read(in);
                if (tag.equals("EAG|UserSkin")) {
                    if (skinsBeingFetched.isEmpty()) {
                        return;
                    }
                    msg = new byte[in.readShort()];
                    in.readBytes(msg);
                    if (msg.length < 8192) {
                        return;
                    }
                    // TODO: FIX LOL!!
                    byte[] res = new byte[msg.length > 16384 ? 16384 : 8192];
                    System.arraycopy(msg, 1, res, 0, res.length);
                    if (res.length < 16384) {
                        final int[] tmp1 = new int[2048];
                        final int[] tmp2 = new int[4096];
                        for (int i = 0; i < tmp1.length; ++i) {
                            tmp1[i] = Ints.fromBytes(res[i * 4 + 3], res[i * 4], res[i * 4 + 1], res[i * 4 + 2]);
                        }
                        SkinConverter.convert64x32to64x64(tmp1, tmp2);
                        res = new byte[16384];
                        for (int i = 0; i < tmp2.length; ++i) {
                            System.arraycopy(Ints.toByteArray(tmp2[i]), 0, res, i * 4, 4);
                        }
                    } else {
                        for (int j = 0; j < res.length; j += 4) {
                            final byte tmp3 = res[j + 3];
                            res[j + 3] = res[j + 2];
                            res[j + 2] = res[j + 1];
                            res[j + 1] = res[j];
                            res[j] = tmp3;
                        }
                    }
                    ByteBuf bb = ctx.alloc().buffer();
                    bb.writeByte((byte) 250);
                    Types1_6_4.STRING.write(bb, "EAG|Skins-1.8");
                    byte[] data = SkinPackets.makeCustomResponse(skinsBeingFetched.remove(0), 0, res);
                    bb.writeShort(data.length);
                    bb.writeBytes(data);
                    out.add(bb);
                    return;
                } else if (tag.equals("EAG|Reconnect")) {
                    msg = new byte[in.readShort()];
                    in.readBytes(msg);
                    in.resetReaderIndex();
                    in.resetWriterIndex();
                    in.writeByte((byte) 0xFF);
                    Types1_6_4.STRING.write(in, "Please use the IP: " + ChatColorUtil.COLOR_CHAR + "n" + new String(msg, StandardCharsets.UTF_8));
                    in.resetReaderIndex();
                    ctx.fireChannelRead(in.retain()).close();
                    if (!(proxyConnection instanceof ProxyConnection)) {
                        ((LegacyProxyConnection) proxyConnection).getC2P().close();
                    }
                    return;
                }
            } catch (Exception ignored) {
            }
            in.resetReaderIndex();
        }
        if (in.getByte(0) == (byte) 0x83 && in.getShort(1) != 358) {
            return;
        }
        out.add(in.retain());
    }
}