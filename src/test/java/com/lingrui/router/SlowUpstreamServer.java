package com.lingrui.router;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class SlowUpstreamServer {

    public static void main(String[] args) throws Exception{
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 18081), 0);

        server.createContext("/chat/completions", exchange -> {
            try {
                //读完请求体
                exchange.getRequestBody().readAllBytes();
                //告诉中转站，这是一个SSE响应
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
                exchange.sendResponseHeaders(200, 0);

                //先发送一条包含正文的事件
                String event = "data: {\"choices\":[{\"index\":0,"
                        + "\"delta\":{\"role\":\"assistant\","
                        + "\"content\":\"这是超时前的一段回答。\"}}]}\n\n";

                exchange.getResponseBody().write(event.getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                System.out.println("已发送第一条事件，接下来暂停 70 秒");
                //模拟发送了一部分回答后，上游卡住
                Thread.sleep(70_000);

            } catch (InterruptedException exception){
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });

        server.start();
        System.out.println("测试服务已启动：http://127.0.0.1:18081");
    }
}
