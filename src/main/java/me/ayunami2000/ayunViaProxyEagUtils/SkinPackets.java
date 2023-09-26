package me.ayunami2000.ayunViaProxyEagUtils;

import io.netty.channel.ChannelHandlerContext;
import net.raphimc.viaproxy.cli.options.Options;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;

public class SkinPackets {
    public static void processPacket(final byte[] data, final ChannelHandlerContext sender, final SkinService skinService) throws IOException {
        if (data.length == 0) {
            throw new IOException("Zero-length packet recieved");
        }
        final int packetId = data[0] & 0xFF;
        try {
            switch (packetId) {
                case 3: {
                    processGetOtherSkin(data, sender, skinService);
                    break;
                }
                case 6: {
                    if (EaglerXSkinHandler.skinService.loadPremiumSkins) {
                        processGetOtherSkinByURL(data, sender, skinService);
                    }
                    break;
                }
                default: {
                    throw new IOException("Unknown packet type " + packetId);
                }
            }
        } catch (IOException ex) {
            throw ex;
        } catch (Throwable t) {
            throw new IOException("Unhandled exception handling packet type " + packetId, t);
        }
    }

    private static void processGetOtherSkin(final byte[] data, final ChannelHandlerContext sender, final SkinService skinService) throws IOException {
        if (data.length != 17) {
            throw new IOException("Invalid length " + data.length + " for skin request packet");
        }
        final UUID searchUUID = bytesToUUID(data, 1);
        skinService.processGetOtherSkin(searchUUID, sender);
    }

    private static void processGetOtherSkinByURL(byte[] data, ChannelHandlerContext sender, SkinService skinService) throws IOException {
        if(data.length < 20) {
            throw new IOException("Invalid length " + data.length + " for skin request packet");
        }
        UUID searchUUID = bytesToUUID(data, 1);
        int urlLength = (data[17] << 8) | data[18];
        if(data.length < 19 + urlLength) {
            throw new IOException("Invalid length " + data.length + " for skin request packet with " + urlLength + " length URL");
        }
        String urlStr = bytesToAscii(data, 19, urlLength);
        urlStr = SkinService.sanitizeTextureURL(urlStr);
        if(urlStr == null) {
            throw new IOException("Invalid URL for skin request packet");
        }
        URL url;
        try {
            url = new URL(urlStr);
        }catch(MalformedURLException t) {
            throw new IOException("Invalid URL for skin request packet", t);
        }
        String host = url.getHost();
        if(host.equalsIgnoreCase("textures.minecraft.net")) {
            UUID validUUID = createEaglerURLSkinUUID(urlStr);
            if(!searchUUID.equals(validUUID)) {
                throw new IOException("Invalid generated UUID from skin URL");
            }
            skinService.processGetOtherSkin(searchUUID, urlStr, sender);
        }else {
            throw new IOException("Invalid host in skin packet: " + host);
        }
    }

    public static void registerEaglerPlayer(final UUID clientUUID, final byte[] bs, final SkinService skinService) throws IOException {
        if (bs.length == 0) {
            throw new IOException("Zero-length packet recieved");
        }
        final int packetType = bs[0] & 0xFF;
        byte[] generatedPacket;
        switch (packetType) {
            case 1: {
                if (bs.length != 5) {
                    throw new IOException("Invalid length " + bs.length + " for preset skin packet");
                }
                generatedPacket = makePresetResponse(clientUUID, bs[1] << 24 | bs[2] << 16 | bs[3] << 8 | (bs[4] & 0xFF));
                break;
            }
            case 2: {
                final byte[] pixels = new byte[16384];
                if (bs.length != 2 + pixels.length) {
                    throw new IOException("Invalid length " + bs.length + " for custom skin packet");
                }
                setAlphaForChest(pixels, (byte) (-1));
                System.arraycopy(bs, 2, pixels, 0, pixels.length);
                generatedPacket = makeCustomResponse(clientUUID, bs[1] & 0xFF, pixels);
                break;
            }
            default: {
                throw new IOException("Unknown skin packet type: " + packetType);
            }
        }
        skinService.registerEaglercraftPlayer(clientUUID, generatedPacket);
    }

    public static byte[] asciiString(String string) {
        byte[] str = new byte[string.length()];
        for(int i = 0; i < str.length; ++i) {
            str[i] = (byte)string.charAt(i);
        }
        return str;
    }

    public static UUID createEaglerURLSkinUUID(String skinUrl) {
        return UUID.nameUUIDFromBytes(asciiString("EaglercraftSkinURL:" + skinUrl));
    }

    public static void registerEaglerPlayerFallback(final UUID clientUUID, final SkinService skinService) throws IOException {
        final int skinModel = ((clientUUID.hashCode() & 0x1) != 0x0) ? 1 : 0;
        final byte[] generatedPacket = makePresetResponse(clientUUID, skinModel);
        skinService.registerEaglercraftPlayer(clientUUID, generatedPacket);
    }

    public static void setAlphaForChest(final byte[] skin64x64, final byte alpha) {
        if (skin64x64.length != 16384) {
            throw new IllegalArgumentException("Skin is not 64x64!");
        }
        for (int y = 20; y < 32; ++y) {
            for (int x = 16; x < 40; ++x) {
                skin64x64[y << 8 | x << 2] = alpha;
            }
        }
    }

    public static byte[] makePresetResponse(final UUID uuid) {
        return makePresetResponse(uuid, ((uuid.hashCode() & 0x1) != 0x0) ? 1 : 0);
    }

    public static byte[] makePresetResponse(final UUID uuid, final int presetId) {
        final byte[] ret = new byte[21];
        ret[0] = 4;
        UUIDToBytes(uuid, ret, 1);
        ret[17] = (byte) (presetId >> 24);
        ret[18] = (byte) (presetId >> 16);
        ret[19] = (byte) (presetId >> 8);
        ret[20] = (byte) (presetId & 0xFF);
        return ret;
    }

    public static byte[] makeCustomResponse(final UUID uuid, final int model, final byte[] pixels) {
        final byte[] ret = new byte[18 + pixels.length];
        ret[0] = 5;
        UUIDToBytes(uuid, ret, 1);
        ret[17] = (byte) model;
        System.arraycopy(pixels, 0, ret, 18, pixels.length);
        return ret;
    }

    public static String bytesToAscii(byte[] bytes, int off, int len) {
        char[] ret = new char[len];
        for(int i = 0; i < len; ++i) {
            ret[i] = (char)((int)bytes[off + i] & 0xFF);
        }
        return new String(ret);
    }

    public static UUID bytesToUUID(final byte[] bytes, final int off) {
        final long msb = ((long) bytes[off] & 0xFFL) << 56 | ((long) bytes[off + 1] & 0xFFL) << 48 | ((long) bytes[off + 2] & 0xFFL) << 40 | ((long) bytes[off + 3] & 0xFFL) << 32 | ((long) bytes[off + 4] & 0xFFL) << 24 | ((long) bytes[off + 5] & 0xFFL) << 16 | ((long) bytes[off + 6] & 0xFFL) << 8 | ((long) bytes[off + 7] & 0xFFL);
        final long lsb = ((long) bytes[off + 8] & 0xFFL) << 56 | ((long) bytes[off + 9] & 0xFFL) << 48 | ((long) bytes[off + 10] & 0xFFL) << 40 | ((long) bytes[off + 11] & 0xFFL) << 32 | ((long) bytes[off + 12] & 0xFFL) << 24 | ((long) bytes[off + 13] & 0xFFL) << 16 | ((long) bytes[off + 14] & 0xFFL) << 8 | ((long) bytes[off + 15] & 0xFFL);
        return new UUID(msb, lsb);
    }

    public static void UUIDToBytes(final UUID uuid, final byte[] bytes, final int off) {
        final long msb = uuid.getMostSignificantBits();
        final long lsb = uuid.getLeastSignificantBits();
        bytes[off] = (byte) (msb >> 56);
        bytes[off + 1] = (byte) (msb >> 48);
        bytes[off + 2] = (byte) (msb >> 40);
        bytes[off + 3] = (byte) (msb >> 32);
        bytes[off + 4] = (byte) (msb >> 24);
        bytes[off + 5] = (byte) (msb >> 16);
        bytes[off + 6] = (byte) (msb >> 8);
        bytes[off + 7] = (byte) (msb & 0xFFL);
        bytes[off + 8] = (byte) (lsb >> 56);
        bytes[off + 9] = (byte) (lsb >> 48);
        bytes[off + 10] = (byte) (lsb >> 40);
        bytes[off + 11] = (byte) (lsb >> 32);
        bytes[off + 12] = (byte) (lsb >> 24);
        bytes[off + 13] = (byte) (lsb >> 16);
        bytes[off + 14] = (byte) (lsb >> 8);
        bytes[off + 15] = (byte) (lsb & 0xFFL);
    }
}
