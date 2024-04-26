package me.ayunami2000.ayunViaProxyEagUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.raphimc.netminecraft.packet.PacketTypes;

/**
 * Copyright (c) 2024 lax1dude. All Rights Reserved.
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
public class VoiceSignalPackets {

	static final int VOICE_SIGNAL_ALLOWED = 0;
	static final int VOICE_SIGNAL_REQUEST = 0;
	static final int VOICE_SIGNAL_CONNECT = 1;
	static final int VOICE_SIGNAL_DISCONNECT = 2;
	static final int VOICE_SIGNAL_ICE = 3;
	static final int VOICE_SIGNAL_DESC = 4;
	static final int VOICE_SIGNAL_GLOBAL = 5;

	public static void processPacket(byte[] data, ChannelHandlerContext sender) throws IOException {
		int packetId = -1;
		if(data.length == 0) {
			throw new IOException("Zero-length packet recieved");
		}
		try {
			ByteBuf buffer = Unpooled.wrappedBuffer(data).writerIndex(data.length);
			packetId = buffer.readUnsignedByte();
			switch(packetId) {
				case VOICE_SIGNAL_REQUEST: {
					EaglerVoiceHandler.voiceService.handleVoiceSignalPacketTypeRequest(PacketTypes.readUuid(buffer), sender);
					break;
				}
				case VOICE_SIGNAL_CONNECT: {
					EaglerVoiceHandler.voiceService.handleVoiceSignalPacketTypeConnect(sender);
					break;
				}
				case VOICE_SIGNAL_ICE: {
					EaglerVoiceHandler.voiceService.handleVoiceSignalPacketTypeICE(PacketTypes.readUuid(buffer), PacketTypes.readString(buffer, 32767), sender);
					break;
				}
				case VOICE_SIGNAL_DESC: {
					EaglerVoiceHandler.voiceService.handleVoiceSignalPacketTypeDesc(PacketTypes.readUuid(buffer), PacketTypes.readString(buffer, 32767), sender);
					break;
				}
				case VOICE_SIGNAL_DISCONNECT: {
					EaglerVoiceHandler.voiceService.handleVoiceSignalPacketTypeDisconnect(buffer.readableBytes() > 0 ? PacketTypes.readUuid(buffer) : null, sender);
					break;
				}
				default: {
					throw new IOException("Unknown packet type " + packetId);
				}
			}
			if(buffer.readableBytes() > 0) {
				throw new IOException("Voice packet is too long!");
			}
		}catch(IOException ex) {
			throw ex;
		}catch(Throwable t) {
			throw new IOException("Unhandled exception handling voice packet type " + packetId, t);
		}
	}

	static byte[] makeVoiceSignalPacketAllowed(boolean allowed, String[] iceServers) {
		if (iceServers == null) {
			byte[] ret = new byte[2];
			ByteBuf wrappedBuffer = Unpooled.wrappedBuffer(ret).writerIndex(0);
			wrappedBuffer.writeByte(VOICE_SIGNAL_ALLOWED);
			wrappedBuffer.writeBoolean(allowed);
			return ret;
		}
		byte[][] iceServersBytes = new byte[iceServers.length][];
		int totalLen = 2 + getVarIntSize(iceServers.length);
		for(int i = 0; i < iceServers.length; ++i) {
			byte[] b = iceServersBytes[i] = iceServers[i].getBytes(StandardCharsets.UTF_8);
			totalLen += getVarIntSize(b.length) + b.length;
		}
		byte[] ret = new byte[totalLen];
		ByteBuf wrappedBuffer = Unpooled.wrappedBuffer(ret).writerIndex(0);
		wrappedBuffer.writeByte(VOICE_SIGNAL_ALLOWED);
		wrappedBuffer.writeBoolean(allowed);
		PacketTypes.writeVarInt(wrappedBuffer, iceServersBytes.length);
		for(int i = 0; i < iceServersBytes.length; ++i) {
			byte[] b = iceServersBytes[i];
			PacketTypes.writeVarInt(wrappedBuffer, b.length);
			wrappedBuffer.writeBytes(b);
		}
		return ret;
	}

	static byte[] makeVoiceSignalPacketGlobal(Collection<ChannelHandlerContext> users) {
		int cnt = users.size();
		byte[][] displayNames = new byte[cnt][];
		int i = 0;
		for(ChannelHandlerContext user : users) {
			String name = ((EaglerVoiceHandler) user.pipeline().get("ayun-eag-voice")).user;
			if(name.length() > 16) name = name.substring(0, 16);
			displayNames[i++] = name.getBytes(StandardCharsets.UTF_8);
		}
		int totalLength = 1 + getVarIntSize(cnt) + (cnt << 4);
		for(i = 0; i < cnt; ++i) {
			totalLength += getVarIntSize(displayNames[i].length) + displayNames[i].length;
		}
		byte[] ret = new byte[totalLength];
		ByteBuf wrappedBuffer = Unpooled.wrappedBuffer(ret).writerIndex(0);
		wrappedBuffer.writeByte(VOICE_SIGNAL_GLOBAL);
		PacketTypes.writeVarInt(wrappedBuffer, cnt);
		for(ChannelHandlerContext user : users) {
			PacketTypes.writeUuid(wrappedBuffer, ((EaglerVoiceHandler) user.pipeline().get("ayun-eag-voice")).uuid);
		}
		for(i = 0; i < cnt; ++i) {
			PacketTypes.writeVarInt(wrappedBuffer, displayNames[i].length);
			wrappedBuffer.writeBytes(displayNames[i]);
		}
		return ret;
	}

	static byte[] makeVoiceSignalPacketConnect(UUID player, boolean offer) {
		byte[] ret = new byte[18];
		ByteBuf wrappedBuffer = Unpooled.wrappedBuffer(ret).writerIndex(0);
		wrappedBuffer.writeByte(VOICE_SIGNAL_CONNECT);
		PacketTypes.writeUuid(wrappedBuffer, player);
		wrappedBuffer.writeBoolean(offer);
		return ret;
	}

	static byte[] makeVoiceSignalPacketDisconnect(UUID player) {
		if(player == null) {
			return new byte[] { (byte)VOICE_SIGNAL_DISCONNECT };
		}
		byte[] ret = new byte[17];
		ByteBuf wrappedBuffer = Unpooled.wrappedBuffer(ret).writerIndex(0);
		wrappedBuffer.writeByte(VOICE_SIGNAL_DISCONNECT);
		PacketTypes.writeUuid(wrappedBuffer, player);
		return ret;
	}

	static byte[] makeVoiceSignalPacketICE(UUID player, String str) {
		byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
		byte[] ret = new byte[17 + getVarIntSize(strBytes.length) + strBytes.length];
		ByteBuf wrappedBuffer = Unpooled.wrappedBuffer(ret).writerIndex(0);
		wrappedBuffer.writeByte(VOICE_SIGNAL_ICE);
		PacketTypes.writeUuid(wrappedBuffer, player);
		PacketTypes.writeVarInt(wrappedBuffer, strBytes.length);
		wrappedBuffer.writeBytes(strBytes);
		return ret;
	}

	static byte[] makeVoiceSignalPacketDesc(UUID player, String str) {
		byte[] strBytes = str.getBytes(StandardCharsets.UTF_8);
		byte[] ret = new byte[17 + getVarIntSize(strBytes.length) + strBytes.length];
		ByteBuf wrappedBuffer = Unpooled.wrappedBuffer(ret).writerIndex(0);
		wrappedBuffer.writeByte(VOICE_SIGNAL_DESC);
		PacketTypes.writeUuid(wrappedBuffer, player);
		PacketTypes.writeVarInt(wrappedBuffer, strBytes.length);
		wrappedBuffer.writeBytes(strBytes);
		return ret;
	}

	public static int getVarIntSize(int input) {
		for (int i = 1; i < 5; ++i) {
			if ((input & -1 << i * 7) == 0) {
				return i;
			}
		}

		return 5;
	}
}
