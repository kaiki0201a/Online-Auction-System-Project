package com.auction.utils;

import com.auction.model.BidTransaction;
import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PriceChartHelper — Utility class xây dựng và cập nhật biểu đồ giá theo thời gian thực.
 */
public class PriceChartHelper {

    /** Utility class — không cho phép khởi tạo. */
    private PriceChartHelper() {}

    /** Số điểm dữ liệu tối đa hiển thị trên biểu đồ (tránh giật). */
    private static final int MAX_CHART_POINTS = 20;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /**
     * Xây dựng biểu đồ giá từ lịch sử bid. Chỉ hiển thị tối đa {@value #MAX_CHART_POINTS} điểm gần nhất.
     *
     * @return Series đã được thêm vào chart (dùng cho updateChartRealTime sau này)
     */
    public static XYChart.Series<String, Number> buildHistoricalChart(
            LineChart<String, Number> lineChart, List<BidTransaction> bidHistory) {
        
        lineChart.getData().clear();
        lineChart.setAnimated(false);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Mức giá trúng thầu ($)");

        if (bidHistory == null || bidHistory.isEmpty()) {
            lineChart.getData().add(series);
            return series;
        }

        // Chỉ lấy MAX_CHART_POINTS điểm gần nhất để biểu đồ không bị giật
        int startIndex = Math.max(0, bidHistory.size() - MAX_CHART_POINTS);
        
        for (int i = startIndex; i < bidHistory.size(); i++) {
            BidTransaction tx = bidHistory.get(i);
            String timeString = tx.getTimestamp().format(TIME_FORMATTER);
            series.getData().add(new XYChart.Data<>(timeString, tx.getBidAmount()));
        }

        lineChart.getData().add(series);
        return series; 
    }

    /**
     * Cập nhật biểu đồ real-time khi có bid mới. Giới hạn tối đa {@value #MAX_CHART_POINTS} điểm.
     */
    public static void updateChartRealTime(XYChart.Series<String, Number> series, BidTransaction newBid) {
        String timeString = newBid.getTimestamp().format(TIME_FORMATTER);

        Platform.runLater(() -> {
            series.getData().add(new XYChart.Data<>(timeString, newBid.getBidAmount()));
            while (series.getData().size() > MAX_CHART_POINTS) {
                series.getData().remove(0);
            }
        });
    }
}