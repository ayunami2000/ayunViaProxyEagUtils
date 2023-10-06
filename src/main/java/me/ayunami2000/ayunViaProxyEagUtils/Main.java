package me.ayunami2000.ayunViaProxyEagUtils;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import net.lenni0451.lambdaevents.EventHandler;
import net.raphimc.netminecraft.constants.MCPipeline;
import net.raphimc.netminecraft.netty.connection.NetClient;
import net.raphimc.netminecraft.util.ServerAddress;
import net.raphimc.vialegacy.protocols.release.protocol1_7_2_5to1_6_4.types.Types1_6_4;
import net.raphimc.vialoader.util.VersionEnum;
import net.raphimc.viaproxy.plugins.PluginManager;
import net.raphimc.viaproxy.plugins.ViaProxyPlugin;
import net.raphimc.viaproxy.plugins.events.Client2ProxyChannelInitializeEvent;
import net.raphimc.viaproxy.plugins.events.Proxy2ServerChannelInitializeEvent;
import net.raphimc.viaproxy.plugins.events.types.ITyped;
import net.raphimc.viaproxy.proxy.session.LegacyProxyConnection;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import net.raphimc.viaproxy.proxy.util.ChannelUtil;
import net.raphimc.viaproxy.proxy.util.ExceptionUtil;

import javax.net.ssl.*;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class Main extends ViaProxyPlugin {
    public static final AttributeKey<Boolean> secureWs = AttributeKey.newInstance("eag-secure-ws");
    public static final AttributeKey<String> wsPath = AttributeKey.newInstance("eag-ws-path");
    public static final AttributeKey<String> eagxPass = AttributeKey.newInstance("eag-x-pass");
    private static final SSLContext sc;

    static {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }

                        public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }
                    }};

            // Ignore differences between given hostname and certificate hostname
            sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public void onEnable() {
        PluginManager.EVENT_MANAGER.register(this);
        (new FunnyConfig(new File("ViaLoader", "vpeagutils.yml"))).reloadConfig();
        EaglerXSkinHandler.skinService = new SkinService();
    }

    @EventHandler
    public void onEvent(final Proxy2ServerChannelInitializeEvent event) throws URISyntaxException {
        if (event.getType() == ITyped.Type.POST) {
            Channel ch = event.getChannel();

            NetClient proxyConnection;
            Channel c2p;
            ServerAddress addr;
            if (event.isLegacyPassthrough()) {
                proxyConnection = LegacyProxyConnection.fromChannel(ch);
                c2p = ((LegacyProxyConnection) proxyConnection).getC2P();
                addr = ((LegacyProxyConnection) proxyConnection).getServerAddress();
            } else {
                proxyConnection = ProxyConnection.fromChannel(ch);
                c2p = ((ProxyConnection) proxyConnection).getC2P();
                addr = ((ProxyConnection) proxyConnection).getServerAddress();
            }

            if (FunnyConfig.eaglerServerMode == 1) {
                c2p.attr(secureWs).set(false);
            } else if (FunnyConfig.eaglerServerMode == 2) {
                c2p.attr(secureWs).set(true);
            }

            if (c2p.hasAttr(secureWs)) {
                doWsServerStuff(ch, proxyConnection, c2p, addr);
                if (!event.isLegacyPassthrough()) {
                    ch.pipeline().addFirst("handshake-waiter", new ChannelOutboundHandlerAdapter() {
                        private boolean hasHandshake = false;

                        @Override
                        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                            if (msg instanceof ByteBuf) {
                                hasHandshake = true;
                                ChannelUtil.restoreAutoRead(c2p);
                            }
                            super.write(ctx, msg, promise);
                        }

                        @Override
                        public void flush(ChannelHandlerContext ctx) throws Exception {
                            super.flush(ctx);
                            if (hasHandshake) {
                                ch.pipeline().remove(this);
                                ChannelUtil.disableAutoRead(c2p);
                            }
                        }
                    });
                }
            }
        }
    }

    private static void doWsServerStuff(Channel ch, NetClient proxyConnection, Channel c2p, ServerAddress addr) throws URISyntaxException {
        ch.attr(MCPipeline.COMPRESSION_THRESHOLD_ATTRIBUTE_KEY).set(-2);
        if (proxyConnection instanceof ProxyConnection && ((ProxyConnection) proxyConnection).getServerVersion().isNewerThan(VersionEnum.r1_6_4)) {
            ch.pipeline().remove(MCPipeline.SIZER_HANDLER_NAME);
        } else if (ch.pipeline().get(MCPipeline.ENCRYPTION_HANDLER_NAME) != null) {
            ch.pipeline().remove(MCPipeline.ENCRYPTION_HANDLER_NAME);
        }
        if (c2p.pipeline().get("ayun-eag-voice") != null) {
            c2p.pipeline().remove("ayun-eag-voice");
        }
        if (c2p.pipeline().get("ayun-eag-skin") != null) {
            c2p.pipeline().remove("ayun-eag-skin");
        }
        if (c2p.pipeline().get("ayun-eag-x-login") != null) {
            c2p.pipeline().remove("ayun-eag-x-login");
        }
        if (c2p.pipeline().get("ayun-eag-skin-x") != null) {
            c2p.pipeline().remove("ayun-eag-skin-x");
        }
        StringBuilder url = new StringBuilder("ws");
        boolean secure = c2p.attr(secureWs).get();
        if (secure) {
            final SSLEngine sslEngine = sc.createSSLEngine(addr.getAddress(), addr.getPort());
            sslEngine.setUseClientMode(true);
            sslEngine.setNeedClientAuth(false);
            ch.pipeline().addFirst("eag-server-ssl", new SslHandler(sslEngine) {
                @Override
                public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                    if (this.handshakeFuture().cause() != null) {
                        ExceptionUtil.handleNettyException(ctx, this.handshakeFuture().cause(), null);
                    }
                    super.closeOutbound();
                }
            });
            url.append("s");
            ch.pipeline().addAfter("eag-server-ssl", "eag-server-http-codec", new HttpClientCodec());
        } else {
            ch.pipeline().addFirst("eag-server-http-codec", new HttpClientCodec());
        }
        url.append("://").append(addr.getAddress());
        boolean addPort = (secure && addr.getPort() != 443) || (!secure && addr.getPort() != 80);
        if (addPort) {
            url.append(":").append(addr.getPort());
        }
        String path = c2p.attr(wsPath).get();
        if (path != null) {
            url.append("/").append(path);
        }
        URI uri = new URI(url.toString());
        HttpHeaders headers = new DefaultHttpHeaders();
        headers.set(HttpHeaderNames.HOST, uri.getHost() + (addPort ? ":" + uri.getPort() : ""));
        headers.set(HttpHeaderNames.ORIGIN, "via.shhnowisnottheti.me");
        ch.pipeline().addAfter("eag-server-http-codec", "eag-server-http-aggregator", new HttpObjectAggregator(2097152, true));
        ch.pipeline().addAfter("eag-server-http-aggregator", "eag-server-ws-compression", WebSocketClientCompressionHandler.INSTANCE);
        ch.pipeline().addAfter("eag-server-ws-compression", "eag-server-ws-handshaker", new WebSocketClientProtocolHandler(WebSocketClientHandshakerFactory.newHandshaker(uri, WebSocketVersion.V13, null, true, headers, 2097152)));
        ch.pipeline().addAfter("eag-server-ws-handshaker", "eag-server-ws-ready", new WebSocketConnectedNotifier());
        ch.pipeline().addAfter("eag-server-ws-ready", "eag-server-handler", new EaglerServerHandler(proxyConnection, c2p.attr(eagxPass).get()));
    }

    @EventHandler
    public void onEvent(final Client2ProxyChannelInitializeEvent event) {
        if (event.isLegacyPassthrough()) return;
        if (event.getType() == ITyped.Type.PRE) {
            event.getChannel().pipeline().addLast("eaglercraft-initial-handler", new EaglercraftInitialHandler());
        }
        if (event.getType() == ITyped.Type.POST) {
            event.getChannel().pipeline().addAfter("eaglercraft-initial-handler", "ayun-eag-detector", new EaglerConnectionHandler());
        }
    }

    static class EaglerConnectionHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(final ChannelHandlerContext ctx, final Object evt) throws Exception {
            super.userEventTriggered(ctx, evt);
            if (evt instanceof EaglercraftInitialHandler.EaglercraftClientConnected) {
                ctx.pipeline().remove("ayun-eag-detector");
                ctx.pipeline().addBefore("eaglercraft-handler", "ayun-eag-utils-init", new EaglerUtilsInitHandler());
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ExceptionUtil.handleNettyException(ctx, cause, null);
        }
    }

    static class EaglerUtilsInitHandler extends ChannelInboundHandlerAdapter {
        @Override
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

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ExceptionUtil.handleNettyException(ctx, cause, null);
        }
    }
}
