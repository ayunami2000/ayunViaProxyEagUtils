package me.ayunami2000.ayunViaProxyEagUtils;

import com.google.gson.JsonObject;
import com.viaversion.viaversion.util.GsonUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import net.raphimc.vialegacy.protocols.release.protocol1_7_2_5to1_6_4.types.Types1_6_4;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.proxy.util.ExceptionUtil;

import javax.imageio.ImageIO;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EaglerSkinHandler extends ChannelInboundHandlerAdapter {
    public static final ConcurrentHashMap<UUID, byte[]> skinCollection;
    private static final ConcurrentHashMap<UUID, byte[]> capeCollection;
    private static final ConcurrentHashMap<UUID, Long> lastSkinLayerUpdate;
    private static final int[] SKIN_DATA_SIZE;
    private static final int[] CAPE_DATA_SIZE;
    private static final ConcurrentHashMap<UUID, ChannelHandlerContext> users;
    private final String user;

    private static void sendData(final ChannelHandlerContext ctx, final String channel, final byte[] data) throws IOException {
        final ByteBuf bb = ctx.alloc().buffer();
        bb.writeByte(250);
        try {
            Types1_6_4.STRING.write(bb, channel);
        } catch (Exception e) {
            throw new IOException(e);
        }
        bb.writeShort(data.length);
        bb.writeBytes(data);
        ctx.writeAndFlush(new BinaryWebSocketFrame(bb));
    }

    public EaglerSkinHandler(final String username) {
        this.user = username;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ExceptionUtil.handleNettyException(ctx, cause, null);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object obj) throws Exception {
        final UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + this.user).getBytes(StandardCharsets.UTF_8));
        if (!EaglerSkinHandler.users.containsKey(uuid) && ctx.channel().isActive()) {
            EaglerSkinHandler.users.put(uuid, ctx);
        }
        if (obj instanceof BinaryWebSocketFrame) {
            final ByteBuf bb = ((BinaryWebSocketFrame) obj).content();
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
                    if ("EAG|MySkin".equals(tag)) {
                        if (!FunnyConfig.eaglerSkins) {
                            bb.release();
                            return;
                        }
                        if (!EaglerSkinHandler.skinCollection.containsKey(uuid)) {
                            final int t = msg[0] & 0xFF;
                            if (t < EaglerSkinHandler.SKIN_DATA_SIZE.length && msg.length == EaglerSkinHandler.SKIN_DATA_SIZE[t] + 1) {
                                EaglerSkinHandler.skinCollection.put(uuid, msg);
                            }
                        }
                        bb.release();
                        return;
                    }
                    if ("EAG|MyCape".equals(tag)) {
                        if (!FunnyConfig.eaglerSkins) {
                            bb.release();
                            return;
                        }
                        if (!EaglerSkinHandler.capeCollection.containsKey(uuid)) {
                            final int t = msg[0] & 0xFF;
                            if (t < EaglerSkinHandler.CAPE_DATA_SIZE.length && msg.length == EaglerSkinHandler.CAPE_DATA_SIZE[t] + 2) {
                                EaglerSkinHandler.capeCollection.put(uuid, msg);
                            }
                        }
                        bb.release();
                        return;
                    }
                    if ("EAG|FetchSkin".equals(tag)) {
                        if (msg.length > 2) {
                            final String fetch = new String(msg, 2, msg.length - 2, StandardCharsets.UTF_8);
                            final UUID uuidFetch = UUID.nameUUIDFromBytes(("OfflinePlayer:" + fetch).getBytes(StandardCharsets.UTF_8));
                            byte[] data;
                            if ((data = EaglerSkinHandler.skinCollection.get(uuidFetch)) != null) {
                                byte[] conc = new byte[data.length + 2];
                                conc[0] = msg[0];
                                conc[1] = msg[1];
                                System.arraycopy(data, 0, conc, 2, data.length);
                                if ((data = EaglerSkinHandler.capeCollection.get(uuidFetch)) != null) {
                                    final byte[] conc2 = new byte[conc.length + data.length];
                                    System.arraycopy(conc, 0, conc2, 0, conc.length);
                                    System.arraycopy(data, 0, conc2, conc.length, data.length);
                                    conc = conc2;
                                }
                                sendData(ctx, "EAG|UserSkin", conc);
                            } else if (FunnyConfig.premiumSkins) {
                                try {
                                    URL url = new URL("https://playerdb.co/api/player/minecraft/" + fetch);
                                    URLConnection urlConnection = url.openConnection();
                                    urlConnection.setRequestProperty("user-agent", "Mozilla/5.0 ViaProxy/" + ViaProxy.VERSION);
                                    JsonObject json = GsonUtil.getGson().fromJson(new InputStreamReader(urlConnection.getInputStream()), JsonObject.class);
                                    if (json.get("success").getAsBoolean()) {
                                        String premiumUUID = json.getAsJsonObject("data").getAsJsonObject("player").getAsJsonObject("meta").get("id").getAsString();
                                        byte[] tmp = EaglerXSkinHandler.skinService.fetchSkinPacket(uuidFetch, "https://crafatar.com/skins/" + premiumUUID);
                                        if (tmp != null) {
                                            EaglerXSkinHandler.skinService.registerEaglercraftPlayer(uuidFetch, tmp);
                                            if ((data = EaglerSkinHandler.skinCollection.get(uuidFetch)) != null) {
                                                byte[] conc = new byte[data.length + 2];
                                                conc[0] = msg[0];
                                                conc[1] = msg[1];
                                                System.arraycopy(data, 0, conc, 2, data.length);
                                                if ((data = EaglerSkinHandler.capeCollection.get(uuidFetch)) != null) {
                                                    final byte[] conc2 = new byte[conc.length + data.length];
                                                    System.arraycopy(conc, 0, conc2, 0, conc.length);
                                                    System.arraycopy(data, 0, conc2, conc.length, data.length);
                                                    conc = conc2;
                                                } else {
                                                    try {
                                                        tmp = ((DataBufferByte) ImageIO.read(new URL("https://crafatar.com/capes/" + premiumUUID)).getRaster().getDataBuffer()).getData();
                                                        data = new byte[4098];
                                                        data[0] = data[1] = 0;
                                                        // todo: figure out if we need to shuffle around colors
                                                        System.arraycopy(tmp, 0, data, 2, tmp.length);
                                                        EaglerSkinHandler.capeCollection.put(uuid, data);
                                                        final byte[] conc2 = new byte[conc.length + data.length];
                                                        System.arraycopy(conc, 0, conc2, 0, conc.length);
                                                        System.arraycopy(data, 0, conc2, conc.length, data.length);
                                                        conc = conc2;
                                                    } catch (Exception ignored) {
                                                    }
                                                }
                                                sendData(ctx, "EAG|UserSkin", conc);
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }
                        bb.release();
                        return;
                    }
                    if ("EAG|SkinLayers".equals(tag)) {
                        final long millis = System.currentTimeMillis();
                        final Long lsu = EaglerSkinHandler.lastSkinLayerUpdate.get(uuid);
                        if (lsu != null && millis - lsu < 700L) {
                            return;
                        }
                        EaglerSkinHandler.lastSkinLayerUpdate.put(uuid, millis);
                        byte[] conc2;
                        if ((conc2 = EaglerSkinHandler.capeCollection.get(uuid)) != null) {
                            conc2[1] = msg[0];
                        } else {
                            conc2 = new byte[]{2, msg[0], 0};
                            EaglerSkinHandler.capeCollection.put(uuid, conc2);
                        }
                        final ByteArrayOutputStream bao = new ByteArrayOutputStream();
                        final DataOutputStream dd = new DataOutputStream(bao);
                        dd.write(msg[0]);
                        dd.writeUTF(this.user);
                        final byte[] bpacket = bao.toByteArray();
                        for (final UUID pl : EaglerSkinHandler.users.keySet()) {
                            if (!pl.equals(uuid)) {
                                sendData(EaglerSkinHandler.users.get(pl), "EAG|SkinLayers", bpacket);
                            }
                        }
                        bb.release();
                        return;
                    }
                } catch (Throwable var18) {
                    var18.printStackTrace();
                }
            }
            bb.resetReaderIndex();
        }
        super.channelRead(ctx, obj);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        final UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + this.user).getBytes(StandardCharsets.UTF_8));
        EaglerSkinHandler.users.remove(uuid);
        EaglerSkinHandler.skinCollection.remove(uuid);
        EaglerSkinHandler.capeCollection.remove(uuid);
        EaglerSkinHandler.lastSkinLayerUpdate.remove(uuid);
    }

    static {
        skinCollection = new ConcurrentHashMap<>();
        capeCollection = new ConcurrentHashMap<>();
        lastSkinLayerUpdate = new ConcurrentHashMap<>();
        SKIN_DATA_SIZE = new int[]{8192, 16384, -9, -9, 1, 16384, -9};
        CAPE_DATA_SIZE = new int[]{4096, -9, 1};
        users = new ConcurrentHashMap<>();
    }
}
