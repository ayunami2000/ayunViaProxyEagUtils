package me.ayunami2000.ayunViaProxyEagUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.raphimc.viaproxy.plugins.PluginManager;
import net.raphimc.viaproxy.plugins.events.Client2ProxyHandlerCreationEvent;
import net.raphimc.viaproxy.proxy.client2proxy.Client2ProxyChannelInitializer;
import net.raphimc.viaproxy.proxy.client2proxy.passthrough.LegacyPassthroughInitialHandler;
import net.raphimc.viaproxy.proxy.client2proxy.passthrough.PassthroughClient2ProxyChannelInitializer;
import net.raphimc.viaproxy.proxy.client2proxy.passthrough.PassthroughClient2ProxyHandler;
import net.raphimc.viaproxy.proxy.util.ExceptionUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class EaglercraftInitialHandler extends ByteToMessageDecoder {
    private static final Method initChannelMethod;
    public static SslContext sslContext;

    @Override
    protected void decode(final ChannelHandlerContext ctx, final ByteBuf in, final List<Object> out) {
        if (!ctx.channel().isOpen()) {
            return;
        }
        if (!in.isReadable()) {
            return;
        }
        if (in.readableBytes() >= 3 || in.getByte(0) != 71) {
            if ((in.readableBytes() >= 3 && in.getCharSequence(0, 3, StandardCharsets.UTF_8).equals("GET")) || (in.readableBytes() >= 4 && in.getCharSequence(0, 4, StandardCharsets.UTF_8).equals("POST"))) {
                if (EaglercraftInitialHandler.sslContext != null) {
                    ctx.pipeline().addBefore("eaglercraft-initial-handler", "ws-ssl-handler", EaglercraftInitialHandler.sslContext.newHandler(ctx.alloc()));
                }
                ctx.pipeline().addBefore("eaglercraft-initial-handler", "ws-http-codec", new HttpServerCodec());
                ctx.pipeline().addBefore("eaglercraft-initial-handler", "ws-http-aggregator", new HttpObjectAggregator(65535, true));
                ctx.pipeline().addBefore("eaglercraft-initial-handler", "ws-compression", new WebSocketServerCompressionHandler());
                ctx.pipeline().addBefore("eaglercraft-initial-handler", "ws-handler", new WebSocketServerProtocolHandler("/", null, true));
                ctx.pipeline().addBefore("eaglercraft-initial-handler", "ws-active-notifier", new WebSocketActiveNotifier());
                ctx.pipeline().addBefore("eaglercraft-initial-handler", "eaglercraft-handler", new EaglercraftHandler());
                ctx.pipeline().replace(Client2ProxyChannelInitializer.LEGACY_PASSTHROUGH_INITIAL_HANDLER_NAME, Client2ProxyChannelInitializer.LEGACY_PASSTHROUGH_INITIAL_HANDLER_NAME, new LegacyPassthroughInitialHandler() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                        if (ctx.channel().isOpen()) {
                            if (msg.isReadable()) {
                                int lengthOrPacketId = msg.getUnsignedByte(0);
                                if (lengthOrPacketId == 0 || lengthOrPacketId == 1 || lengthOrPacketId == 2 || lengthOrPacketId == 254) {
                                    boolean fard = false;
                                    for (Map.Entry<String, ChannelHandler> entry : ctx.pipeline()) {
                                        if (entry.getKey().equals(Client2ProxyChannelInitializer.LEGACY_PASSTHROUGH_INITIAL_HANDLER_NAME)) {
                                            fard = true;
                                        } else {
                                            if (fard) {
                                                ctx.pipeline().remove(entry.getValue());
                                            }
                                        }
                                    }

                                    Supplier<ChannelHandler> handlerSupplier = () -> PluginManager.EVENT_MANAGER.call(new Client2ProxyHandlerCreationEvent(new PassthroughClient2ProxyHandler(), true)).getHandler();
                                    PassthroughClient2ProxyChannelInitializer channelInitializer = new PassthroughClient2ProxyChannelInitializer(handlerSupplier);
                                    try {
                                        initChannelMethod.invoke(channelInitializer, ctx.channel());
                                    } catch (IllegalAccessException | InvocationTargetException e) {
                                        throw new RuntimeException(e);
                                    }
                                    ctx.fireChannelActive();
                                    ctx.fireChannelRead(msg.retain());
                                    ctx.pipeline().remove(this);
                                } else {
                                    ctx.pipeline().remove(this);
                                    ctx.pipeline().fireChannelRead(msg.retain());
                                }
                            }
                        }
                    }
                });
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
        try {
            initChannelMethod = PassthroughClient2ProxyChannelInitializer.class.getDeclaredMethod("initChannel", Channel.class);
            initChannelMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public static final class EaglercraftClientConnected {
        public static final EaglercraftClientConnected INSTANCE = new EaglercraftClientConnected();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ExceptionUtil.handleNettyException(ctx, cause, null);
    }
}
