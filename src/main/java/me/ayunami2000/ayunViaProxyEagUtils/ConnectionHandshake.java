package me.ayunami2000.ayunViaProxyEagUtils;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.viaversion.viaversion.util.ChatColorUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.AttributeKey;
import net.lenni0451.mcstructs.text.serializer.TextComponentSerializer;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import net.raphimc.viaproxy.util.logging.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Copyright (c) 2022-2023 LAX1DUDE. All Rights Reserved.
 *
 * WITH THE EXCEPTION OF PATCH FILES, MINIFIED JAVASCRIPT, AND ALL FILES
 * NORMALLY FOUND IN AN UNMODIFIED MINECRAFT RESOURCE PACK, YOU ARE NOT ALLOWED
 * TO SHARE, DISTRIBUTE, OR REPURPOSE ANY FILE USED BY OR PRODUCED BY THE
 * SOFTWARE IN THIS REPOSITORY WITHOUT PRIOR PERMISSION FROM THE PROJECT AUTHOR.
 *
 * NOT FOR COMMERCIAL OR MALICIOUS USE
 *
 * (please read the 'LICENSE' file this repo's root directory for more info)
 *
 */
public class ConnectionHandshake {
    private static final AttributeKey<Integer> serverVersKey = AttributeKey.newInstance("eag-server-vers");
    private static final int protocolV2 = 2;
    private static final int protocolV3 = 3;

    public static void attemptHandshake(List<Object> out, Channel ch, ProxyConnection proxyConnection, String password) {
        try {
            ByteArrayOutputStream bao = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(bao);

            d.writeByte(HandshakePacketTypes.PROTOCOL_CLIENT_VERSION);

            d.writeByte(2); // legacy protocol version

            d.writeShort(2); // supported eagler protocols count
            d.writeShort(protocolV2); // client supports v2
            d.writeShort(protocolV3); // client supports v3

            d.writeShort(1); // supported game protocols count
            d.writeShort(proxyConnection.getServerVersion().getVersion()); // client supports this protocol

            String clientBrand = "ViaProxy";
            d.writeByte(clientBrand.length());
            d.writeBytes(clientBrand);

            String clientVers = ViaProxy.VERSION;
            d.writeByte(clientVers.length());
            d.writeBytes(clientVers);

            d.writeBoolean(password != null);

            String username = proxyConnection.getGameProfile().getName();
            d.writeByte(username.length());
            d.writeBytes(username);

            out.add(new BinaryWebSocketFrame(ch.alloc().buffer(bao.size()).writeBytes(bao.toByteArray())));
        } catch (Throwable t) {
            Logger.LOGGER.error("Exception in handshake");
            Logger.LOGGER.error(t);
        }
    }

    public static void attemptHandshake2(Channel ch, byte[] read, ProxyConnection proxyConnection, String password) {
        try {
            ByteArrayOutputStream bao = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(bao);

            String username = proxyConnection.getGameProfile().getName();

            DataInputStream di = new DataInputStream(new ByteArrayInputStream(read));

            int type = di.read();
            if (type == HandshakePacketTypes.PROTOCOL_VERSION_MISMATCH) {

                StringBuilder protocols = new StringBuilder();
                int c = di.readShort();
                for (int i = 0; i < c; ++i) {
                    if (i > 0) {
                        protocols.append(", ");
                    }
                    protocols.append("v").append(di.readShort());
                }

                StringBuilder games = new StringBuilder();
                c = di.readShort();
                for (int i = 0; i < c; ++i) {
                    if (i > 0) {
                        games.append(", ");
                    }
                    games.append("mc").append(di.readShort());
                }

                Logger.LOGGER.info("Incompatible client: v2 & mc" + proxyConnection.getServerVersion().getVersion());
                Logger.LOGGER.info("Server supports: {}", protocols);
                Logger.LOGGER.info("Server supports: {}", games);

                int msgLen = di.read();
                byte[] dat = new byte[msgLen];
                di.read(dat);
                String msg = new String(dat, StandardCharsets.UTF_8);

                proxyConnection.kickClient(msg);
            } else if (type == HandshakePacketTypes.PROTOCOL_SERVER_VERSION) {
                int serverVers = di.readShort();

                if (serverVers != protocolV2 && serverVers != protocolV3) {
                    Logger.LOGGER.info("Incompatible server version: {}", serverVers);
                    proxyConnection.kickClient(serverVers < protocolV2 ? "Outdated Server" : "Outdated Client");
                    return;
                }

                ch.attr(serverVersKey).set(serverVers);

                int gameVers = di.readShort();
                if (gameVers != proxyConnection.getServerVersion().getVersion()) {
                    Logger.LOGGER.info("Incompatible minecraft protocol version: {}", gameVers);
                    proxyConnection.kickClient("This server does not support " + proxyConnection.getServerVersion().getName() + "!");
                    return;
                }

                Logger.LOGGER.info("Server protocol: {}", serverVers);

                int msgLen = di.read();
                byte[] dat = new byte[msgLen];
                di.read(dat);
                String brandStr = asciiString(dat);

                msgLen = di.read();
                dat = new byte[msgLen];
                di.read(dat);
                String versionStr = asciiString(dat);

                Logger.LOGGER.info("Server version: {}", versionStr);
                Logger.LOGGER.info("Server brand: {}", brandStr);

                int authType = di.read();
                int saltLength = (int) di.readShort() & 0xFFFF;

                byte[] salt = new byte[saltLength];
                di.read(salt);

                bao.reset();
                d.writeByte(HandshakePacketTypes.PROTOCOL_CLIENT_REQUEST_LOGIN);

                d.writeByte(username.length());
                d.writeBytes(username);

                String requestedServer = "default";
                d.writeByte(requestedServer.length());
                d.writeBytes(requestedServer);

                if (authType != 0 && password != null && !password.isEmpty()) {
                    if (authType == HandshakePacketTypes.AUTH_METHOD_PLAINTEXT) {
                        Logger.LOGGER.warn("Server is using insecure plaintext authentication");
                        d.writeByte(password.length() << 1);
                        d.writeChars(password);
                    } else if (authType == HandshakePacketTypes.AUTH_METHOD_EAGLER_SHA256) {
                        SHA256Digest digest = new SHA256Digest();

                        int passLen = password.length();

                        digest.update((byte) ((passLen >> 8) & 0xFF));
                        digest.update((byte) (passLen & 0xFF));

                        for (int i = 0; i < passLen; ++i) {
                            char codePoint = password.charAt(i);
                            digest.update((byte) ((codePoint >> 8) & 0xFF));
                            digest.update((byte) (codePoint & 0xFF));
                        }

                        digest.update(HandshakePacketTypes.EAGLER_SHA256_SALT_SAVE, 0, 32);

                        byte[] hashed = new byte[32];
                        digest.doFinal(hashed, 0);

                        digest.reset();

                        digest.update(hashed, 0, 32);
                        digest.update(salt, 0, 32);
                        digest.update(HandshakePacketTypes.EAGLER_SHA256_SALT_BASE, 0, 32);

                        digest.doFinal(hashed, 0);

                        digest.reset();

                        digest.update(hashed, 0, 32);
                        digest.update(salt, 32, 32);
                        digest.update(HandshakePacketTypes.EAGLER_SHA256_SALT_BASE, 0, 32);

                        digest.doFinal(hashed, 0);

                        d.writeByte(32);
                        d.write(hashed);
                    } else if (authType == HandshakePacketTypes.AUTH_METHOD_AUTHME_SHA256) {
                        SHA256Digest digest = new SHA256Digest();

                        byte[] passwd = password.getBytes(StandardCharsets.UTF_8);
                        digest.update(passwd, 0, passwd.length);

                        byte[] hashed = new byte[32];
                        digest.doFinal(hashed, 0);

                        byte[] toHexAndSalt = new byte[64];
                        for (int i = 0; i < 32; ++i) {
                            toHexAndSalt[i << 1] = HEX[(hashed[i] >> 4) & 0xF];
                            toHexAndSalt[(i << 1) + 1] = HEX[hashed[i] & 0xF];
                        }

                        digest.reset();
                        digest.update(toHexAndSalt, 0, 64);
                        digest.update(salt, 0, salt.length);

                        digest.doFinal(hashed, 0);

                        for (int i = 0; i < 32; ++i) {
                            toHexAndSalt[i << 1] = HEX[(hashed[i] >> 4) & 0xF];
                            toHexAndSalt[(i << 1) + 1] = HEX[hashed[i] & 0xF];
                        }

                        d.writeByte(64);
                        d.write(toHexAndSalt);
                    } else {
                        Logger.LOGGER.error("Unsupported authentication type: {}", authType);
                        proxyConnection.kickClient(ChatColorUtil.COLOR_CHAR + "cUnsupported authentication type: " + authType + "\n\n" + ChatColorUtil.COLOR_CHAR + "7(Use a newer version of the client)");
                        return;
                    }
                } else {
                    d.writeByte(0);
                }

                ch.writeAndFlush(new BinaryWebSocketFrame(ch.alloc().buffer(bao.size()).writeBytes(bao.toByteArray())));
            } else if (type == HandshakePacketTypes.PROTOCOL_SERVER_ERROR) {
                showError(proxyConnection, di, true);
            }
        } catch (Throwable t) {
            Logger.LOGGER.error("Exception in handshake");
            Logger.LOGGER.error(t);
        }
    }

    public static void attemptHandshake3(Channel ch, byte[] read, ProxyConnection proxyConnection) {
        try {
            int serverVers = ch.attr(serverVersKey).get();

            ByteArrayOutputStream bao = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(bao);

            int msgLen;
            byte[] dat;

            DataInputStream di = new DataInputStream(new ByteArrayInputStream(read));
            int type = di.read();
            if (type == HandshakePacketTypes.PROTOCOL_SERVER_ALLOW_LOGIN) {
                msgLen = di.read();
                dat = new byte[msgLen];
                di.read(dat);

                String serverUsername = asciiString(dat);

                JsonObject json = new JsonObject();
                json.addProperty("name", serverUsername);
                UUID uuid = new UUID(di.readLong(), di.readLong());
                json.addProperty("uuid", uuid.toString());
                proxyConnection.setGameProfile(new GameProfile(uuid, serverUsername));

                if (proxyConnection.getC2P().hasAttr(EaglercraftHandler.profileDataKey)) {
                    EaglercraftHandler.ProfileData profileData = proxyConnection.getC2P().attr(EaglercraftHandler.profileDataKey).get();
                    bao.reset();
                    d.writeByte(HandshakePacketTypes.PROTOCOL_CLIENT_PROFILE_DATA);
                    d.writeByte(profileData.type.length());
                    d.writeBytes(profileData.type);
                    d.writeShort(profileData.data.length);
                    d.write(profileData.data);
                    ch.writeAndFlush(new BinaryWebSocketFrame(ch.alloc().buffer(bao.size()).writeBytes(bao.toByteArray())));
                }

                bao.reset();
                d.writeByte(HandshakePacketTypes.PROTOCOL_CLIENT_FINISH_LOGIN);
                ch.writeAndFlush(new BinaryWebSocketFrame(ch.alloc().buffer(bao.size()).writeBytes(bao.toByteArray())));
            } else if (type == HandshakePacketTypes.PROTOCOL_SERVER_DENY_LOGIN) {
                if (serverVers == protocolV2) {
                    msgLen = di.read();
                } else {
                    msgLen = di.readUnsignedShort();
                }
                dat = new byte[msgLen];
                di.read(dat);
                String errStr = new String(dat, StandardCharsets.UTF_8);
                proxyConnection.kickClient(TextComponentSerializer.V1_8.deserialize(errStr).asLegacyFormatString());
            } else if (type == HandshakePacketTypes.PROTOCOL_SERVER_ERROR) {
                showError(proxyConnection, di, serverVers == protocolV2);
            }
        } catch (Throwable t) {
            Logger.LOGGER.error("Exception in handshake");
            Logger.LOGGER.error(t);
        }
    }

    public static void attemptHandshake4(Channel ch, byte[] read, ProxyConnection proxyConnection) {
        try {
            int serverVers = ch.attr(serverVersKey).get();

            DataInputStream di = new DataInputStream(new ByteArrayInputStream(read));
            int type = di.read();
            if (type == HandshakePacketTypes.PROTOCOL_SERVER_ERROR) {
                showError(proxyConnection, di, serverVers == protocolV2);
            }
        } catch (Throwable t) {
            Logger.LOGGER.error("Exception in handshake");
            Logger.LOGGER.error(t);
        }
    }

    private static void showError(ProxyConnection proxyConnection, DataInputStream err, boolean v2) throws IOException {
        int errorCode = err.read();
        int msgLen = v2 ? err.read() : err.readUnsignedShort();
        byte[] dat = new byte[msgLen];
        err.read(dat);
        String errStr = new String(dat, StandardCharsets.UTF_8);
        Logger.LOGGER.info("Server Error Code {}: {}", errorCode, errStr);
        if(errorCode == HandshakePacketTypes.SERVER_ERROR_RATELIMIT_BLOCKED) {
            proxyConnection.kickClient("Server Error Ratelimited (blocked)");
        }else if(errorCode == HandshakePacketTypes.SERVER_ERROR_RATELIMIT_LOCKED) {
            proxyConnection.kickClient("Server Error Ratelimited (locked)");
        }else if(errorCode == HandshakePacketTypes.SERVER_ERROR_CUSTOM_MESSAGE) {
            proxyConnection.kickClient("Server Error Message " + TextComponentSerializer.V1_8.deserialize(errStr).asLegacyFormatString());
        }else if(errorCode == HandshakePacketTypes.SERVER_ERROR_AUTHENTICATION_REQUIRED) {
            proxyConnection.kickClient("Server Error Authentication required " + TextComponentSerializer.V1_8.deserialize(errStr).asLegacyFormatString());
        }else {
            proxyConnection.kickClient("Server Error Code " + errorCode + "\n" + TextComponentSerializer.V1_8.deserialize(errStr).asLegacyFormatString());
        }
    }

    private static final byte[] HEX = new byte[]{
            (byte) '0', (byte) '1', (byte) '2', (byte) '3', (byte) '4', (byte) '5', (byte) '6', (byte) '7',
            (byte) '8', (byte) '9', (byte) 'a', (byte) 'b', (byte) 'c', (byte) 'd', (byte) 'e', (byte) 'f'
    };

    public static String asciiString(byte[] bytes) {
        char[] str = new char[bytes.length];
        for(int i = 0; i < bytes.length; ++i) {
            str[i] = (char)((int) bytes[i] & 0xFF);
        }
        return new String(str);
    }
}
