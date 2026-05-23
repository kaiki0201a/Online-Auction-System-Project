package com.auction.client;

import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import javafx.application.Platform;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * NetworkClient — Singleton TCP client với multi-listener EventBus.
 *
 * FIX CRITICAL: Thay thế single-listener (currentListener) bằng ConcurrentHashMap.
 * Mỗi màn hình đăng ký với key riêng → không còn ghi đè nhau.
 * Tất cả listeners đều nhận BROADCAST từ server (AUCTION_CREATED, AUCTION_APPROVED, v.v.)
 */
public class NetworkClient {

    private static volatile NetworkClient instance;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    // FIX: Dùng Map thay vì 1 listener duy nhất — mỗi screen giữ key riêng
    private final Map<String, Consumer<Response>> listeners = new ConcurrentHashMap<>();

    // Key mặc định cho backward compatibility
    private static final String DEFAULT_KEY = "default";

    private NetworkClient() {}

    public static NetworkClient getInstance() {
        if (instance == null) {
            synchronized (NetworkClient.class) {
                if (instance == null) {
                    instance = new NetworkClient();
                }
            }
        }
        return instance;
    }

    // ─── EventBus API ────────────────────────────────────────────────────────

    /**
     * Đăng ký listener với key định danh (VD: "seller", "admin", "bidder").
     * Mỗi key ghi đè listener cũ có cùng key — nhưng không ảnh hưởng key khác.
     */
    public void addEventListener(String key, Consumer<Response> callback) {
        listeners.put(key, callback);
    }

    /**
     * Gỡ listener theo key khi màn hình đóng/navigate đi.
     */
    public void removeEventListener(String key) {
        listeners.remove(key);
    }

    /**
     * Backward compatibility: setOnResponseReceived → ghi vào key "default".
     * Các màn hình cũ dùng API này vẫn hoạt động bình thường.
     */
    public void setOnResponseReceived(Consumer<Response> callback) {
        listeners.put(DEFAULT_KEY, callback);
    }

    /**
     * Backward compatibility: removeOnResponseReceived → xóa key "default".
     */
    public void removeOnResponseReceived() {
        listeners.remove(DEFAULT_KEY);
    }

    /**
     * Xóa toàn bộ listeners (khi disconnect hoàn toàn).
     */
    public void removeAllListeners() {
        listeners.clear();
    }

    // ─── Network API ─────────────────────────────────────────────────────────

    public void connect(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);

            // BẮT BUỘC khởi tạo ObjectOutputStream TRƯỚC để chống Deadlock
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            System.out.println("✅ Đã kết nối thành công tới Server!");
            startListening();

        } catch (IOException e) {
            System.err.println("❌ Lỗi kết nối tới Server: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return socket != null && !socket.isClosed() && socket.isConnected();
    }

    public void sendRequest(Request request) {
        if (socket != null && !socket.isClosed()) {
            try {
                out.reset(); // Chống gửi cached object cũ
                out.writeObject(request);
                out.flush();
            } catch (IOException e) {
                System.err.println("❌ Lỗi khi gửi dữ liệu: " + e.getMessage());
            }
        } else {
            System.err.println("⚠️ Chưa kết nối tới Server!");
        }
    }

    // ─── Convenience Methods ─────────────────────────────────────────────────

    public void login(String username, String password) {
        sendRequest(new Request(ActionType.LOGIN, username + "|" + password));
    }

    public void register(String username, String password, String email, String role) {
        sendRequest(new Request(ActionType.REGISTER,
                username + "|" + password + "|" + email + "|" + role));
    }

    public void register(String username, String password, String email, String role, String adminCode) {
        sendRequest(new Request(ActionType.REGISTER,
                username + "|" + password + "|" + email + "|" + role + "|" + adminCode));
    }

    public void approveAuction(String auctionId) {
        sendRequest(new Request(ActionType.APPROVE_AUCTION, auctionId));
    }

    public void rejectAuction(String auctionId) {
        sendRequest(new Request(ActionType.REJECT_AUCTION, auctionId));
    }

    public void cancelAuction(String auctionId) {
        sendRequest(new Request(ActionType.CANCEL_AUCTION, auctionId));
    }

    // ─── Listen Thread ───────────────────────────────────────────────────────

    private void startListening() {
        Thread listenThread = new Thread(() -> {
            while (socket != null && !socket.isClosed()) {
                try {
                    Response response = (Response) in.readObject();

                    // FIX: Dispatch đến TẤT CẢ listeners (không chỉ 1)
                    Platform.runLater(() -> {
                        if (!listeners.isEmpty()) {
                            // Snapshot để tránh ConcurrentModificationException
                            for (Consumer<Response> listener : listeners.values()) {
                                try {
                                    listener.accept(response);
                                } catch (Exception ex) {
                                    System.err.println("⚠️ Lỗi trong listener: " + ex.getMessage());
                                }
                            }
                        }
                    });

                } catch (Exception e) {
                    System.out.println("⚠️ Mất kết nối tới Server.");
                    break;
                }
            }
        });

        listenThread.setDaemon(true);
        listenThread.start();
    }

    public void disconnect() {
        try {
            listeners.clear();
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
            System.out.println("Đã đóng kết nối.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}