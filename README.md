# WebSocket Chat

Jakarta WebSocket ve Tyrus kullanılarak Java ile geliştirilmiş, gerçek zamanlı bir sohbet uygulaması. Ham WebSocket protokolü üzerine kurulmuş, SignalR veya Spring WebSocket gibi üst seviye kütüphaneler kullanılmamıştır.

## Özellikler

- Kullanıcı adı ile giriş
- Aynı anda birden fazla kullanıcı desteği
- Anlık mesajlaşma (broadcast)
- Bağlı kullanıcıların canlı listesi
- Kullanıcı giriş/çıkış bildirimleri
- Bağlantı kopması yönetimi:
  - Sunucu tarafı idle timeout (60 sn)
  - İstemci tarafı otomatik yeniden bağlanma (exponential backoff)

## Teknolojiler

- **Java 21**
- **Jakarta WebSocket API 2.1.1** (JSR 356 — Java'nın resmi WebSocket standardı)
- **Tyrus 2.1.5** — Jakarta WebSocket referans implementation'ı
- **Jackson 2.17.2** — JSON serileştirme
- **JDK HttpServer** — HTML istemcisinin sunulması için (ek kütüphane yok)
- **Maven** — bağımlılık yönetimi

## Mimari

- **WebSocket sunucusu**: `ws://localhost:8025/websockets/chat` (Tyrus, port 8025)
- **HTTP sunucusu**: `http://localhost:8080/` (JDK HttpServer, `index.html`'i sunar)
- **İstemci**: Saf HTML + JavaScript, tarayıcının yerleşik `WebSocket` API'sini kullanır

Sunucu, her yeni bağlantı için ayrı bir `ChatServer` instance'ı oluşturur. Ortak state (bağlı kullanıcı sözlüğü) `ConcurrentHashMap` içinde tutulur; thread-safe eş zamanlı erişim garanti edilir.

## Mesaj Protokolü

Tüm mesajlar JSON formatındadır ve bir `type` alanı içerir (message envelope deseni).

**İstemciden sunucuya:**
```json
{ "type": "join", "username": "hamza" }
{ "type": "message", "text": "Merhaba" }
```

**Sunucudan istemcilere:**
```json
{ "type": "system", "text": "Hoşgeldin hamza" }
{ "type": "message", "from": "hamza", "text": "Merhaba" }
{ "type": "userJoined", "username": "hamza" }
{ "type": "userLeft", "username": "hamza" }
{ "type": "userList", "users": ["hamza", "ayse"] }
{ "type": "error", "text": "..." }
```

## Kurulum ve Çalıştırma

Gereksinimler: Java 21+ ve Maven.

```bash
git clone https://github.com/HamzaSenyuz/websocket-chat.git
cd websocket-chat
mvn compile exec:java -Dexec.mainClass="com.example.chat.Main"
```

Alternatif olarak IntelliJ IDEA ile projeyi açıp `Main.java`'yı çalıştırabilirsiniz.

Tarayıcıdan `http://localhost:8080/` adresine gidin, kullanıcı adı girin, katılın. Farklı sekmelerde farklı kullanıcı adlarıyla açarak mesajlaşmayı test edebilirsiniz.

## Proje Yapısı
websocket-chat/
├── src/main/java/com/example/chat/
│   ├── Main.java          # WebSocket ve HTTP sunucularını başlatır
│   └── ChatServer.java    # WebSocket endpoint, mesaj mantığı
├── src/main/resources/
│   └── index.html         # Chat arayüzü (HTML + CSS + JS)
├── pom.xml

## Bağlantı Yönetimi Detayları

**Sunucu tarafı:**
- `session.setMaxIdleTimeout(60_000)` ile 60 saniyelik idle timeout. Bu süre boyunca istemciden hiçbir veri gelmezse (ping'e cevap dahil) Tyrus bağlantıyı otomatik kapatır ve `@OnClose` tetiklenir. Böylece "kirli kopma" (internet kesildi ama Close frame gönderilemedi) durumu ele alınır.

**İstemci tarafı:**
- `WebSocket.onclose` tetiklendiğinde otomatik yeniden bağlanma başlar.
- Exponential backoff: 1sn → 2sn → 4sn → ... → 30sn (tavan).
- Başarılı bağlantı olduğunda gecikme 1sn'ye sıfırlanır.
- Kullanıcı adı JavaScript state'inde tutulduğu için reconnect sonrası otomatik `join` gönderilir; kullanıcının tekrar giriş yapması gerekmez.

## Notlar

Bu proje bir staj ödevi kapsamında hazırlanmıştır. Prodüksiyon kullanımı için ek olarak şunlar yapılmalıdır: TLS (`wss://`), kimlik doğrulama, rate limiting, kalıcı depolama, çok sunuculu ölçekleme için Redis pub/sub, reverse proxy (nginx), loglama framework'ü.
