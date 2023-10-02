package me.ayunami2000.ayunViaProxyEagUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.ssl.SslCompletionEvent;

import java.util.ArrayList;
import java.util.List;

public class WebSocketConnectedNotifier extends ChannelDuplexHandler {
    private final List<Object> msgsRead = new ArrayList<>();
    private final List<MsgPromise> msgsWrite = new ArrayList<>();
    @Override
    public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) {
        if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
            ctx.fireChannelActive();
            ctx.fireUserEventTriggered(evt);
            ctx.pipeline().remove(this);
            for (final Object msg : msgsRead)
                ctx.fireChannelRead(msg);
            msgsRead.clear();
            for (final MsgPromise msg : msgsWrite)
                ctx.write(msg.msg, msg.promise);
            ctx.flush();
            msgsWrite.clear();
        } else if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT) {
            ctx.fireUserEventTriggered(evt);
            ctx.close();
        } else {
            if (evt instanceof SslCompletionEvent && !((SslCompletionEvent) evt).isSuccess()) {
                ((SslCompletionEvent) evt).cause().printStackTrace();
            }
            ctx.fireUserEventTriggered(evt);
        }
    }
    @Override
    public void channelActive(final ChannelHandlerContext ctx) {
        //
    }
    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            msgsRead.add(msg);
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof BinaryWebSocketFrame || msg instanceof ByteBuf) {
            msgsWrite.add(new MsgPromise(msg, promise));
        } else {
            ctx.write(msg, promise);
        }
    }
}