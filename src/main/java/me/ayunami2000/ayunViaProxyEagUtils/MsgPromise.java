package me.ayunami2000.ayunViaProxyEagUtils;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;

public class MsgPromise {
    public Object msg;
    public ChannelPromise promise;
    public MsgPromise(Object msg, ChannelPromise promise) {
        this.msg = msg;
        this.promise = promise;
    }
}
