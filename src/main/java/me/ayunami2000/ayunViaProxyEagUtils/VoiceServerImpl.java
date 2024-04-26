package me.ayunami2000.ayunViaProxyEagUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import net.raphimc.netminecraft.constants.MCPackets;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.vialegacy.protocols.release.protocol1_7_2_5to1_6_4.types.Types1_6_4;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Copyright (c) 2022-2024 lax1dude, ayunami2000. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
public class VoiceServerImpl {

	public static final Map<UUID, String> uuidToUsernameMap = new HashMap<>();

	private static void fuckFuckFuck(ByteBuf out, String str) {
		int strlen = str.length();
		int utflen = 0;
		int c, count = 0;

		/* use charAt instead of copying String to char array */
		for (int i = 0; i < strlen; i++) {
			c = str.charAt(i);
			if ((c >= 0x0001) && (c <= 0x007F)) {
				utflen++;
			} else if (c > 0x07FF) {
				utflen += 3;
			} else {
				utflen += 2;
			}
		}

		if (utflen > 65535)
			throw new RuntimeException(
					"encoded string too long: " + utflen + " bytes");

		byte[] bytearr = new byte[utflen+2];

		bytearr[count++] = (byte) ((utflen >>> 8) & 0xFF);
		bytearr[count++] = (byte) ((utflen >>> 0) & 0xFF);

		int i=0;
		for (i=0; i<strlen; i++) {
			c = str.charAt(i);
			if (!((c >= 0x0001) && (c <= 0x007F))) break;
			bytearr[count++] = (byte) c;
		}

		for (;i < strlen; i++){
			c = str.charAt(i);
			if ((c >= 0x0001) && (c <= 0x007F)) {
				bytearr[count++] = (byte) c;

			} else if (c > 0x07FF) {
				bytearr[count++] = (byte) (0xE0 | ((c >> 12) & 0x0F));
				bytearr[count++] = (byte) (0x80 | ((c >>  6) & 0x3F));
				bytearr[count++] = (byte) (0x80 | ((c >>  0) & 0x3F));
			} else {
				bytearr[count++] = (byte) (0xC0 | ((c >>  6) & 0x1F));
				bytearr[count++] = (byte) (0x80 | ((c >>  0) & 0x3F));
			}
		}
		out.writeBytes(bytearr, 0, utflen+2);
	}

	private int pluginMessageId = -1;
	private void sendData(final ChannelHandlerContext ctx, final byte[] data) {
		final ByteBuf bb = ctx.alloc().buffer();
		if (((EaglerVoiceHandler) ctx.pipeline().get("ayun-eag-voice")).old) {
			bb.writeByte(250);
			try {
				Types1_6_4.STRING.write(bb, "EAG|Voice");
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
			bb.writeShort(data.length);
			bb.writeBytes(data);
			bb.readerIndex(23);
			switch (bb.readByte()) {
				case 0:
					if (bb.readableBytes() == 1) break;
					bb.skipBytes(1);
					int vi = PacketTypes.readVarInt(bb);
					String[] fard = new String[vi];
					for (int i = 0; i < vi; i++) {
						int xd = PacketTypes.readVarInt(bb);
						byte[] arr = new byte[xd];
						bb.readBytes(arr);
						fard[i] = new String(arr, StandardCharsets.UTF_8);
					}
					bb.readerIndex(0);
					bb.writerIndex(25);
					bb.writeByte(vi);
					for (String s : fard) {
						fuckFuckFuck(bb, s);
					}
					break;
				case 1:
					UUID uuid = PacketTypes.readUuid(bb);
					boolean o = bb.readBoolean();
					bb.readerIndex(0);
					bb.writerIndex(24);
					fuckFuckFuck(bb, uuidToUsernameMap.get(uuid));
					bb.writeBoolean(o);
					break;
				case 2:
					if (bb.readableBytes() == 0) break;
					uuid = PacketTypes.readUuid(bb);
					bb.readerIndex(0);
					bb.writerIndex(24);
					fuckFuckFuck(bb, uuidToUsernameMap.get(uuid));
					break;
				case 3:
				case 4:
					uuid = PacketTypes.readUuid(bb);
					vi = PacketTypes.readVarInt(bb);
					byte[] arr = new byte[vi];
					bb.readBytes(arr);
					bb.readerIndex(0);
					bb.writerIndex(24);
					fuckFuckFuck(bb, uuidToUsernameMap.get(uuid));
					fuckFuckFuck(bb, new String(arr, StandardCharsets.UTF_8));
					break;
				case 5:
					vi = PacketTypes.readVarInt(bb);
					bb.skipBytes(vi * 16);
					fard = new String[vi];
					for (int i = 0; i < vi; i++) {
						int xd = PacketTypes.readVarInt(bb);
						arr = new byte[xd];
						bb.readBytes(arr);
						fard[i] = new String(arr, StandardCharsets.UTF_8);
					}
					bb.readerIndex(0);
					bb.writerIndex(24);
					bb.writeInt(vi);
					for (String s : fard) {
						fuckFuckFuck(bb, s);
					}
					break;
			}
			bb.readerIndex(0);
			bb.setShort(21, bb.writerIndex() - 23);
		} else {
			if (pluginMessageId <= 0) {
				pluginMessageId = MCPackets.S2C_PLUGIN_MESSAGE.getId((((EaglercraftHandler) ctx.pipeline().get("eaglercraft-handler")).version).getVersion());
			}
			PacketTypes.writeVarInt(bb, pluginMessageId);
			PacketTypes.writeString(bb, "EAG|Voice-1.8");
			bb.writeBytes(data);
		}
		ctx.writeAndFlush(new BinaryWebSocketFrame(bb));
	}

	private final byte[] iceServersPacket;

	private final Map<UUID, ChannelHandlerContext> voicePlayers = new HashMap<>();
	private final Map<UUID, ExpiringSet<UUID>> voiceRequests = new HashMap<>();
	private final Set<VoicePair> voicePairs = new HashSet<>();

	private static class VoicePair {

		private final UUID uuid1;
		private final UUID uuid2;

		@Override
		public int hashCode() {
			return uuid1.hashCode() ^ uuid2.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			VoicePair other = (VoicePair) obj;
			return (uuid1.equals(other.uuid1) && uuid2.equals(other.uuid2))
					|| (uuid1.equals(other.uuid2) && uuid2.equals(other.uuid1));
		}

		private VoicePair(UUID uuid1, UUID uuid2) {
			this.uuid1 = uuid1;
			this.uuid2 = uuid2;
		}

		private boolean anyEquals(UUID uuid) {
			return uuid1.equals(uuid) || uuid2.equals(uuid);
		}
	}

	VoiceServerImpl() {
		if (FunnyConfig.eaglerVoice) {
			Set<String> list = new HashSet<>();
			list.add("stun:stun.l.google.com:19302");
			list.add("stun:stun1.l.google.com:19302");
			list.add("stun:stun2.l.google.com:19302");
			list.add("stun:stun3.l.google.com:19302");
			list.add("stun:stun4.l.google.com:19302");
			list.add("turn:relay1.expressturn.com:3478;ef2KWF55ZPQWCLBMIG;MGn8T66L0GhgDodf");
			this.iceServersPacket = VoiceSignalPackets.makeVoiceSignalPacketAllowed(true, list.toArray(new String[0]));
		} else {
			this.iceServersPacket = VoiceSignalPackets.makeVoiceSignalPacketAllowed(false, null);
		}
	}

	public void handlePlayerLoggedIn(ChannelHandlerContext player) {
		EaglerVoiceHandler evh = (EaglerVoiceHandler) player.pipeline().get("ayun-eag-voice");
		uuidToUsernameMap.put(evh.uuid, evh.user);
		sendData(player, iceServersPacket);
	}

	public void handlePlayerLoggedOut(UUID playerUUID) {
		removeUser(playerUUID);
		uuidToUsernameMap.remove(playerUUID);
	}

	void handleVoiceSignalPacketTypeRequest(UUID player, ChannelHandlerContext sender) {
		UUID senderUUID = ((EaglerVoiceHandler) sender.pipeline().get("ayun-eag-voice")).uuid;
		synchronized (voicePlayers) {
			if (senderUUID.equals(player))
				return; // prevent duplicates
			if (!voicePlayers.containsKey(senderUUID))
				return;
			ChannelHandlerContext targetPlayerCon = voicePlayers.get(player);
			if (targetPlayerCon == null)
				return;
			VoicePair newPair = new VoicePair(player, senderUUID);
			if (voicePairs.contains(newPair))
				return; // already paired
			ExpiringSet<UUID> senderRequestSet = voiceRequests.get(senderUUID);
			if (senderRequestSet == null) {
				voiceRequests.put(senderUUID, senderRequestSet = new ExpiringSet<>(2000));
			}
			if (!senderRequestSet.add(player)) {
				return;
			}

			// check if other has requested earlier
			ExpiringSet<UUID> theSet;
			if ((theSet = voiceRequests.get(player)) != null && theSet.contains(senderUUID)) {
				theSet.remove(senderUUID);
				if (theSet.isEmpty())
					voiceRequests.remove(player);
				senderRequestSet.remove(player);
				if (senderRequestSet.isEmpty())
					voiceRequests.remove(senderUUID);
				// send each other add data
				voicePairs.add(newPair);
				sendData(targetPlayerCon,
						VoiceSignalPackets.makeVoiceSignalPacketConnect(senderUUID, false));
				sendData(sender, VoiceSignalPackets.makeVoiceSignalPacketConnect(player, true));
			}
		}
	}

	void handleVoiceSignalPacketTypeConnect(ChannelHandlerContext sender) {
		UUID senderUUID = ((EaglerVoiceHandler) sender.pipeline().get("ayun-eag-voice")).uuid;
		synchronized (voicePlayers) {
			if (voicePlayers.containsKey(senderUUID)) {
				return;
			}
			boolean hasNoOtherPlayers = voicePlayers.isEmpty();
			voicePlayers.put(senderUUID, sender);
			if (hasNoOtherPlayers) {
				return;
			}
			byte[] packetToBroadcast = VoiceSignalPackets.makeVoiceSignalPacketGlobal(voicePlayers.values());
			for (ChannelHandlerContext userCon : voicePlayers.values()) {
				sendData(userCon, packetToBroadcast);
			}
		}
	}

	void handleVoiceSignalPacketTypeICE(UUID player, String str, ChannelHandlerContext sender) {
		UUID senderUUID = ((EaglerVoiceHandler) sender.pipeline().get("ayun-eag-voice")).uuid;
		ChannelHandlerContext pass;
		VoicePair pair = new VoicePair(player, senderUUID);
		synchronized (voicePlayers) {
			pass = voicePairs.contains(pair) ? voicePlayers.get(player) : null;
		}
		if (pass != null) {
			sendData(pass, VoiceSignalPackets.makeVoiceSignalPacketICE(senderUUID, str));
		}
	}

	void handleVoiceSignalPacketTypeDesc(UUID player, String str, ChannelHandlerContext sender) {
		UUID senderUUID = ((EaglerVoiceHandler) sender.pipeline().get("ayun-eag-voice")).uuid;
		ChannelHandlerContext pass;
		VoicePair pair = new VoicePair(player, senderUUID);
		synchronized (voicePlayers) {
			pass = voicePairs.contains(pair) ? voicePlayers.get(player) : null;
		}
		if (pass != null) {
			sendData(pass,
					VoiceSignalPackets.makeVoiceSignalPacketDesc(senderUUID, str));
		}
	}

	void handleVoiceSignalPacketTypeDisconnect(UUID player, ChannelHandlerContext sender) {
		UUID senderUUID = ((EaglerVoiceHandler) sender.pipeline().get("ayun-eag-voice")).uuid;
		if (player != null) {
			synchronized (voicePlayers) {
				if (!voicePlayers.containsKey(player)) {
					return;
				}
				byte[] userDisconnectPacket = null;
				Iterator<VoicePair> pairsItr = voicePairs.iterator();
				while (pairsItr.hasNext()) {
					VoicePair voicePair = pairsItr.next();
					UUID target = null;
					if (voicePair.uuid1.equals(player)) {
						target = voicePair.uuid2;
					} else if (voicePair.uuid2.equals(player)) {
						target = voicePair.uuid1;
					}
					if (target != null) {
						pairsItr.remove();
						ChannelHandlerContext conn = voicePlayers.get(target);
						if (conn != null) {
							if (userDisconnectPacket == null) {
								userDisconnectPacket = VoiceSignalPackets.makeVoiceSignalPacketDisconnect(player);
							}
							sendData(conn, userDisconnectPacket);
						}
						sendData(sender,
								VoiceSignalPackets.makeVoiceSignalPacketDisconnect(target));
					}
				}
			}
		} else {
			removeUser(senderUUID);
		}
	}

	public void removeUser(UUID user) {
		synchronized (voicePlayers) {
			if (voicePlayers.remove(user) == null) {
				return;
			}
			voiceRequests.remove(user);
			if (voicePlayers.size() > 0) {
				byte[] voicePlayersPkt = VoiceSignalPackets.makeVoiceSignalPacketGlobal(voicePlayers.values());
				for (Map.Entry<UUID, ChannelHandlerContext> userCon : voicePlayers.entrySet()) {
					if (!user.equals(userCon.getKey())) {
						sendData(userCon.getValue(), voicePlayersPkt);
					}
				}
			}
			byte[] userDisconnectPacket = null;
			Iterator<VoicePair> pairsItr = voicePairs.iterator();
			while (pairsItr.hasNext()) {
				VoicePair voicePair = pairsItr.next();
				UUID target = null;
				if (voicePair.uuid1.equals(user)) {
					target = voicePair.uuid2;
				} else if (voicePair.uuid2.equals(user)) {
					target = voicePair.uuid1;
				}
				if (target != null) {
					pairsItr.remove();
					if (voicePlayers.size() > 0) {
						ChannelHandlerContext conn = voicePlayers.get(target);
						if (conn != null) {
							if (userDisconnectPacket == null) {
								userDisconnectPacket = VoiceSignalPackets.makeVoiceSignalPacketDisconnect(user);
							}
							sendData(conn, userDisconnectPacket);
						}
					}
				}
			}
		}
	}

}
