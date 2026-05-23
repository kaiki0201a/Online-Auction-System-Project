package com.auction.client;

import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.Response;
import javafx.application.Platform;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

public class NetworkClient {

    private static volatile NetworkClient instance;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    // SỬA LỖI LOGIC: Dùng 1 listener duy nhất, có thể thay đổi
    // Thay vì List tích lũy listener gây memory leak
    private Consumer<Response> currentListener;

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

    /**
     * SỬA LỖI LOGIC: Thay thế listener cũ bằng listener mới.
     * Mỗi màn hình chỉ giữ 1 listener tại 1 thời điểm, tránh tích lũy vô hạn.
     */
    public void setOnResponseReceived(Consumer<Response> callback) {
        this.currentListener = callback;
    }

    public void removeOnResponseReceived() {
        this.currentListener = null;
    }

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

    // --- CÁC HÀM TIỆN ÍCH GỬI REQUEST ---

    public void login(String username, String password) {
        sendRequest(new Request(ActionType.LOGIN, username + "|" + password));
    }

    /**
     * Đăng ký tài khoản mới (Bidder / Seller)
     */
    public void register(String username, String password, String email, String role) {
        sendRequest(new Request(ActionType.REGISTER,
                username + "|" + password + "|" + email + "|" + role));
    }

    /**
     * Đăng ký tài khoản Admin kèm mã xác nhận
     */
    public void register(String username, String password, String email, String role, String adminCode) {
        sendRequest(new Request(ActionType.REGISTER,
                username + "|" + password + "|" + email + "|" + role + "|" + adminCode));
    }

    /** Admin phê duyệt sản phẩm đang chờ */
    public void approveAuction(String auctionId) {
        sendRequest(new Request(ActionType.APPROVE_AUCTION, auctionId));
    }

    /** Admin từ chối sản phẩm đang chờ */
    public void rejectAuction(String auctionId) {
        sendRequest(new Request(ActionType.REJECT_AUCTION, auctionId));
    }

    /** Admin dừng phiên đang chạy */
    public void cancelAuction(String auctionId) {
        sendRequest(new Request(ActionType.CANCEL_AUCTION, auctionId));
    }


    private void startListening() {
        Thread listenThread = new Thread(() -> {
            while (socket != null && !socket.isClosed()) {
                try {
                    Response response = (Response) in.readObject();

                    // Đẩy dữ liệu về luồng JavaFX
                    Platform.runLater(() -> {
                        if (currentListener != null) {
                            currentListener.accept(response);
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
            this.currentListener = null;
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
            System.out.println("Đã đóng kết nối.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}