package club.jiajiajia.nwsp.server;


import club.jiajiajia.nwsp.dto.MyMessage;
import com.google.protobuf.MessageLite;
import com.google.protobuf.MessageLiteOrBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.List;
import java.util.concurrent.TimeUnit;


public class NettyServerChannelInitializer extends ChannelInitializer<SocketChannel>  {

    @Override
    protected void initChannel(final SocketChannel channel) throws Exception {
        ChannelPipeline pipeline = channel.pipeline();
        pipeline.addLast(new HttpServerCodec());
        //支持写大数据流
        pipeline.addLast(new ChunkedWriteHandler());
        //http聚合器
        pipeline.addLast(new HttpObjectAggregator(1024*62));
        //websocket支持,设置路由
        pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));

        //解码器，通过Google Protocol Buffers序列化框架动态的切割接收到的ByteBuf
        pipeline.addLast(new ProtobufVarint32FrameDecoder());
        //Google Protocol Buffers 长度属性编码器
        pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());

        // 协议包解码
        pipeline.addLast(new MessageToMessageDecoder<WebSocketFrame>() {
            @Override
            protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> objs) throws Exception {
                System.out.println("received client msg ------------------------");
                if (frame instanceof TextWebSocketFrame) {
                    // 文本消息
                    TextWebSocketFrame textFrame = (TextWebSocketFrame)frame;
                    System.out.println("MsgType is TextWebSocketFrame");
                } else if (frame instanceof BinaryWebSocketFrame) {
                    // 二进制消息
                    ByteBuf buf = ((BinaryWebSocketFrame) frame).content();
                    objs.add(buf);
                    // 自旋累加
                    buf.retain();
                    System.out.println("MsgType is BinaryWebSocketFrame");
                } else if (frame instanceof PongWebSocketFrame) {
                    // PING存活检测消息
                    System.out.println("MsgType is PongWebSocketFrame ");
                } else if (frame instanceof CloseWebSocketFrame) {
                    // 关闭指令消息
                    System.out.println("MsgType is CloseWebSocketFrame");
                    channel.close();
                }

            }
        });

        // 协议包编码，编码成二进制数据，通过websocket发送
        pipeline.addLast(new MessageToMessageEncoder<MessageLiteOrBuilder>() {
            @Override
            protected void encode(ChannelHandlerContext ctx, MessageLiteOrBuilder msg, List<Object> out) throws Exception {
                ByteBuf result = null;
                if (msg instanceof MessageLite) {
                    result = wrappedBuffer(((MessageLite) msg).toByteArray());
                }
                if (msg instanceof MessageLite.Builder) {
                    result = wrappedBuffer(((MessageLite.Builder) msg).build().toByteArray());
                }
                // 封装二进制数据
                WebSocketFrame frame = new BinaryWebSocketFrame(result);
                out.add(frame);
            }
        });

        // protobuf解码器
        channel.pipeline().addLast("decoder",new ProtobufDecoder(MyMessage.MyMessageInfo.getDefaultInstance()));

        // 超时心跳检测
        channel.pipeline().addLast(new IdleStateHandler(5,5,5, TimeUnit.SECONDS));
        // 自定义处理器
        channel.pipeline().addLast(new NettyServerHandler());
    }

    private ByteBuf wrappedBuffer(byte[] toByteArray) {
        return Unpooled.wrappedBuffer(toByteArray);
    }

}
