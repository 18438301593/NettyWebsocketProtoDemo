package club.jiajiajia.nwsp.server;

import club.jiajiajia.nwsp.dto.MyMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @program: qingcheng
 * @author: XIONG CHUAN
 * @create: 2019-04-28 15:21
 * @description: netty服务端处理类
 **/

public class NettyServerHandler extends ChannelInboundHandlerAdapter {

    /**
     * 管理一个全局map，保存连接进服务端的通道数量
     */
    private static final ConcurrentHashMap<ChannelId, ChannelHandlerContext> CHANNEL_MAP = new ConcurrentHashMap<ChannelId, ChannelHandlerContext>();

    /**
     * @param ctx
     * @author xiongchuan on 2019/4/28 16:10
     * @DESCRIPTION: 有客户端连接服务器会触发此函数
     * @return: void
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {

        InetSocketAddress insocket = (InetSocketAddress) ctx.channel().remoteAddress();

        String clientIp = insocket.getAddress().getHostAddress();
        int clientPort = insocket.getPort();

        //获取连接通道唯一标识
        ChannelId channelId = ctx.channel().id();

        //如果map中不包含此连接，就保存连接
        if (CHANNEL_MAP.containsKey(channelId)) {
            System.out.println("客户端【" + channelId + "】是连接状态，连接通道数量: " + CHANNEL_MAP.size());
        } else {
            //保存连接
            CHANNEL_MAP.put(channelId, ctx);

            System.out.println("客户端【" + channelId + "】连接netty服务器[IP:" + clientIp + "--->PORT:" + clientPort + "]");
            System.out.println("连接通道数量: " + CHANNEL_MAP.size());
        }
    }

    /**
     * @param ctx
     * @author xiongchuan on 2019/4/28 16:10
     * @DESCRIPTION: 有客户端终止连接服务器会触发此函数
     * @return: void
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {

        InetSocketAddress insocket = (InetSocketAddress) ctx.channel().remoteAddress();

        String clientIp = insocket.getAddress().getHostAddress();

        ChannelId channelId = ctx.channel().id();

        //包含此客户端才去删除
        if (CHANNEL_MAP.containsKey(channelId)) {
            //删除连接
            CHANNEL_MAP.remove(channelId);

            System.out.println();
            System.out.println("客户端【" + channelId + "】退出netty服务器[IP:" + clientIp + "--->PORT:" + insocket.getPort() + "]");
            System.out.println("连接通道数量: " + CHANNEL_MAP.size());
        }
    }

    /**
     * @param ctx
     * @author xiongchuan on 2019/4/28 16:10
     * @DESCRIPTION: 有客户端发消息会触发此函数
     * @return: void
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println();
        System.out.println("加载客户端报文......"+msg);
        /**
         *  下面可以解析数据，保存数据，生成返回报文，将需要返回报文写入write函数
         *
         */
        /** 响应客户端 **/
        MyMessage.MyMessageInfo message = (MyMessage.MyMessageInfo)msg;
        MyMessage.MyMessageInfo.Builder builder = MyMessage.MyMessageInfo.newBuilder().setId(1).setName("你好"+message.getName()+",我是boss!").setAge(12);
        this.channelWrite(ctx.channel().id(),builder);
    }

    private int readIdleTimes=0;

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt)throws Exception{
        System.out.println("::::"+evt);
        if(evt instanceof IdleStateEvent){
            IdleStateEvent event = (IdleStateEvent)evt;
            String eventType = null;
            switch (event.state()){
                case READER_IDLE:
                    eventType = "读空闲";
                    readIdleTimes ++; // 读空闲的计数加1
                    break;
                case WRITER_IDLE:
                    eventType = "写空闲";
                    // 不处理
                    break;
                case ALL_IDLE:
                    eventType ="读写空闲";
                    // 不处理
                    break;
            }
            System.out.println(ctx.channel().remoteAddress() + "超时事件：" +eventType);
            if(readIdleTimes > 3){
                System.out.println(" [server]读空闲超过3次，关闭连接");
                ctx.channel().close();
            }
        }
    }

    private static final Object obj=new Object();

    /**
     * @param msg        需要发送的消息内容
     * @param channelId 连接通道唯一id
     * @author xiongchuan on 2019/4/28 16:10
     * @DESCRIPTION: 服务端给客户端发送消息
     * @return: void
     */
    public void channelWrite(ChannelId channelId, Object msg) throws Exception {

        ChannelHandlerContext ctx = CHANNEL_MAP.get(channelId);

        if (ctx == null) {
            System.out.println("通道【" + channelId + "】不存在");
            return;
        }

        if (msg == null || msg == "") {
            System.out.println("服务端响应空的消息");
            return;
        }

        /** 将客户端的信息直接返回写入ctx **/
        ctx.write(msg);
        /** 刷新缓存区 **/
        ctx.flush();
    }

    /**
     * @param ctx
     * @author xiongchuan on 2019/4/28 16:10
     * @DESCRIPTION: 发生异常会触发此函数
     * @return: void
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

        System.out.println();
        ctx.close();
        System.out.println(ctx.channel().id() + " 发生了错误,此连接被关闭" + "此时连通数量: " + CHANNEL_MAP.size());
        cause.printStackTrace();
    }
}