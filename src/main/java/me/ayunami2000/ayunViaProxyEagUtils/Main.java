package me.ayunami2000.ayunViaProxyEagUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import net.lenni0451.lambdaevents.EventHandler;
import net.raphimc.vialegacy.protocols.release.protocol1_7_2_5to1_6_4.types.Types1_6_4;
import net.raphimc.viaproxy.plugins.PluginManager;
import net.raphimc.viaproxy.plugins.ViaProxyPlugin;
import net.raphimc.viaproxy.plugins.events.Client2ProxyChannelInitializeEvent;
import net.raphimc.viaproxy.plugins.events.types.ITyped;
import net.raphimc.viaproxy.proxy.util.ExceptionUtil;

public class Main extends ViaProxyPlugin {
    public void onEnable() {
        PluginManager.EVENT_MANAGER.register(this);
    }

    @EventHandler
    public void onEvent(final Client2ProxyChannelInitializeEvent event) {
        if (event.getType() == ITyped.Type.PRE) {
            event.getChannel().pipeline().addLast("eaglercraft-initial-handler", new EaglercraftInitialHandler());
        }
        if (event.getType() == ITyped.Type.POST) {
            event.getChannel().pipeline().addAfter("eaglercraft-initial-handler", "ayun-eag-detector", new EaglerConnectionHandler());
        }
    }

    static class EaglerConnectionHandler extends ChannelInboundHandlerAdapter {
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
            super.userEventTriggered(ctx, evt);
            if (evt instanceof EaglercraftInitialHandler.EaglercraftClientConnected) {
                ctx.pipeline().remove("ayun-eag-detector");
                ctx.pipeline().addBefore("eaglercraft-handler", "ayun-eag-utils-init", new EaglerUtilsInitHandler());
            }
        }
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ExceptionUtil.handleNettyException(ctx, cause, null);
        }
    }

    static class EaglerUtilsInitHandler extends ChannelInboundHandlerAdapter {
        public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
            if (msg instanceof BinaryWebSocketFrame) {
                final ByteBuf bb = ((BinaryWebSocketFrame) msg).content();
                try {
                    if (bb.readByte() == 2 && bb.readByte() == 69) {
                        final String username = Types1_6_4.STRING.read(bb);
                        ctx.pipeline().addBefore("eaglercraft-handler", "ayun-eag-voice", new EaglerVoiceHandler(username));
                        ctx.pipeline().addBefore("eaglercraft-handler", "ayun-eag-skin", new EaglerSkinHandler(username));
                    } else {
                        ctx.pipeline().addBefore("eaglercraft-handler", "ayun-eag-x-login", new EaglerXLoginHandler());
                        ctx.pipeline().addBefore("eaglercraft-handler", "ayun-eag-skin-x", new EaglerXSkinHandler());
                    }
                } catch (Exception ignored) {
                }
                bb.resetReaderIndex();
            }
            ctx.pipeline().remove("ayun-eag-utils-init");
            super.channelRead(ctx, msg);
        }
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ExceptionUtil.handleNettyException(ctx, cause, null);
        }
    }
}
