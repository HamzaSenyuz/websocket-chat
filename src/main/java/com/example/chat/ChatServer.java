package com.example.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/chat")
public class ChatServer {

    // Tüm ChatServer instance'larının paylaştığı ortak sözlük.
    // Key: kullanıcı adı, Value: o kullanıcının Session'ı.
    private static final Map<String, Session> users = new ConcurrentHashMap<>();

    // JSON <-> Java dönüşümü için tek bir ObjectMapper yeterli (thread-safe).
    private static final ObjectMapper json = new ObjectMapper();

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("Yeni bağlantı: " + session.getId() + " (henüz anonim)");

        session.setMaxIdleTimeout(60_000); // 60 saniye
    }

    @OnMessage
    public void onMessage(String rawMessage, Session session) {
        try {
            Map<String, Object> msg = json.readValue(rawMessage, Map.class);
            String type = (String) msg.get("type");

            if (type == null) {
                sendError(session, "Mesajda 'type' alanı yok.");
                return;
            }

            switch (type) {
                case "join"    -> handleJoin(msg, session);
                case "message" -> handleMessage(msg, session);
                default        -> sendError(session, "Bilinmeyen mesaj tipi: " + type);
            }
        } catch (IOException e) {
            sendError(session, "Geçersiz JSON.");
        }
    }

    @OnClose
    public void onClose(Session session) {
        String username = findUsernameBySession(session);
        if (username != null) {
            users.remove(username);
            System.out.println("Ayrıldı: " + username);
            broadcast(Map.of("type", "userLeft", "username", username));
            broadcast(Map.of("type", "userList", "users", users.keySet()));
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        System.err.println("Hata (" + session.getId() + "): " + error.getMessage());
    }

    private void handleJoin(Map<String, Object> msg, Session session) {
        String username = (String) msg.get("username");

        if (username == null || username.isBlank()) {
            sendError(session, "Kullanıcı adı boş olamaz.");
            return;
        }
        if (users.containsKey(username)) {
            sendError(session, "Bu kullanıcı adı zaten kullanılıyor.");
            return;
        }

        users.put(username, session);
        System.out.println("Katıldı: " + username);

        sendTo(session, Map.of("type", "system", "text", "Hoşgeldin " + username));
        broadcast(Map.of("type", "userJoined", "username", username));
        broadcast(Map.of("type", "userList", "users", users.keySet()));
    }

    private void handleMessage(Map<String, Object> msg, Session session) {
        String username = findUsernameBySession(session);
        if (username == null) {
            sendError(session, "Önce 'join' mesajı ile kullanıcı adı belirtmelisin.");
            return;
        }

        String text = (String) msg.get("text");
        if (text == null || text.isBlank()) return;

        broadcast(Map.of("type", "message", "from", username, "text", text));
    }

    private String findUsernameBySession(Session session) {
        for (Map.Entry<String, Session> e : users.entrySet()) {
            if (e.getValue().equals(session)) return e.getKey();
        }
        return null;
    }

    private void broadcast(Map<String, Object> payload) {
        String jsonText;
        try {
            jsonText = json.writeValueAsString(payload);
        } catch (IOException e) {
            System.err.println("JSON yazma hatası: " + e.getMessage());
            return;
        }
        for (Session s : users.values()) {
            sendRaw(s, jsonText);
        }
    }

    private void sendTo(Session session, Map<String, Object> payload) {
        try {
            sendRaw(session, json.writeValueAsString(payload));
        } catch (IOException e) {
            System.err.println("JSON yazma hatası: " + e.getMessage());
        }
    }

    private void sendError(Session session, String text) {
        sendTo(session, Map.of("type", "error", "text", text));
    }

    private void sendRaw(Session session, String text) {
        try {
            session.getBasicRemote().sendText(text);
        } catch (IOException e) {
            System.err.println("Gönderim hatası: " + e.getMessage());
        }
    }
}