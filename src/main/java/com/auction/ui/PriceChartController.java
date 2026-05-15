package com.auction.ui;

import com.auction.model.BidTransaction;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Random;

public class PriceChartController {

    // 1. Ánh xạ đúng cái ID của biểu đồ trong file FXML
    @FXML 
    private LineChart<String, Number> priceChart; 
    
    // Sợi dây chứa các điểm dữ liệu
    private XYChart.Series<String, Number> currentSeries;
    
    // Biến để giả lập giá tăng dần
    private double fakePrice = 100.0; 

    // 2. Hàm này TỰ ĐỘNG CHẠY ngay khi giao diện vừa bật lên
    @FXML
    public void initialize() {
        // Dùng Helper (mà bạn đã tạo) để setup biểu đồ lúc đầu (chưa có giá nào)
        currentSeries = PriceChartHelper.buildHistoricalChart(priceChart, new ArrayList<>());
        
        // 3. TẠO RA MỘT CON ROBOT GIẢ LẬP ĐẶT GIÁ LIÊN TỤC (Chỉ dùng để Test)
        Thread testThread = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(2000); // Cứ 2 giây lại có 1 người đặt giá
                    
                    // Random tăng giá thêm 10$ - 50$
                    fakePrice += new Random().nextInt(40) + 10; 
                    
                    // Tạo một cục dữ liệu ảo (Transaction)
                    BidTransaction fakeTx = new BidTransaction(null, null, fakePrice);
                   //fakeTx.setTimestamp(LocalDateTime.now()); // Gắn giờ hiện tại
                    
                    // Gọi Helper để vẽ lên màn hình
                    PriceChartHelper.updateChartRealTime(currentSeries, fakeTx);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        
        testThread.setDaemon(true); // Tắt App thì luồng này cũng tự chết theo
        testThread.start();
    }
}