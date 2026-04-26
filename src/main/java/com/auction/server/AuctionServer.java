package com.auction.server;

import com.auction.network.UpdateMessage;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AuctionServer {
    private static final int PORT = 8080;
    private static List<ObjectOutputStream> clientWriters = Collections.synchronizedList(new ArrayList<>());
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✅ Tổng đài Server đang mở tại cổng " + PORT + "...");
            System.out.println("Đang chờ các Bidder kết nối...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("🎉 Có một Bidder vừa kết nối từ IP: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void broadcast(UpdateMessage message) {
        synchronized (clientWriters) {
            for (ObjectOutputStream out : clientWriters) {
                try {
                    out.writeObject(message);
                    out.flush();
                } catch (Exception e) {
                    System.out.println("Lỗi khi gửi tin cho 1 client.");
                }
            }
        }
    }
    public static void addClient(ObjectOutputStream out) {
        clientWriters.add(out);
    }
}
