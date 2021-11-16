package club.jiajiajia.nwsp;

import club.jiajiajia.nwsp.server.NettyServer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.InetSocketAddress;

@SpringBootApplication
public class MainApplication {
    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class,args);

        // 启动 netty 服务
        InetSocketAddress inetSocketAddress=new InetSocketAddress(8899);
        NettyServer.start(inetSocketAddress);
    }
}
