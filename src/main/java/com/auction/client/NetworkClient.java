package com.auction.client; // Hoặc com.auction.client.network tùy bạn chia

import com.auction.controller.*;
import com.auction.protocol.ActionType;
import com.auction.protocol.Request;
import com.auction.protocol.Response;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

public class NetworkClient {
    // 1. Áp dụng Singleton Pattern
    private static NetworkClient instance;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    // Callback dùng để đẩy dữ liệu về giao diện (Controller)
    private Consumer<Response> onResponseReceived;

    // Private constructor để không ai được new NetworkClient() bừa bãi
    private NetworkClient() {
    }

    public static NetworkClient getInstance() {
        if (instance == null) {
            instance = new NetworkClient();
        }
        return instance;
    }

    // Cho phép Controller đăng ký hàm nhận dữ liệu
    public void setOnResponseReceived(Consumer<Response> callback) {
        this.onResponseReceived = callback;
    }

    // 2. Hàm kết nối tới Server
    public void connect(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);

            // LƯU Ý SỐNG CÒN: BẮT BUỘC phải khởi tạo ObjectOutputStream TRƯỚC ObjectInputStream
            // Nếu làm ngược lại, cả Client và Server sẽ bị kẹt (Deadlock) mãi mãi!
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            System.out.println("✅ Đã kết nối thành công tới Server!");

            // 3. Khởi động luồng (Thread) chạy ngầm để lắng nghe Server
            startListening();

        } catch (IOException e) {
            System.err.println("❌ Lỗi kết nối tới Server: " + e.getMessage());
        }
    }

    // 4. Hàm gửi Request lên Server (Hàm này cho UI gọi)
    public void sendRequest(Request request) {
        if (socket != null && !socket.isClosed()) {
            try {
                out.writeObject(request);
                out.flush();
            } catch (IOException e) {
                System.err.println("❌ Lỗi khi gửi dữ liệu: " + e.getMessage());
            }
        } else {
            System.err.println("⚠️ Chưa kết nối tới Server!");
        }
    }

    // API móc (hook) nhanh cho giao diện
    public void login(String username, String password) {
        // Giả sử gửi String, sau này có thể tạo LoginPayload riêng
        sendRequest(new Request(ActionType.LOGIN, username + "|" + password));
    }

    // 5. Luồng chạy ngầm lắng nghe Server
    private void startListening() {
        Thread listenThread = new Thread(() -> {
            while (socket != null && !socket.isClosed()) {
                try {
                    // Đợi nhận gói tin từ Server
                    Response response = (Response) in.readObject();

                    // CẦU NỐI UI: Đẩy dữ liệu về luồng JavaFX
                    javafx.application.Platform.runLater(() -> {
                        if (onResponseReceived != null) {
                            onResponseReceived.accept(response);
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

    // Hàm ngắt kết nối
    public void disconnect() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
            System.out.println("Đã đóng kết nối.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}