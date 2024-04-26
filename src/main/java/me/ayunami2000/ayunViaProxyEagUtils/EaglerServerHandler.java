package me.ayunami2000.ayunViaProxyEagUtils;

import com.google.common.primitives.Ints;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.util.ChatColorUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AttributeKey;
import net.jodah.expiringmap.ExpiringMap;
import net.raphimc.netminecraft.constants.MCPackets;
import net.raphimc.netminecraft.netty.connection.NetClient;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.vialegacy.protocols.release.protocol1_6_1to1_5_2.ClientboundPackets1_5_2;
import net.raphimc.vialegacy.protocols.release.protocol1_6_1to1_5_2.ServerboundPackets1_5_2;
import net.raphimc.vialegacy.protocols.release.protocol1_7_2_5to1_6_4.types.Types1_6_4;
import net.raphimc.viaproxy.proxy.session.LegacyProxyConnection;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import net.raphimc.viaproxy.proxy.util.ExceptionUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class EaglerServerHandler extends MessageToMessageCodec<WebSocketFrame, ByteBuf> {
    private final ProtocolVersion version;
    private final String password;
    private final NetClient proxyConnection;
    private final Map<UUID, String> uuidStringMap = new HashMap<>();
    private final Map<Integer, UUID> eidUuidMap = new HashMap<>();
    private final ExpiringMap<Short, UUID> skinsBeingFetched = ExpiringMap.builder().expiration(1, TimeUnit.MINUTES).build();
    private short skinFetchCounter = 0;
    private ByteBuf serverBoundPartialPacket = Unpooled.EMPTY_BUFFER;
    private ByteBuf clientBoundPartialPacket = Unpooled.EMPTY_BUFFER;
    public EaglerServerHandler(NetClient proxyConnection, String password) {
        this.version = proxyConnection instanceof ProxyConnection ? ((ProxyConnection) proxyConnection).getServerVersion() : LegacyProtocolVersion.r1_5_2;
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
        if (handshakeState < 0) {
            out.add(Unpooled.EMPTY_BUFFER);
            return;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_7_2)) {
            if (in.readableBytes() >= 2 && in.getUnsignedByte(0) == 0xFE && in.getUnsignedByte(1) == 0x01) {
                handshakeState = -1;
                out.add(new TextWebSocketFrame("Accept: MOTD"));
                return;
            }

            if (handshakeState == 0) {
                handshakeState = 1;

                int id = PacketTypes.readVarInt(in);
                if (id == 0x00) {
                    PacketTypes.readVarInt(in);
                    PacketTypes.readString(in, 32767);
                    in.readUnsignedShort();
                    int nextState = PacketTypes.readVarInt(in);
                    if (nextState == 1) {
                        handshakeState = -2;
                        out.add(new TextWebSocketFrame("Accept: MOTD"));
                        return;
                    }
                }
                in.resetReaderIndex();

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
                    if (!ctx.channel().isOpen()) {
                        out.add(Unpooled.EMPTY_BUFFER);
                        return;
                    }
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
        if (in.readableBytes() >= 2 && in.getUnsignedByte(0) == 0xFE && in.getUnsignedByte(1) == 0x01) {
            handshakeState = -1;
            out.add(new TextWebSocketFrame("Accept: MOTD"));
            return;
        }
        if (in.readableBytes() >= 2 && in.getUnsignedByte(0) == 2) {
            in.setByte(1, in.getUnsignedByte(1) + 8);
            out.add(new BinaryWebSocketFrame(in.retain()));

            Channel c2p;
            if (proxyConnection instanceof ProxyConnection) {
                c2p = ((ProxyConnection) proxyConnection).getC2P();
            } else {
                c2p = ((LegacyProxyConnection) proxyConnection).getC2P();
            }
            if (c2p.hasAttr(EaglercraftHandler.profileDataKey)) {
                EaglercraftHandler.ProfileData profileData = c2p.attr(EaglercraftHandler.profileDataKey).get();
                if (profileData.type.equals("skin_v1")) {
                    int packetType = profileData.data[0] & 0xFF;
                    if (packetType == 1 || packetType == 2) {
                        try {
                            byte[] res = SkinService.newToOldSkin(profileData.data);
                            ByteBuf bb = ctx.alloc().buffer();
                            bb.writeByte((byte) 250);
                            Types1_6_4.STRING.write(bb, "EAG|MySkin");
                            bb.writeShort(res.length);
                            bb.writeBytes(res);
                            out.add(new BinaryWebSocketFrame(bb));
                        } catch (Exception ignored) {}
                    }

                }
            }
            return;
        }
        if (in.getUnsignedByte(0) == 0xFD) {
            return;
        }
        if (proxyConnection instanceof ProxyConnection) {
            if (in.readableBytes() >= 3 && in.getUnsignedByte(0) == 250) {
                in.skipBytes(1);
                String tag;
                byte[] msg;
                try {
                    tag = Types1_6_4.STRING.read(in);
                    if (tag.equals("EAG|Skins-1.8")) {
                        msg = new byte[in.readShort()];
                        in.readBytes(msg);
                        if (msg.length == 0) {
                            throw new IOException("Zero-length packet recieved");
                        }
                        final int packetId = msg[0] & 0xFF;
                        switch (packetId) {
                            case 3: {
                                if (msg.length != 17) {
                                    throw new IOException("Invalid length " + msg.length + " for skin request packet");
                                }
                                final UUID searchUUID = SkinPackets.bytesToUUID(msg, 1);
                                if (uuidStringMap.containsKey(searchUUID)) {
                                    short id = skinFetchCounter++;
                                    skinsBeingFetched.put(id, searchUUID);
                                    String name = uuidStringMap.get(searchUUID);
                                    ByteBuf bb = ctx.alloc().buffer();
                                    bb.writeByte((byte) 250);
                                    Types1_6_4.STRING.write(bb, "EAG|FetchSkin");
                                    ByteBuf bbb = ctx.alloc().buffer();
                                    bbb.writeByte((byte) ((id >> 8) & 0xFF));
                                    bbb.writeByte((byte) (id & 0xFF));
                                    bbb.writeBytes(name.getBytes(StandardCharsets.UTF_8));
                                    bb.writeShort(bbb.readableBytes());
                                    bb.writeBytes(bbb);
                                    bbb.release();
                                    out.add(new BinaryWebSocketFrame(bb));
                                }
                                break;
                            }
                            case 6: {
                                break;
                            }
                            default: {
                                throw new IOException("Unknown packet type " + packetId);
                            }
                        }
                        return;
                    }
                } catch (Exception ignored) {
                }
                in.resetReaderIndex();
            }
        }
        out.add(new BinaryWebSocketFrame(in.retain()));
    }

    private static class ServerInfo {
        public final String motd;
        public final int online;
        public final int max;
        public final String[] players;
        public ServerInfo(String motd, int online, int max, String[] players) {
            this.motd = motd;
            this.online = online;
            this.max = max;
            this.players = players;
        }
    }

    private static final AttributeKey<ServerInfo> serverInfoKey = AttributeKey.newInstance("server-info");
    public static final AttributeKey<ByteBuf> eagIconKey = AttributeKey.newInstance("eag-icon");
    private static final AttributeKey<ByteBuf> eagLegacyStatusKey = AttributeKey.newInstance("eag-legacy-status");

    @Override
    public void decode(ChannelHandlerContext ctx, WebSocketFrame in, List<Object> out) {
        if (in instanceof TextWebSocketFrame && handshakeState < 0) {
            JsonObject json = JsonParser.parseString(((TextWebSocketFrame) in).text()).getAsJsonObject();
            if (!(json.has("data") && json.get("data").isJsonObject() && (json = json.getAsJsonObject("data")).has("motd") && json.get("motd").isJsonArray() && json.has("icon") && json.get("icon").isJsonPrimitive() && json.has("online") && json.get("online").isJsonPrimitive() && json.has("max") && json.get("max").isJsonPrimitive() && json.has("players") && json.get("players").isJsonArray())) {
                out.add(Unpooled.EMPTY_BUFFER);
                return;
            }
            JsonArray motd = json.getAsJsonArray("motd");
            StringBuilder motdSb = new StringBuilder();
            for (JsonElement line : motd) {
                motdSb.append(line.getAsString()).append("\n");
            }
            if (motdSb.length() > 0) {
                motdSb.setLength(motdSb.length() - 1);
            }
            boolean icon = json.get("icon").getAsBoolean();
            int online = json.get("online").getAsInt();
            int max = json.get("max").getAsInt();
            JsonArray players = json.getAsJsonArray("players");
            if (handshakeState == -1) {
                ByteBuf bb = ctx.alloc().buffer();
                bb.writeByte((byte) 0xFF);
                StringBuilder sb = new StringBuilder("\u00A71\0");
                sb.append(version.getVersion()).append("\0");
                sb.append(version.getName()).append("\0");
                sb.append(motdSb).append("\0");
                sb.append(online).append("\0");
                sb.append(max);
                try {
                    Types1_6_4.STRING.write(bb, sb.toString());
                } catch (Exception ignored) {
                }
                if (icon) {
                    ctx.channel().attr(eagLegacyStatusKey).set(bb);
                    handshakeState = -3;
                } else {
                    out.add(bb);
                }
            } else if (icon) {
                List<String> playerList = new ArrayList<>();
                for (JsonElement player : players) {
                    playerList.add(player.toString());
                }
                ctx.channel().attr(serverInfoKey).set(new ServerInfo(motdSb.toString(), online, max, playerList.toArray(new String[0])));
            } else {
                JsonObject resp = new JsonObject();
                JsonObject versionObj = new JsonObject();
                versionObj.addProperty("name", version.getName());
                versionObj.addProperty("protocol", version.getVersion());
                resp.add("version", versionObj);
                JsonObject playersObj = new JsonObject();
                playersObj.addProperty("max", max);
                playersObj.addProperty("online", online);
                if (!players.isEmpty()) {
                    JsonArray sampleArr = new JsonArray();
                    for (JsonElement player : players) {
                        JsonObject playerObj = new JsonObject();
                        playerObj.addProperty("name", player.toString());
                        playerObj.addProperty("id", UUID.nameUUIDFromBytes(("OfflinePlayer:" + player).getBytes(StandardCharsets.UTF_8)).toString());
                        sampleArr.add(playerObj);
                    }
                    playersObj.add("sample", sampleArr);
                }
                resp.add("players", playersObj);
                JsonObject descriptionObj = new JsonObject();
                descriptionObj.addProperty("text", motdSb.toString());
                resp.add("description", descriptionObj);
                ByteBuf bb = ctx.alloc().buffer();
                PacketTypes.writeVarInt(bb, 0);
                PacketTypes.writeString(bb, resp.toString());
                out.add(bb);
                handshakeState = -1;
            }
        }
        if (!(in instanceof BinaryWebSocketFrame)) {
            if (out.isEmpty()) {
                out.add(Unpooled.EMPTY_BUFFER);
            }
            return;
        }
        if (handshakeState < 0) {
            if (handshakeState == -3) {
                handshakeState = -1;
                if (proxyConnection instanceof ProxyConnection) {
                    ((ProxyConnection) proxyConnection).getC2P().attr(eagIconKey).set(in.content().retain());
                } else {
                    ((LegacyProxyConnection) proxyConnection).getC2P().attr(eagIconKey).set(in.content().retain());
                }
                out.add(ctx.channel().attr(eagLegacyStatusKey).getAndSet(null));
                return;
            }
            if (handshakeState == -1) {
                out.add(Unpooled.EMPTY_BUFFER);
                return;
            }
            ServerInfo serverInfo = ctx.channel().attr(serverInfoKey).getAndSet(null);
            JsonObject resp = new JsonObject();
            JsonObject versionObj = new JsonObject();
            versionObj.addProperty("name", version.getName());
            versionObj.addProperty("protocol", version.getVersion());
            resp.add("version", versionObj);
            JsonObject playersObj = new JsonObject();
            playersObj.addProperty("max", serverInfo.max);
            playersObj.addProperty("online", serverInfo.online);
            if (serverInfo.players.length > 0) {
                JsonArray sampleArr = new JsonArray();
                for (String player : serverInfo.players) {
                    JsonObject playerObj = new JsonObject();
                    playerObj.addProperty("name", player);
                    playerObj.addProperty("id", UUID.nameUUIDFromBytes(("OfflinePlayer:" + player).getBytes(StandardCharsets.UTF_8)).toString());
                    sampleArr.add(playerObj);
                }
                playersObj.add("sample", sampleArr);
            }
            resp.add("players", playersObj);
            JsonObject descriptionObj = new JsonObject();
            descriptionObj.addProperty("text", serverInfo.motd);
            resp.add("description", descriptionObj);
            if (in.content().readableBytes() == 16384) {
                BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_4BYTE_ABGR);
                byte[] pixels = new byte[16384];
                for (int i = 0; i < 4096; i++) {
                    pixels[i * 4] = in.content().getByte(i * 4 + 3);
                    pixels[i * 4 + 1] = in.content().getByte(i * 4 + 2);
                    pixels[i * 4 + 2] = in.content().getByte(i * 4 + 1);
                    pixels[i * 4 + 3] = in.content().getByte(i * 4);
                }
                image.setData(Raster.createRaster(image.getSampleModel(), new DataBufferByte(pixels, 16384), new Point()));
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                try {
                    ImageIO.write(image, "png", os);
                    resp.addProperty("favicon", "data:image/png;base64," + Base64.getEncoder().encodeToString(os.toByteArray()));
                } catch (IOException ignored) {
                }
            }
            ByteBuf bb = ctx.alloc().buffer();
            PacketTypes.writeVarInt(bb, 0);
            PacketTypes.writeString(bb, resp.toString());
            out.add(bb);
            handshakeState = -1;
            return;
        }
        if (version.newerThanOrEqualTo(ProtocolVersion.v1_7_2)) {
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
            bb.writeBytes(in.content()); // todo: do i need to reset this??????
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
        if (proxyConnection instanceof ProxyConnection) {
            if (in.getUnsignedByte(0) == 0x14) {
                try {
                    in.skipBytes(1);
                    int eid = in.readInt();
                    String name = Types1_6_4.STRING.read(in);
                    UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
                    eidUuidMap.put(eid, uuid);
                    uuidStringMap.put(uuid, name);
                } catch (Exception ignored) {
                }
                in.resetReaderIndex();
            }
            if (in.getUnsignedByte(0) == 0x1D) {
                try {
                    in.skipBytes(1);
                    short count = in.readUnsignedByte();
                    for (short i = 0; i < count; i++) {
                        int eid = in.readInt();
                        if (eidUuidMap.containsKey(eid)) {
                            uuidStringMap.remove(eidUuidMap.remove(eid));
                        }
                    }
                } catch (Exception ignored) {
                }
                in.resetReaderIndex();
            }
            if (in.getUnsignedByte(0) == 0x09) {
                eidUuidMap.clear();
                uuidStringMap.clear();
            }
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
                if (proxyConnection instanceof ProxyConnection && tag.equals("EAG|UserSkin")) {
                    msg = new byte[in.readShort()];
                    in.readBytes(msg);
                    short id = (short) ((msg[0] << 8) + msg[1]);
                    if (!skinsBeingFetched.containsKey(id)) {
                        return;
                    }
                    byte[] res = new byte[Math.min(16384, msg.length - 3)];
                    System.arraycopy(msg, 3, res, 0, res.length);
                    if (res.length <= 16) {
                        int presetId = res[0] & 0xFF;
                        InputStream stream = Main.class.getResourceAsStream("/n" + presetId + ".png");
                        if (stream != null) {
                            try {
                                res = ((DataBufferByte) ImageIO.read(stream).getRaster().getDataBuffer()).getData();
                                for (int i = 0; i < res.length; i += 4) {
                                    final byte tmp = res[i];
                                    res[i] = res[i + 1];
                                    res[i + 1] = res[i + 2];
                                    res[i + 2] = res[i + 3];
                                    res[i + 3] = tmp;
                                }
                            } catch (IOException ignored) {}
                        }
                    }
                    if (res.length >= 8192 && res.length < 16384) {
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
                    } else if (res.length == 16384) {
                        for (int j = 0; j < res.length; j += 4) {
                            final byte tmp3 = res[j + 3];
                            res[j + 3] = res[j + 2];
                            res[j + 2] = res[j + 1];
                            res[j + 1] = res[j];
                            res[j] = tmp3;
                        }
                    } else {
                        ByteBuf bb = ctx.alloc().buffer();
                        bb.writeByte((byte) 250);
                        Types1_6_4.STRING.write(bb, "EAG|Skins-1.8");
                        byte[] data = SkinPackets.makePresetResponse(skinsBeingFetched.remove(id));
                        bb.writeShort(data.length);
                        bb.writeBytes(data);
                        out.add(bb);
                        return;
                    }
                    UUID uuid = skinsBeingFetched.remove(id);
                    ByteBuf bb = ctx.alloc().buffer();
                    bb.writeByte((byte) 250);
                    Types1_6_4.STRING.write(bb, "EAG|Skins-1.8");
                    byte[] data = SkinPackets.makeCustomResponse(uuid, 0, res);
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