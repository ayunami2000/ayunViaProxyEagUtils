package me.ayunami2000.ayunViaProxyEagUtils;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import net.raphimc.viaproxy.proxy.util.ExceptionUtil;

public class WebSocketActiveNotifier extends ChannelInboundHandlerAdapter {
    public void channelActive(final ChannelHandlerContext ctx) {
    }

    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            ctx.fireChannelActive();
            ctx.pipeline().remove(this);
        }
        super.userEventTriggered(ctx, evt);
    }

    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ExceptionUtil.handleNettyException(ctx, cause, null);
    }
}
