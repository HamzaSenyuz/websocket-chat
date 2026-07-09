package com.example.chat;

import com.sun.net.httpserver.HttpServer;
import org.glassfish.tyrus.server.Server;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) {
        // 1) WebSocket sunucusu (Tyrus) — port 8025
        Server wsServer = new Server(
                "localhost",
                8025,
                "/websockets",
                null,
                ChatServer.class
        );

        // 2) HTTP sunucusu (JDK yerleşik) — port 8080, index.html'i sunar
        HttpServer httpServer;
        try {
            httpServer = HttpServer.create(new InetSocketAddress(8080), 0);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        httpServer.createContext("/", exchange -> {
            try (InputStream is = Main.class.getResourceAsStream("/index.html")) {
                if (is == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] body = is.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.getResponseBody().close();
            }
        });

        try {
            wsServer.start();
            httpServer.start();
            System.out.println("WebSocket sunucusu: ws://localhost:8025/websockets/chat");
            System.out.println("Chat arayüzü:       http://localhost:8080/");
            System.out.println("Kapatmak için Enter'a bas.");
            System.in.read();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            wsServer.stop();
            httpServer.stop(0);
            System.out.println("Sunucular kapatıldı.");
        }
    }
}