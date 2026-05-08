package com.auction.server; // Hoặc com.auction.server.network

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerApp {
    private static final int PORT = 8888; // Bạn có thể chọn cổng nào cũng được (VD: 8080, 9999)

    public static void main(String[] args) {
        System.out.println("🚀 Đang khởi động Server Đấu Giá...");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✅ Server đang lắng nghe tại cổng " + PORT);

            // Vòng lặp vô tận: Luôn mở cửa đón khách
            while (true) {
                // Code sẽ đứng im ở đây chờ đến khi có 1 Client gọi NetworkClient.connect()
                Socket clientSocket = serverSocket.accept();
                System.out.println("👋 Có khách mới kết nối: " + clientSocket.getInetAddress());

                // Giao khách này cho một "Bạn phục vụ" (ClientHandler) chạy trên 1 Luồng (Thread) riêng biệt
                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.err.println("❌ Lỗi Server: " + e.getMessage());
        }
    }
}