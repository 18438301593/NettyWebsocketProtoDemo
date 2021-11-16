# NettyWebsocketProtoDemo

&emsp;&emsp;github地址：[https://github.com/18438301593/NettyWebsocketProtoDemo](https://github.com/18438301593/NettyWebsocketProtoDemo)

&emsp;&emsp;基于java nio + netty + websocket + protobuf +javascript等技术实现前后端高性能实时数据传输的demo模型。

主要过程分析：
### 一、.proto文件编写，生成java类，以及javacript文件。
  
  &emsp;&emsp;参考文章：
  
  &emsp;&emsp;[http://www.jiajiajia.club/blog/artical/351psy9r6l0g/464](http://www.jiajiajia.club/blog/artical/351psy9r6l0g/464)，
  
  &emsp;&emsp;[http://www.jiajiajia.club/blog/artical/ydn9dpg64gkf/466](http://www.jiajiajia.club/blog/artical/ydn9dpg64gkf/466)

### 二、集成websocket
&emsp;&emsp;websocket协议的建立连接的阶段采用了http协议的方式，所以会看到http协议相关的解码器和编码器。如下：
```java
        pipeline.addLast(new HttpServerCodec());
        //支持写大数据流
        pipeline.addLast(new ChunkedWriteHandler());
        //http聚合器
        pipeline.addLast(new HttpObjectAggregator(1024*62));
        //websocket支持,设置路由
        pipeline.addLast(new WebSocketServerProtocolHandler("/ws"));
        
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
```

### 三、 支持protobuf协议
&emsp;&emsp;主要配置解码器和编码器
```java
        //解码器，通过Google Protocol Buffers序列化框架动态的切割接收到的ByteBuf
        pipeline.addLast(new ProtobufVarint32FrameDecoder());
        //Google Protocol Buffers 长度属性编码器
        pipeline.addLast(new ProtobufVarint32LengthFieldPrepender());
        
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
```
### 四、支持心跳检测，超时关闭连接
&emsp;&emsp;解码器配置
```java
        // 超时心跳检测
        channel.pipeline().addLast(new IdleStateHandler(5,5,5, TimeUnit.SECONDS));
```

&emsp;&emsp;业务处理在NettyServerHandler类的userEventTriggered()方法。
```java
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
```

### 五、前端websocket接受到数据如何解析？
&emsp;&emsp;websocket是一帧一帧发送的，所以要读取所有数据，在转成字节数据，才能反序列化成js对象。
```javascript
    ws.onmessage = function (event) {
        var reader = new FileReader();
        reader.readAsArrayBuffer(event.data);
        reader.onload = function (e) {
            var buf = new Uint8Array(reader.result);
            var j = proto.MyMessageInfo.deserializeBinary(buf);
            var t = document.getElementById("message");
            t.innerHTML = j.getName();
        }
    };
```
