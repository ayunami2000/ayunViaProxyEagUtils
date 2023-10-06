package me.ayunami2000.ayunViaProxyEagUtils;

import com.google.common.net.HostAndPort;
import com.viaversion.viaversion.libs.gson.JsonArray;
import com.viaversion.viaversion.libs.gson.JsonObject;
import com.viaversion.viaversion.libs.gson.JsonParser;
import com.viaversion.viaversion.protocols.base.ClientboundStatusPackets;
import com.viaversion.viaversion.protocols.base.ServerboundHandshakePackets;
import com.viaversion.viaversion.protocols.base.ServerboundLoginPackets;
import com.viaversion.viaversion.protocols.base.ServerboundStatusPackets;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.AttributeKey;
import net.lenni0451.mcstructs.text.serializer.TextComponentSerializer;
import net.raphimc.netminecraft.constants.ConnectionState;
import net.raphimc.netminecraft.constants.MCPackets;
import net.raphimc.netminecraft.constants.MCPipeline;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.vialegacy.protocols.release.protocol1_6_1to1_5_2.ServerboundPackets1_5_2;
import net.raphimc.vialegacy.protocols.release.protocol1_7_2_5to1_6_4.types.Types1_6_4;
import net.raphimc.vialoader.util.VersionEnum;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.proxy.client2proxy.Client2ProxyChannelInitializer;
import net.raphimc.viaproxy.proxy.util.ExceptionUtil;
import net.raphimc.viaproxy.util.logging.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class EaglercraftHandler extends MessageToMessageCodec<WebSocketFrame, ByteBuf> {
    public static class ProfileData {
        public final String type;
        public final byte[] data;
        public ProfileData(String type, byte[] data) {
            this.type = type;
            this.data = data;
        }
    }
    public static final AttributeKey<ProfileData> profileDataKey = AttributeKey.newInstance("eagx-profile-data");
    private HostAndPort host;
    public State state;
    public VersionEnum version;
    public int pluginMessageId;
    public String username;

    public EaglercraftHandler() {
        this.state = State.PRE_HANDSHAKE;
    }

    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        ctx.channel().attr(MCPipeline.COMPRESSION_THRESHOLD_ATTRIBUTE_KEY).set(-2);
        if (ctx.pipeline().get(MCPipeline.SIZER_HANDLER_NAME) != null) {
            ctx.pipeline().remove(MCPipeline.SIZER_HANDLER_NAME);
        }
        super.channelActive(ctx);
    }

    protected void encode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) throws IOException {
        if (this.state == State.STATUS) {
            final int packetId = PacketTypes.readVarInt(in);
            if (packetId != ClientboundStatusPackets.STATUS_RESPONSE.getId()) {
                throw new IllegalStateException("Unexpected packet id " + packetId);
            }
            final JsonObject root = JsonParser.parseString(PacketTypes.readString(in, 32767)).getAsJsonObject();
            final JsonObject response = new JsonObject();
            response.addProperty("name", "ViaProxy");
            response.addProperty("brand", "ViaProxy");
            if (root.has("version")) {
                response.add("vers", root.getAsJsonObject("version").get("name"));
            } else {
                response.addProperty("vers", "Unknown");
            }
            response.addProperty("cracked", Boolean.TRUE);
            response.addProperty("secure", Boolean.FALSE);
            response.addProperty("time", System.currentTimeMillis());
            response.addProperty("uuid", UUID.randomUUID().toString());
            response.addProperty("type", "motd");
            final JsonObject data = new JsonObject();
            data.addProperty("cache", Boolean.FALSE);
            final JsonArray motd = new JsonArray();
            if (root.has("description")) {
                final String[] split = TextComponentSerializer.V1_8.deserialize(root.get("description").toString()).asLegacyFormatString().split("\n");
                for (final String motdLine : split) {
                    motd.add(motdLine);
                }
            }
            data.add("motd", motd);
            boolean hasIcon = root.has("favicon") || ctx.channel().hasAttr(EaglerServerHandler.eagIconKey);
            data.addProperty("icon", hasIcon);
            if (root.has("players")) {
                final JsonObject javaPlayers = root.getAsJsonObject("players");
                data.add("online", javaPlayers.get("online"));
                data.add("max", javaPlayers.get("max"));
                final JsonArray players = new JsonArray();
                if (javaPlayers.has("sample")) {
                    javaPlayers.getAsJsonArray("sample").forEach(player -> players.add(TextComponentSerializer.V1_8.deserialize(player.getAsJsonObject().get("name").getAsString()).asLegacyFormatString()));
                }
                data.add("players", players);
            }
            response.add("data", data);
            out.add(new TextWebSocketFrame(response.toString()));
            if (root.has("favicon")) {
                final BufferedImage icon = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(root.get("favicon").getAsString().substring(22).replace("\n", "").getBytes(StandardCharsets.UTF_8))));
                final int[] pixels = icon.getRGB(0, 0, 64, 64, null, 0, 64);
                final byte[] iconPixels = new byte[16384];
                for (int i = 0; i < 4096; ++i) {
                    iconPixels[i * 4] = (byte) (pixels[i] >> 16 & 0xFF);
                    iconPixels[i * 4 + 1] = (byte) (pixels[i] >> 8 & 0xFF);
                    iconPixels[i * 4 + 2] = (byte) (pixels[i] & 0xFF);
                    iconPixels[i * 4 + 3] = (byte) (pixels[i] >> 24 & 0xFF);
                }
                out.add(new BinaryWebSocketFrame(ctx.alloc().buffer().writeBytes(iconPixels)));
            } else if (hasIcon) {
                out.add(new BinaryWebSocketFrame(ctx.channel().attr(EaglerServerHandler.eagIconKey).get()));
            }
        } else {
            if (this.state != State.LOGIN_COMPLETE) {
                throw new IllegalStateException("Cannot send packets before login is completed");
            }
            out.add(new BinaryWebSocketFrame(in.retain()));
        }
    }

    protected void decode(final ChannelHandlerContext ctx, final WebSocketFrame in, final List<Object> out) throws Exception {
        if (in instanceof BinaryWebSocketFrame) {
            final ByteBuf data = in.content();
            switch (this.state) {
                case PRE_HANDSHAKE: {
                    if (data.readableBytes() >= 2 && data.getByte(0) == 2 && data.getByte(1) == 69) {
                        data.setByte(1, 61);
                        this.state = State.LOGIN_COMPLETE;
                        this.version = VersionEnum.r1_5_2;
                        out.add(data.retain());
                        break;
                    }
                    this.state = State.HANDSHAKE;
                }
                case HANDSHAKE: {
                    final int packetId = data.readUnsignedByte();
                    if (packetId != 1) {
                        throw new IllegalStateException("Unexpected packet id " + packetId + " in state " + this.state);
                    }
                    int eaglercraftVersion = data.readUnsignedByte();
                    int minecraftVersion;
                    if (eaglercraftVersion == 1) {
                        minecraftVersion = data.readUnsignedByte();
                    } else {
                        if (eaglercraftVersion != 2) {
                            throw new IllegalArgumentException("Unknown Eaglercraft version: " + eaglercraftVersion);
                        }
                        int count = data.readUnsignedShort();
                        final List<Integer> eaglercraftVersions = new ArrayList<>(count);
                        for (int i = 0; i < count; ++i) {
                            eaglercraftVersions.add(data.readUnsignedShort());
                        }
                        if (!eaglercraftVersions.contains(2) && !eaglercraftVersions.contains(3)) {
                            Logger.LOGGER.error("No supported eaglercraft versions found");
                            ctx.close();
                            return;
                        }
                        if (eaglercraftVersions.contains(3)) {
                            eaglercraftVersion = 3;
                        }
                        count = data.readUnsignedShort();
                        final List<Integer> minecraftVersions = new ArrayList<>(count);
                        for (int j = 0; j < count; ++j) {
                            minecraftVersions.add(data.readUnsignedShort());
                        }
                        if (minecraftVersions.size() != 1) {
                            Logger.LOGGER.error("No supported minecraft versions found");
                            ctx.close();
                        }
                        minecraftVersion = minecraftVersions.get(0);
                    }
                    final String clientBrand = data.readCharSequence(data.readUnsignedByte(), StandardCharsets.US_ASCII).toString();
                    final String clientVersionString = data.readCharSequence(data.readUnsignedByte(), StandardCharsets.US_ASCII).toString();
                    if (eaglercraftVersion >= 2) {
                        data.skipBytes(1);
                        data.skipBytes(data.readUnsignedByte());
                    }
                    if (data.isReadable()) {
                        throw new IllegalArgumentException("Too much data in packet: " + data.readableBytes() + " bytes");
                    }
                    Logger.LOGGER.info("Eaglercraft client connected: " + clientBrand + " " + clientVersionString);
                    this.state = State.HANDSHAKE_COMPLETE;
                    this.version = VersionEnum.fromProtocolId(minecraftVersion);
                    if (this.version.equals(VersionEnum.UNKNOWN)) {
                        Logger.LOGGER.error("Unsupported protocol version: " + minecraftVersion);
                        ctx.close();
                        return;
                    }
                    final ByteBuf response = ctx.alloc().buffer();
                    response.writeByte(2);
                    if (eaglercraftVersion == 1) {
                        response.writeByte(1);
                    } else {
                        response.writeShort(eaglercraftVersion);
                        response.writeShort(minecraftVersion);
                    }
                    response.writeByte("ViaProxy".length()).writeCharSequence("ViaProxy", StandardCharsets.US_ASCII);
                    response.writeByte(ViaProxy.VERSION.length()).writeCharSequence(ViaProxy.VERSION, StandardCharsets.US_ASCII);
                    response.writeByte(0);
                    response.writeShort(0);
                    ctx.writeAndFlush(new BinaryWebSocketFrame(response));
                    break;
                }
                case HANDSHAKE_COMPLETE: {
                    final int packetId = data.readUnsignedByte();
                    if (packetId != 4) {
                        throw new IllegalStateException("Unexpected packet id " + packetId + " in state " + this.state);
                    }
                    final String username = data.readCharSequence(data.readUnsignedByte(), StandardCharsets.US_ASCII).toString();
                    data.readCharSequence(data.readUnsignedByte(), StandardCharsets.US_ASCII);
                    data.skipBytes(data.readUnsignedByte());
                    if (data.isReadable()) {
                        throw new IllegalArgumentException("Too much data in packet: " + data.readableBytes() + " bytes");
                    }
                    this.state = State.LOGIN;
                    this.username = username;
                    final UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
                    final ByteBuf response2 = ctx.alloc().buffer();
                    response2.writeByte(5);
                    response2.writeByte(username.length()).writeCharSequence(username, StandardCharsets.US_ASCII);
                    response2.writeLong(uuid.getMostSignificantBits()).writeLong(uuid.getLeastSignificantBits());
                    ctx.writeAndFlush(new BinaryWebSocketFrame(response2));
                    break;
                }
                case LOGIN: {
                    final int packetId = data.readUnsignedByte();
                    if (packetId == 7) {
                        String type = data.readCharSequence(data.readUnsignedByte(), StandardCharsets.US_ASCII).toString();
                        final byte[] dataBytes = new byte[data.readUnsignedShort()];
                        data.readBytes(dataBytes);
                        if (data.isReadable()) {
                            throw new IllegalArgumentException("Too much data in packet: " + data.readableBytes() + " bytes");
                        }
                        ctx.channel().attr(profileDataKey).set(new ProfileData(type, dataBytes));
                    } else {
                        if (packetId != 8) {
                            throw new IllegalStateException("Unexpected packet id " + packetId + " in state " + this.state);
                        }
                        if (data.isReadable()) {
                            throw new IllegalArgumentException("Too much data in packet: " + data.readableBytes() + " bytes");
                        }
                        this.state = State.LOGIN_COMPLETE;
                        this.pluginMessageId = MCPackets.C2S_PLUGIN_MESSAGE.getId(this.version.getVersion());
                        if (this.pluginMessageId == -1) {
                            Logger.LOGGER.error("Unsupported protocol version: " + this.version.getVersion());
                            ctx.close();
                            return;
                        }
                        if (ctx.pipeline().get(Client2ProxyChannelInitializer.LEGACY_PASSTHROUGH_INITIAL_HANDLER_NAME) != null) {
                            ctx.pipeline().remove(Client2ProxyChannelInitializer.LEGACY_PASSTHROUGH_INITIAL_HANDLER_NAME);
                        }
                        out.add(this.writeHandshake(ctx.alloc().buffer(), ConnectionState.LOGIN));
                        final ByteBuf loginHello = ctx.alloc().buffer();
                        PacketTypes.writeVarInt(loginHello, ServerboundLoginPackets.HELLO.getId());
                        PacketTypes.writeString(loginHello, this.username);
                        out.add(loginHello);
                        final ByteBuf response3 = ctx.alloc().buffer();
                        response3.writeByte(9);
                        ctx.writeAndFlush(new BinaryWebSocketFrame(response3));
                    }
                    break;
                }
                case LOGIN_COMPLETE: {
                    if (this.version.equals(VersionEnum.r1_5_2)) {
                        final int packetId = data.readUnsignedByte();
                        if (packetId == ServerboundPackets1_5_2.SHARED_KEY.getId()) {
                            ctx.channel().writeAndFlush(new BinaryWebSocketFrame(data.readerIndex(0).retain()));
                            break;
                        }
                        if (!ctx.channel().hasAttr(Main.secureWs)) {
                            if (packetId == ServerboundPackets1_5_2.PLUGIN_MESSAGE.getId() && Types1_6_4.STRING.read(data).startsWith("EAG|")) {
                                break;
                            }
                        }
                    } else if (this.version.isNewerThanOrEqualTo(VersionEnum.r1_7_2tor1_7_5) && !ctx.channel().hasAttr(Main.secureWs)) {
                        final int packetId = PacketTypes.readVarInt(data);
                        if (packetId == this.pluginMessageId && PacketTypes.readString(data, 32767).startsWith("EAG|")) {
                            break;
                        }
                    }
                    out.add(data.readerIndex(0).retain());
                    break;
                }
                default: {
                    throw new IllegalStateException("Unexpected binary frame in state " + this.state);
                }
            }
        } else {
            if (!(in instanceof TextWebSocketFrame)) {
                throw new UnsupportedOperationException("Unsupported frame type: " + in.getClass().getName());
            }
            final String text = ((TextWebSocketFrame) in).text();
            if (this.state != State.PRE_HANDSHAKE) {
                throw new IllegalStateException("Unexpected text frame in state " + this.state);
            }
            if (!text.equalsIgnoreCase("accept: motd")) {
                ctx.close();
                return;
            }
            this.state = State.STATUS;
            this.version = VersionEnum.r1_8;
            if (ctx.pipeline().get(Client2ProxyChannelInitializer.LEGACY_PASSTHROUGH_INITIAL_HANDLER_NAME) != null) {
                ctx.pipeline().remove(Client2ProxyChannelInitializer.LEGACY_PASSTHROUGH_INITIAL_HANDLER_NAME);
            }
            out.add(this.writeHandshake(ctx.alloc().buffer(), ConnectionState.STATUS));
            final ByteBuf statusRequest = ctx.alloc().buffer();
            PacketTypes.writeVarInt(statusRequest, ServerboundStatusPackets.STATUS_REQUEST.getId());
            out.add(statusRequest);
        }
    }

    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            final WebSocketServerProtocolHandler.HandshakeComplete handshake = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            if (!handshake.requestHeaders().contains("Host")) {
                ctx.close();
                return;
            }
            this.host = HostAndPort.fromString(handshake.requestHeaders().get("Host").replaceAll("__", ".")).withDefaultPort(80);
        }
        super.userEventTriggered(ctx, evt);
    }

    private ByteBuf writeHandshake(final ByteBuf byteBuf, final ConnectionState state) {
        PacketTypes.writeVarInt(byteBuf, ServerboundHandshakePackets.CLIENT_INTENTION.getId());
        PacketTypes.writeVarInt(byteBuf, this.version.getVersion());
        PacketTypes.writeString(byteBuf, this.host.getHost());
        byteBuf.writeShort(this.host.getPort());
        int i;
        switch (state) {
            case PLAY:
                i = 0;
                break;
            case STATUS:
                i = 1;
                break;
            case LOGIN:
                i = 2;
                break;
            default:
                i = -1;
        }
        PacketTypes.writeVarInt(byteBuf, i);
        return byteBuf;
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ExceptionUtil.handleNettyException(ctx, cause, null);
    }

    public enum State {
        STATUS,
        PRE_HANDSHAKE,
        HANDSHAKE,
        HANDSHAKE_COMPLETE,
        LOGIN,
        LOGIN_COMPLETE
    }
}
