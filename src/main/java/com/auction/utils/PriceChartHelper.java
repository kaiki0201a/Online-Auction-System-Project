package com.auction.utils; // 🛠️ FIX 1: Đổi thành package utils theo đúng chuẩn của team

import com.auction.model.BidTransaction;
import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class PriceChartHelper {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    /**
     * Chuyển List<BidTransaction> thành biểu đồ lúc mới vào phòng
     */
    public static XYChart.Series<String, Number> buildHistoricalChart(
            LineChart<String, Number> lineChart, List<BidTransaction> bidHistory) {
        
        lineChart.getData().clear(); // Xóa sạch dữ liệu cũ/mặc định
        lineChart.setAnimated(false); // TẮT ANIMATION 

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Mức giá trúng thầu ($)");

        //   THÊM ÁO GIÁP CHỐNG CRASH NULL POINTER THEO LỜI SẾP
        if (bidHistory == null || bidHistory.isEmpty()) {
            lineChart.getData().add(series);
            return series; // Thoát ra ngay, trả về đồ thị rỗng chờ người đặt giá
        }

        // TỐI ƯU: Chỉ lấy tối đa 20 giao dịch gần nhất để biểu đồ không bị giật
        // Nếu lịch sử có 50 điểm -> startIndex = 30 (lấy từ 30 đến 49)
        // Nếu lịch sử có 5 điểm -> startIndex = 0 (lấy tất cả)
        int startIndex = Math.max(0, bidHistory.size() - 20);
        
        for (int i = startIndex; i < bidHistory.size(); i++) {
            BidTransaction tx = bidHistory.get(i);
            String timeString = tx.getTimestamp().format(TIME_FORMATTER);
            series.getData().add(new XYChart.Data<>(timeString, tx.getBidAmount()));
        }

        lineChart.getData().add(series);
        return series; 
    }

    /**
     * Cập nhật biểu đồ ngay khi processBid thông báo giá mới
     */
    public static void updateChartRealTime(XYChart.Series<String, Number> series, BidTransaction newBid) {
        String timeString = newBid.getTimestamp().format(TIME_FORMATTER);

        Platform.runLater(() -> {
            series.getData().add(new XYChart.Data<>(timeString, newBid.getBidAmount()));

            while (series.getData().size() > 20) {
                series.getData().remove(0);
            }
        });
    }
}