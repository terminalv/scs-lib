package com.identityblitz.scs.glue.netty.http;

import com.identityblitz.scs.ConfigParameter;
import com.identityblitz.scs.SCSService;
import com.identityblitz.scs.SCSession;
import com.identityblitz.scs.error.SCSExpiredException;
import com.identityblitz.scs.service.ServiceProvider;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.*;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.identityblitz.scs.LoggingUtils.getLogger;
import static com.identityblitz.scs.service.ServiceProvider.service;
import static io.netty.handler.codec.http.HttpHeaders.Names.COOKIE;
import static io.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;

/**
 * This Netty handler allows to add Secure Cookie Session (SCS) functionality to Netty HTTP protocol.
 * The handler sould be insert into the pipeline after http handlers
 * {@link io.netty.handler.codec.http.HttpRequestDecoder}, {@link io.netty.handler.codec.http.HttpObjectAggregator},
 * {@link io.netty.handler.codec.http.HttpResponseEncoder} and before any handlers that use SCS.
 * To get current session state it is necessary to use the function
 * {@link com.identityblitz.scs.SCSService#getSCS(Object)}
 * and to change the current session state - the function
 * {@link com.identityblitz.scs.SCSService#changeSCS(Object, String)}.
 * The filter has only one boolean option to configure <b>com.blitz.scs.useCompression</b>. This option turns off/on
 * using of compression session state. Default value is not to use compression.
 */
public class SCSHandler extends MessageToMessageDecoder<FullHttpRequest> implements ChannelOutboundHandler {
    private static final String SCS_COOKIE_NAME = service().getConfiguration()
            .getString(ConfigParameter.SCS_COOKIE_NAME.key(), "SCS");
    private static final String DOMAIN = service().getConfiguration()
            .getString(ConfigParameter.DOMAIN.key());
    private static final boolean IS_SECURE = service().getConfiguration()
            .getBoolean(ConfigParameter.IS_SECURE.key(), false);
    private static final String PATH = service().getConfiguration()
            .getString(ConfigParameter.PATH.key(), "/");

    private static final SCSService scsService;
    static {
        scsService = new SCSService();
        scsService.init(service().getConfiguration()
                .getBoolean(ConfigParameter.USE_COMPRESSION.key(), false), null);
    }

    private SCSFullHttpRequest req;

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof HttpResponse) {
            HttpResponse response = (HttpResponse) msg;
            final String cookiesHeader = response.headers().get(SET_COOKIE);
            final Set<Cookie> cookies;
            if (cookiesHeader != null)
                cookies = CookieDecoder.decode(cookiesHeader);
            else
                cookies = Collections.emptySet();
            final Set<Cookie> newCookies = new TreeSet<Cookie>(cookies);

            final String state = req.getSCS();
            this.req = null;
            if(state != null) {
                final SCSession session = scsService.encode(state);
                getLogger().debug("session state is stored into SCS cookie {}.", session);
                DefaultCookie scsCookie = new DefaultCookie(SCS_COOKIE_NAME, session.asString());
                scsCookie.setDomain(DOMAIN);
                scsCookie.setPath(PATH);
                scsCookie.setHttpOnly(true);
                scsCookie.setSecure(IS_SECURE);
                newCookies.add(scsCookie);
            }
            else {
                getLogger().debug("there is no session state to store in SCS cookie.");
                final Cookie scs = findCookie(newCookies, SCS_COOKIE_NAME);
                if(scs != null)
                    newCookies.remove(scs);
            }
            response.headers().set(SET_COOKIE, ServerCookieEncoder.encode(newCookies));

        }
        ctx.writeAndFlush(msg, promise);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, FullHttpRequest msg, List<Object> out) throws Exception {
        String state = null;
        final String cookieHeader = msg.headers().get(COOKIE);
        if (cookieHeader != null) {
            final Set<Cookie> cookies = CookieDecoder.decode(cookieHeader);
            final Cookie cookie = findCookie(cookies, SCS_COOKIE_NAME);
            if (cookie != null) {
                try {
                    SCSession session = scsService.decode(cookie.getValue());
                    if(session != null) {
                        getLogger().debug("SCS [{}] is extracted from request cookie.", session);
                        state = session.getData();
                    }
                } catch (SCSExpiredException e) {
                }
            }
        }
        final SCSFullHttpRequest request = new SCSFullHttpRequest(msg, state);
        request.retain();
        this.req = request;
        out.add(request);
    }

    private Cookie findCookie(final Set<Cookie> cookies, final String name) {
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(name))
                return cookie;
        }
        return null;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        this.req = null;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        this.req = null;
    }

    @Override
    public void bind(ChannelHandlerContext ctx, SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.bind(localAddress, promise);
    }

    @Override
    public void connect(ChannelHandlerContext ctx, SocketAddress remoteAddress,
                        SocketAddress localAddress, ChannelPromise promise) throws Exception {
        ctx.connect(remoteAddress, localAddress, promise);
    }

    @Override
    public void disconnect(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.disconnect(promise);
    }

    @Override
    public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.close(promise);
    }

    @Override
    public void deregister(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
        ctx.deregister(promise);
    }

    @Override
    public void read(ChannelHandlerContext ctx) throws Exception {
        ctx.read();
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

}
