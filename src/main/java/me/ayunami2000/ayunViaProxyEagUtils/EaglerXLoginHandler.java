package me.ayunami2000.ayunViaProxyEagUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import net.raphimc.netminecraft.packet.PacketTypes;
import net.raphimc.viaproxy.proxy.util.ExceptionUtil;

public class EaglerXLoginHandler extends ChannelOutboundHandlerAdapter {
    private int counter;

    public EaglerXLoginHandler() {
        this.counter = 0;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ExceptionUtil.handleNettyException(ctx, cause, null, true);
    }

    @Override
    public void write(final ChannelHandlerContext ctx, final Object msg, final ChannelPromise promise) throws Exception {
        if (msg instanceof BinaryWebSocketFrame) {
            final ByteBuf bb = ((BinaryWebSocketFrame) msg).content();
            if (PacketTypes.readVarInt(bb) == 2 && ++this.counter == 2) {
                ctx.pipeline().remove("ayun-eag-x-login");
                bb.writerIndex(bb.readerIndex());
                PacketTypes.writeString(bb, "");
                bb.writeByte(2);
            }
            bb.resetReaderIndex();
        }
        super.write(ctx, msg, promise);
    }
}
