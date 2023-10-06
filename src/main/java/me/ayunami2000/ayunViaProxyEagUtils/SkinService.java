package me.ayunami2000.ayunViaProxyEagUtils;

import com.google.common.primitives.Ints;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import net.raphimc.netminecraft.constants.MCPackets;
import net.raphimc.netminecraft.packet.PacketTypes;

import javax.imageio.ImageIO;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkinService {
    private final ConcurrentHashMap<UUID, CachedSkin> skinCache;

    public SkinService() {
        this.skinCache = new ConcurrentHashMap<>();
    }

    private static void sendData(final ChannelHandlerContext ctx, final byte[] data) {
        final ByteBuf bb = ctx.alloc().buffer();
        PacketTypes.writeVarInt(bb, MCPackets.S2C_PLUGIN_MESSAGE.getId((((EaglercraftHandler) ctx.pipeline().get("eaglercraft-handler")).version).getVersion()));
        PacketTypes.writeString(bb, "EAG|Skins-1.8");
        bb.writeBytes(data);
        ctx.writeAndFlush(new BinaryWebSocketFrame(bb));
    }

    public void processGetOtherSkin(final UUID searchUUID, final ChannelHandlerContext sender) {
        final CachedSkin cached = this.skinCache.get(searchUUID);
        if (cached != null) {
            sendData(sender, cached.packet);
        } else if (EaglerSkinHandler.skinCollection.containsKey(searchUUID)) {
            final byte[] src = EaglerSkinHandler.skinCollection.get(searchUUID);
            byte[] res = new byte[src.length - 1];
            System.arraycopy(src, 1, res, 0, res.length);
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
            if (res.length == 8192) {
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
                sendData(sender, SkinPackets.makePresetResponse(searchUUID));
                return;
            }
            sendData(sender, SkinPackets.makeCustomResponse(searchUUID, 0, res));
        } else {
            processGetOtherSkin(searchUUID, "https://crafatar.com/skins/" + searchUUID.toString(), sender);
        }
    }

    public byte[] fetchSkinPacket(final UUID searchUUID, final String skinURL) {
        // no rate-limit or size limit. it is assumed that this feature is used privately anyway.
        final CachedSkin cached = this.skinCache.get(searchUUID);
        if (cached != null) {
            return cached.packet;
        } else {
            try {
                byte[] res = ((DataBufferByte) ImageIO.read(new URL(skinURL)).getRaster().getDataBuffer()).getData();
                if (res.length == 8192) {
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
                    for (int j = 0; j < res.length; j += 4) {
                        final byte tmp3 = res[j];
                        res[j] = res[j + 1];
                        res[j + 1] = res[j + 2];
                        res[j + 2] = res[j + 3];
                        res[j + 3] = tmp3;
                    }
                }
                byte[] pkt = SkinPackets.makeCustomResponse(searchUUID, 0, res);
                registerEaglercraftPlayer(searchUUID, pkt);
                return pkt;
            } catch (IOException ignored) {
                return null;
            }
        }
    }

    public void processGetOtherSkin(final UUID searchUUID, final String skinURL, final ChannelHandlerContext sender) {
        final byte[] skin = fetchSkinPacket(searchUUID, skinURL);
        if (skin != null) {
            sendData(sender, skin);
        } else {
            sendData(sender, SkinPackets.makePresetResponse(searchUUID));
        }
    }

    public void registerEaglercraftPlayer(final UUID clientUUID, final byte[] generatedPacket) throws IOException {
        this.skinCache.put(clientUUID, new CachedSkin(clientUUID, generatedPacket));
        EaglerSkinHandler.skinCollection.put(clientUUID, newToOldSkin(generatedPacket));
    }

    public static byte[] newToOldSkin(final byte[] packet) throws IOException {
        final byte type = packet[0];
        byte[] res;
        switch (type) {
            case 1:
            case 4: {
                res = new byte[16385];
                res[0] = 1;
                final int o = type == 1 ? 16 : 0;
                final int presetId = packet[17 - o] << 24 | packet[18 - o] << 16 | packet[19 - o] << 8 | packet[20 - o];
                final InputStream stream = Main.class.getResourceAsStream("/" + presetId + ".png");
                if (stream == null) {
                    throw new IOException("Invalid skin preset: " + presetId);
                }
                System.arraycopy(((DataBufferByte) ImageIO.read(stream).getRaster().getDataBuffer()).getData(), 0, res, 1, 16384);
                for (int i = 1; i < 16385; i += 4) {
                    final byte tmp = res[i];
                    res[i] = res[i + 1];
                    res[i + 1] = res[i + 2];
                    res[i + 2] = res[i + 3];
                    res[i + 3] = tmp;
                }
                break;
            }
            case 2:
            case 5: {
                res = new byte[16385];
                res[0] = 1;
                final int o = type == 2 ? 16 : 0;
                System.arraycopy(packet, 18 - o, res, 1, 16384);
                for (int i = 1; i < 16385; i += 4) {
                    final byte tmp = res[i];
                    res[i] = res[i + 1];
                    res[i + 1] = res[i + 2];
                    res[i + 2] = res[i + 3];
                    res[i + 3] = tmp;
                }
                break;
            }
            default: {
                throw new IOException("Invalid skin packet type: " + type);
            }
        }
        return res;
    }

    public void unregisterPlayer(final UUID clientUUID) {
        this.skinCache.remove(clientUUID);
        EaglerSkinHandler.skinCollection.remove(clientUUID);
    }

    private static class CachedSkin {
        protected final UUID uuid;
        protected final byte[] packet;

        protected CachedSkin(final UUID uuid, final byte[] packet) {
            this.uuid = uuid;
            this.packet = packet;
        }
    }

    public static String sanitizeTextureURL(String url) {
        try {
            URI uri = URI.create(url);
            StringBuilder builder = new StringBuilder();
            String scheme = uri.getScheme();
            if(scheme == null) {
                return null;
            }
            String host = uri.getHost();
            if(host == null) {
                return null;
            }
            scheme = scheme.toLowerCase();
            builder.append(scheme).append("://");
            builder.append(host);
            int port = uri.getPort();
            if(port != -1) {
                switch(scheme) {
                    case "http":
                        if(port == 80) {
                            port = -1;
                        }
                        break;
                    case "https":
                        if(port == 443) {
                            port = -1;
                        }
                        break;
                    default:
                        return null;
                }
                if(port != -1) {
                    builder.append(":").append(port);
                }
            }
            String path = uri.getRawPath();
            if(path != null) {
                if(path.contains("//")) {
                    path = String.join("/", path.split("[\\/]+"));
                }
                int len = path.length();
                if(len > 1 && path.charAt(len - 1) == '/') {
                    path = path.substring(0, len - 1);
                }
                builder.append(path);
            }
            return builder.toString();
        }catch(Throwable t) {
            return null;
        }
    }
}
