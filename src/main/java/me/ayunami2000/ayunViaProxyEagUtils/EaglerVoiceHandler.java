package me.ayunami2000.ayunViaProxyEagUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.AttributeKey;
import net.raphimc.vialegacy.protocols.release.protocol1_7_2_5to1_6_4.types.Types1_6_4;
import net.raphimc.viaproxy.proxy.util.ExceptionUtil;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class EaglerVoiceHandler extends ChannelInboundHandlerAdapter {
    private static final ConcurrentHashMap<String, ChannelHandlerContext> voicePlayers;
    private static final ConcurrentHashMap<String, ExpiringSet<String>> voiceRequests;
    private static final CopyOnWriteArraySet<String[]> voicePairs;
    private final String user;
    private static final Collection<String> iceServers;
    private static final AttributeKey<Boolean> VOICE_ENABLED;

    private static void sendData(final ChannelHandlerContext ctx, final byte[] data) throws IOException {
        final ByteBuf bb = ctx.alloc().buffer();
        bb.writeByte(250);
        try {
            Types1_6_4.STRING.write(bb, "EAG|Voice");
        } catch (Exception e) {
            throw new IOException(e);
        }
        bb.writeShort(data.length);
        bb.writeBytes(data);
        ctx.writeAndFlush(new BinaryWebSocketFrame(bb));
    }

    public EaglerVoiceHandler(final String username) {
        this.user = username;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ExceptionUtil.handleNettyException(ctx, cause, null);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object obj) throws Exception {
        if (((EaglercraftHandler) ctx.pipeline().get("eaglercraft-handler")).state == EaglercraftHandler.State.LOGIN_COMPLETE && !ctx.channel().hasAttr(EaglerVoiceHandler.VOICE_ENABLED)) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final DataOutputStream dos = new DataOutputStream(baos);
            dos.write(0);
            dos.writeBoolean(true);
            dos.write(EaglerVoiceHandler.iceServers.size());
            for (final String str : EaglerVoiceHandler.iceServers) {
                dos.writeUTF(str);
            }
            sendData(ctx, baos.toByteArray());
            this.sendVoicePlayers(this.user);
            ctx.channel().attr(EaglerVoiceHandler.VOICE_ENABLED).set(true);
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
                    if (!tag.equals("EAG|Voice")) {
                        bb.resetReaderIndex();
                        super.channelRead(ctx, obj);
                        return;
                    }
                    if (!FunnyConfig.eaglerVoice) {
                        bb.release();
                        return;
                    }
                    final DataInputStream streamIn = new DataInputStream(new ByteArrayInputStream(msg));
                    final int sig = streamIn.read();
                    switch (sig) {
                        case 0: {
                            if (!EaglerVoiceHandler.voicePlayers.containsKey(this.user)) {
                                bb.release();
                                return;
                            }
                            final String targetUser = streamIn.readUTF();
                            if (this.user.equals(targetUser)) {
                                bb.release();
                                return;
                            }
                            if (this.checkVoicePair(this.user, targetUser)) {
                                bb.release();
                                return;
                            }
                            if (!EaglerVoiceHandler.voicePlayers.containsKey(targetUser)) {
                                bb.release();
                                return;
                            }
                            if (!EaglerVoiceHandler.voiceRequests.containsKey(this.user)) {
                                EaglerVoiceHandler.voiceRequests.put(this.user, new ExpiringSet<>(2000L));
                            }
                            if (!EaglerVoiceHandler.voiceRequests.get(this.user).contains(targetUser)) {
                                EaglerVoiceHandler.voiceRequests.get(this.user).add(targetUser);
                                if (EaglerVoiceHandler.voiceRequests.containsKey(targetUser) && EaglerVoiceHandler.voiceRequests.get(targetUser).contains(this.user)) {
                                    if (EaglerVoiceHandler.voiceRequests.containsKey(targetUser)) {
                                        EaglerVoiceHandler.voiceRequests.get(targetUser).remove(this.user);
                                        if (EaglerVoiceHandler.voiceRequests.get(targetUser).isEmpty()) {
                                            EaglerVoiceHandler.voiceRequests.remove(targetUser);
                                        }
                                    }
                                    if (EaglerVoiceHandler.voiceRequests.containsKey(this.user)) {
                                        EaglerVoiceHandler.voiceRequests.get(this.user).remove(targetUser);
                                        if (EaglerVoiceHandler.voiceRequests.get(this.user).isEmpty()) {
                                            EaglerVoiceHandler.voiceRequests.remove(this.user);
                                        }
                                    }
                                    EaglerVoiceHandler.voicePairs.add(new String[]{this.user, targetUser});
                                    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                                    DataOutputStream dos2 = new DataOutputStream(baos2);
                                    dos2.write(1);
                                    dos2.writeUTF(this.user);
                                    dos2.writeBoolean(false);
                                    sendData(EaglerVoiceHandler.voicePlayers.get(targetUser), baos2.toByteArray());
                                    baos2 = new ByteArrayOutputStream();
                                    dos2 = new DataOutputStream(baos2);
                                    dos2.write(1);
                                    dos2.writeUTF(targetUser);
                                    dos2.writeBoolean(true);
                                    sendData(ctx, baos2.toByteArray());
                                }
                                bb.release();
                                return;
                            }
                            bb.release();
                            return;
                        }
                        case 1: {
                            if (!EaglerVoiceHandler.voicePlayers.containsKey(this.user)) {
                                final ByteArrayOutputStream baos3 = new ByteArrayOutputStream();
                                final DataOutputStream dos3 = new DataOutputStream(baos3);
                                dos3.write(1);
                                dos3.writeUTF(this.user);
                                final byte[] out = baos3.toByteArray();
                                for (final ChannelHandlerContext conn : EaglerVoiceHandler.voicePlayers.values()) {
                                    sendData(conn, out);
                                }
                                EaglerVoiceHandler.voicePlayers.put(this.user, ctx);
                                for (final String username : EaglerVoiceHandler.voicePlayers.keySet()) {
                                    this.sendVoicePlayers(username);
                                }
                                bb.release();
                                return;
                            }
                            bb.release();
                            return;
                        }
                        case 2: {
                            if (!EaglerVoiceHandler.voicePlayers.containsKey(this.user)) {
                                bb.release();
                                return;
                            }
                            try {
                                final String targetUser = streamIn.readUTF();
                                if (!EaglerVoiceHandler.voicePlayers.containsKey(targetUser)) {
                                    bb.release();
                                    return;
                                }
                                if (EaglerVoiceHandler.voicePairs.removeIf(pair -> (pair[0].equals(this.user) && pair[1].equals(targetUser)) || (pair[0].equals(targetUser) && pair[1].equals(this.user)))) {
                                    ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
                                    DataOutputStream dos2 = new DataOutputStream(baos2);
                                    dos2.write(2);
                                    dos2.writeUTF(this.user);
                                    sendData(EaglerVoiceHandler.voicePlayers.get(targetUser), baos2.toByteArray());
                                    baos2 = new ByteArrayOutputStream();
                                    dos2 = new DataOutputStream(baos2);
                                    dos2.write(2);
                                    dos2.writeUTF(targetUser);
                                    sendData(ctx, baos2.toByteArray());
                                    break;
                                }
                            } catch (EOFException var7) {
                                this.removeUser(this.user);
                            }
                            bb.release();
                            return;
                        }
                        case 3:
                        case 4: {
                            if (EaglerVoiceHandler.voicePlayers.containsKey(this.user)) {
                                final String username = streamIn.readUTF();
                                if (this.checkVoicePair(this.user, username)) {
                                    final String data = streamIn.readUTF();
                                    final ByteArrayOutputStream baos4 = new ByteArrayOutputStream();
                                    final DataOutputStream dos4 = new DataOutputStream(baos4);
                                    dos4.write(sig);
                                    dos4.writeUTF(this.user);
                                    dos4.writeUTF(data);
                                    sendData(EaglerVoiceHandler.voicePlayers.get(username), baos4.toByteArray());
                                }
                                bb.release();
                                return;
                            }
                            bb.release();
                            return;
                        }
                        default: {
                            bb.release();
                            return;
                        }
                    }
                } catch (Throwable var8) {
                    this.removeUser(this.user);
                    bb.release();
                    return;
                }
                bb.release();
                return;
            }
            bb.resetReaderIndex();
        }
        super.channelRead(ctx, obj);
    }

    @Override
    public void channelInactive(final ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        this.removeUser(this.user);
    }

    public void sendVoicePlayers(final String name) {
        if (EaglerVoiceHandler.voicePlayers.containsKey(name)) {
            try {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final DataOutputStream dos = new DataOutputStream(baos);
                dos.write(5);
                final Set<String> mostlyGlobalPlayers = ConcurrentHashMap.newKeySet();
                for (final String username : EaglerVoiceHandler.voicePlayers.keySet()) {
                    if (!username.equals(name) && EaglerVoiceHandler.voicePairs.stream().noneMatch(pair -> (pair[0].equals(name) && pair[1].equals(username)) || (pair[0].equals(username) && pair[1].equals(name)))) {
                        mostlyGlobalPlayers.add(username);
                    }
                }
                if (!mostlyGlobalPlayers.isEmpty()) {
                    dos.writeInt(mostlyGlobalPlayers.size());
                    for (final String username : mostlyGlobalPlayers) {
                        dos.writeUTF(username);
                    }
                    sendData(EaglerVoiceHandler.voicePlayers.get(name), baos.toByteArray());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public void removeUser(final String name) {
        EaglerVoiceHandler.voicePlayers.remove(name);
        for (final String username : EaglerVoiceHandler.voicePlayers.keySet()) {
            if (!name.equals(username)) {
                this.sendVoicePlayers(username);
            }
        }
        for (final String[] voicePair : EaglerVoiceHandler.voicePairs) {
            String target = null;
            if (voicePair[0].equals(name)) {
                target = voicePair[1];
            } else if (voicePair[1].equals(name)) {
                target = voicePair[0];
            }
            if (target != null && EaglerVoiceHandler.voicePlayers.containsKey(target)) {
                try {
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    final DataOutputStream dos = new DataOutputStream(baos);
                    dos.write(2);
                    dos.writeUTF(name);
                    sendData(EaglerVoiceHandler.voicePlayers.get(target), baos.toByteArray());
                } catch (IOException ignored) {
                }
            }
        }
        EaglerVoiceHandler.voicePairs.removeIf(pair -> pair[0].equals(name) || pair[1].equals(name));
    }

    private boolean checkVoicePair(final String user1, final String user2) {
        return EaglerVoiceHandler.voicePairs.stream().anyMatch(pair -> (pair[0].equals(user1) && pair[1].equals(user2)) || (pair[0].equals(user2) && pair[1].equals(user1)));
    }

    static {
        voicePlayers = new ConcurrentHashMap<>();
        voiceRequests = new ConcurrentHashMap<>();
        voicePairs = new CopyOnWriteArraySet<>();
        (iceServers = new ArrayList<>()).add("stun:stun.l.google.com:19302");
        EaglerVoiceHandler.iceServers.add("stun:stun1.l.google.com:19302");
        EaglerVoiceHandler.iceServers.add("stun:stun2.l.google.com:19302");
        EaglerVoiceHandler.iceServers.add("stun:stun3.l.google.com:19302");
        EaglerVoiceHandler.iceServers.add("stun:stun4.l.google.com:19302");
        EaglerVoiceHandler.iceServers.add("stun:openrelay.metered.ca:80");
        final Map<String, Map<String, String>> turnServerList = new HashMap<>();
        HashMap<String, String> n = new HashMap<>();
        n.put("url", "turn:openrelay.metered.ca:80");
        n.put("username", "openrelayproject");
        n.put("password", "openrelayproject");
        turnServerList.put("openrelay1", n);
        n = new HashMap<>();
        n.put("url", "turn:openrelay.metered.ca:443");
        n.put("username", "openrelayproject");
        n.put("password", "openrelayproject");
        turnServerList.put("openrelay2", n);
        n = new HashMap<>();
        n.put("url", "turn:openrelay.metered.ca:443?transport=tcp");
        n.put("username", "openrelayproject");
        n.put("password", "openrelayproject");
        turnServerList.put("openrelay3", n);
        for (final Map.Entry<String, Map<String, String>> trn : turnServerList.entrySet()) {
            final Map<String, String> o = trn.getValue();
            EaglerVoiceHandler.iceServers.add(o.get("url") + ";" + o.get("username") + ";" + o.get("password"));
        }
        VOICE_ENABLED = AttributeKey.valueOf("ayun-voice-enabled");
    }
}
