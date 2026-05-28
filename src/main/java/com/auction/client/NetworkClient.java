package com.auction.client;

import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import com.auction.protocol.StatusType;
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
 * FIXES:
 * 1. Dùng ConcurrentHashMap thay vì single listener — nhiều màn hình đăng ký key riêng,
 *    không còn bị ghi đè lẫn nhau.
 * 2. Auto-reconnect: khi mất kết nối, thử lại tối đa MAX_RETRY lần với delay RETRY_DELAY_MS.
 * 3. Broadcast "CONNECTION_LOST" / "CONNECTION_RESTORED" / "CONNECTION_FAILED"
 *    để UI hiển thị thông báo cho người dùng (xử lý lỗi kết nối).
 */
public class NetworkClient {

    private static volatile NetworkClient instance;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    // Dùng Map thay vì 1 listener — mỗi màn hình giữ key riêng
    private final Map<String, Consumer<Response>> listeners = new ConcurrentHashMap<>();

    // Key mặc định cho backward compatibility
    private static final String DEFAULT_KEY = "default";

    // ─── Auto-reconnect config ────────────────────────────────────────────────
    /** Số lần tối đa thử kết nối lại khi mất mạng */
    private static final int MAX_RETRY      = 3;
    /** Thời gian chờ giữa mỗi lần thử (ms) */
    private static final int RETRY_DELAY_MS = 2000;

    /** Lưu lại địa chỉ server để có thể reconnect */
    private String  savedAddress;
    private int     savedPort;

    /** Cờ ngăn nhiều luồng reconnect chạy song song */
    private volatile boolean isReconnecting = false;

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

    /** Gỡ listener theo key khi màn hình đóng/navigate đi. */
    public void removeEventListener(String key) {
        listeners.remove(key);
    }

    /** Backward compatibility: setOnResponseReceived → ghi vào key "default". */
    public void setOnResponseReceived(Consumer<Response> callback) {
        listeners.put(DEFAULT_KEY, callback);
    }

    /** Backward compatibility: removeOnResponseReceived → xóa key "default". */
    public void removeOnResponseReceived() {
        listeners.remove(DEFAULT_KEY);
    }

    /** Xóa toàn bộ listeners (khi disconnect hoàn toàn). */
    public void removeAllListeners() {
        listeners.clear();
    }

    // ─── Network API ─────────────────────────────────────────────────────────

    /**
     * Kết nối tới server. Lưu lại địa chỉ để auto-reconnect sau này.
     */
    public void connect(String serverAddress, int port) {
        this.savedAddress = serverAddress;
        this.savedPort    = port;
        doConnect(serverAddress, port);
    }

    /** Thực hiện kết nối thực sự — dùng cho cả lần đầu lẫn khi reconnect. */
    private void doConnect(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);

            // BẮT BUỘC khởi tạo ObjectOutputStream TRƯỚC để chống Deadlock
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            isReconnecting = false;
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

                    // Dispatch đến TẤT CẢ listeners (không chỉ 1)
                    Platform.runLater(() -> {
                        if (!listeners.isEmpty()) {
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
                    System.out.println("⚠️ Mất kết nối tới Server: " + e.getMessage());
                    break;
                }
            }

            // Vòng lặp thoát ra → thử auto-reconnect
            attemptReconnect();
        });

        listenThread.setDaemon(true);
        listenThread.start();
    }

    /**
     * Tự động thử kết nối lại tối đa MAX_RETRY lần khi mất mạng.
     * Broadcast sự kiện kết nối để tất cả màn hình có thể phản ứng (hiện dialog, disable nút...).
     */
    private void attemptReconnect() {
        // Tránh nhiều luồng reconnect chạy cùng lúc; savedAddress=null khi chủ động disconnect
        if (isReconnecting || savedAddress == null) return;
        isReconnecting = true;

        // Thông báo UI mất kết nối ngay lập tức
        broadcastConnectionEvent("CONNECTION_LOST",
                "⚠️ Mất kết nối tới server. Đang thử kết nối lại...");

        Thread retryThread = new Thread(() -> {
            for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
                System.out.printf("🔄 [RECONNECT] Lần thử %d/%d — chờ %dms...%n",
                        attempt, MAX_RETRY, RETRY_DELAY_MS);
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }

                try {
                    doConnect(savedAddress, savedPort);
                    if (isConnected()) {
                        broadcastConnectionEvent("CONNECTION_RESTORED",
                                "✅ Đã kết nối lại với server thành công!");
                        System.out.println("✅ [RECONNECT] Kết nối lại thành công!");
                        return;
                    }
                } catch (Exception e) {
                    System.err.println("❌ [RECONNECT] Lần " + attempt + " thất bại: " + e.getMessage());
                }
            }

            // Hết số lần thử → báo thất bại
            isReconnecting = false;
            broadcastConnectionEvent("CONNECTION_FAILED",
                    "❌ Không thể kết nối lại sau " + MAX_RETRY
                            + " lần thử. Vui lòng khởi động lại ứng dụng.");
            System.err.println("❌ [RECONNECT] Đã thử " + MAX_RETRY + " lần, không thành công.");
        });

        retryThread.setDaemon(true);
        retryThread.start();
    }

    /** Gửi sự kiện kết nối đến tất cả listener để UI hiển thị thông báo. */
    private void broadcastConnectionEvent(String eventType, String message) {
        Response event = new Response(StatusType.ERROR, eventType, message);
        Platform.runLater(() -> {
            for (Consumer<Response> listener : listeners.values()) {
                try {
                    listener.accept(event);
                } catch (Exception ex) {
                    // Bỏ qua lỗi trong listener khi broadcast connection event
                }
            }
        });
    }

    public void disconnect() {
        try {
            savedAddress = null; // Ngăn auto-reconnect khi chủ động disconnect
            listeners.clear();
            if (in     != null) in.close();
            if (out    != null) out.close();
            if (socket != null) socket.close();
            System.out.println("Đã đóng kết nối.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}