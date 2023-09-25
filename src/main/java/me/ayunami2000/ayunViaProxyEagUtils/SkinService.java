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
import java.util.HashMap;
import java.util.Map;
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
            } else {
                for (int j = 0; j < res.length; j += 4) {
                    final byte tmp3 = res[j + 3];
                    res[j + 3] = res[j + 2];
                    res[j + 2] = res[j + 1];
                    res[j + 1] = res[j];
                    res[j] = tmp3;
                }
            }
            sendData(sender, SkinPackets.makeCustomResponse(searchUUID, 0, res));
        } else {
            sendData(sender, SkinPackets.makePresetResponse(searchUUID));
        }
    }

    public void registerEaglercraftPlayer(final UUID clientUUID, final byte[] generatedPacket) throws IOException {
        this.skinCache.put(clientUUID, new CachedSkin(clientUUID, generatedPacket));
        EaglerSkinHandler.skinCollection.put(clientUUID, newToOldSkin(generatedPacket));
    }

    private static byte[] newToOldSkin(final byte[] packet) throws IOException {
        final byte type = packet[0];
        byte[] res;
        switch (type) {
            case 4: {
                res = new byte[16385];
                res[0] = 1;
                final int presetId = packet[17] << 24 | packet[18] << 16 | packet[19] << 8 | packet[20];
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
            case 5: {
                res = new byte[16385];
                res[0] = 1;
                System.arraycopy(packet, 18, res, 1, 16384);
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
}
