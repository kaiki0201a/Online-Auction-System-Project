 package com.auction.client; 


import com.auction.protocol.ActionType;

import com.auction.protocol.Request;

import com.auction.protocol.Response;

import javafx.application.Platform;


import java.io.IOException;

import java.io.ObjectInputStream;

import java.io.ObjectOutputStream;

import java.net.Socket;

import java.util.List;

import java.util.concurrent.CopyOnWriteArrayList;

import java.util.function.Consumer;


public class NetworkClient {

    // 1. Áp dụng Singleton Pattern an toàn đa luồng

    private static volatile NetworkClient instance;


    private Socket socket;

    private ObjectOutputStream out;

    private ObjectInputStream in;


    // 🚀 Dùng List để nhiều màn hình cùng nghe được dữ liệu (Bí mật sửa bên trong)

    private List<Consumer<Response>> listeners = new CopyOnWriteArrayList<>();


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


    // 🚀 FIX "BÌNH CŨ RƯỢU MỚI": Giữ nguyên tên hàm cũ cho anh em khỏi phải sửa code

    // Nhưng bản chất bên trong là ADD vào danh sách (List) chứ không phải GHI ĐÈ nữa!

    public void setOnResponseReceived(Consumer<Response> callback) {

        if (!listeners.contains(callback)) {

            listeners.add(callback);

        }

    }


    // (Tùy chọn) Thêm hàm này để sau này nếu màn hình nào đóng thì tự gỡ tai nghe ra

    public void removeOnResponseReceived(Consumer<Response> callback) {

        listeners.remove(callback);

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


    public void login(String username, String password) {

        sendRequest(new Request(ActionType.LOGIN, username + "|" + password));

    }


    private void startListening() {

        Thread listenThread = new Thread(() -> {

            while (socket != null && !socket.isClosed()) {

                try {

                    Response response = (Response) in.readObject();


                    // Đẩy dữ liệu về luồng JavaFX

                    Platform.runLater(() -> {

                        // 🚀 Phát loa thông báo cho TẤT CẢ các màn hình đang đăng ký lắng nghe

                        for (Consumer<Response> listener : listeners) {

                            listener.accept(response);

                        }

                    });

                } catch (Exception e) {

                    System.out.println("⚠️ Mất kết nối tới Server.");

                    break; // Thoát vòng lặp khi rớt mạng

                }

            }

        });

        listenThread.setDaemon(true);

        listenThread.start();

    }


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