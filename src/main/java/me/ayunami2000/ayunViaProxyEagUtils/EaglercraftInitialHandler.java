package me.ayunami2000.ayunViaProxyEagUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.raphimc.viaproxy.proxy.util.ExceptionUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class EaglercraftInitialHandler extends ByteToMessageDecoder {
    private static SslContext sslContext;

    @Override
    protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) {
        if (!ctx.channel().isOpen()) {
            return;
        }
        if (!in.isReadable()) {
            return;
        }
        if (in.readableBytes() >= 3 || in.getByte(0) != 71) {
            if (in.readableBytes() >= 3 && in.getCharSequence(0, 3, StandardCharsets.UTF_8).equals("GET")) {
                if (EaglercraftInitialHandler.sslContext != null) {
                    ctx.pipeline().addBefore("eaglercraft-initial-handler", "ws-ssl-handler", EaglercraftInitialHandler.sslContext.newHandler(ctx.alloc()));
                }
                ctx.pipeline().addBefore("eaglercraft-initial-handler", "ws-http-codec", new HttpServerCodec());
                ctx.pipeline().addBefore("eaglercraft-initial-handler", "ws-http-aggregator", new HttpObjectAggregator(65535, true));
                ctx.pipeline().addBefore("eaglercraft-initial-handler", "ws-compression", new WebSocketServerCompressionHandler());
                ctx.pipeline().addBefore("eaglercraft-initial-handler", "ws-handler", new WebSocketServerProtocolHandler("/", null, true));
                ctx.pipeline().addBefore("eaglercraft-initial-handler", "ws-active-notifier", new WebSocketActiveNotifier());
                ctx.pipeline().addBefore("eaglercraft-initial-handler", "eaglercraft-handler", new EaglercraftHandler());
                ctx.fireUserEventTriggered(EaglercraftClientConnected.INSTANCE);
                ctx.pipeline().fireChannelRead(in.readBytes(in.readableBytes()));
            } else {
                out.add(in.readBytes(in.readableBytes()));
            }
            ctx.pipeline().remove(this);
        }
    }

    static {
        final File certFolder = new File("certs");
        if (certFolder.exists()) {
            try {
                EaglercraftInitialHandler.sslContext = SslContextBuilder.forServer(new File(certFolder, "fullchain.pem"), new File(certFolder, "privkey.pem")).build();
            } catch (Throwable e) {
                throw new RuntimeException("Failed to load SSL context", e);
            }
        }
    }

    public static final class EaglercraftClientConnected {
        public static final EaglercraftClientConnected INSTANCE;

        static {
            INSTANCE = new EaglercraftClientConnected();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ExceptionUtil.handleNettyException(ctx, cause, null);
    }
}
