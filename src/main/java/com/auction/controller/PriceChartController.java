package com.auction.controller;

import com.auction.model.BidTransaction;
import com.auction.utils.PriceChartHelper;
import javafx.fxml.FXML;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

import java.util.List;

/**
 * Controller cho PriceChart.fxml.
 * Cung cấp API setData() để các controller khác (ví dụ AuctionDetailController)
 * nạp dữ liệu lịch sử đấu giá vào biểu đồ sau khi load FXML.
 */
public class PriceChartController {

    @FXML private LineChart<String, Number> priceChart;
    @FXML private CategoryAxis xAxis;
    @FXML private NumberAxis yAxis;

    private XYChart.Series<String, Number> series;

    @FXML
    public void initialize() {
        priceChart.setAnimated(false);
        priceChart.setLegendVisible(false);
    }

    /**
     * Nạp toàn bộ lịch sử bid vào biểu đồ.
     * Phải được gọi từ FX Application Thread (hoặc bọc trong Platform.runLater).
     *
     * @param bidHistory danh sách BidTransaction đã sắp xếp theo thời gian
     */
    public void setData(List<BidTransaction> bidHistory) {
        series = PriceChartHelper.buildHistoricalChart(priceChart, bidHistory);
    }

    /**
     * Cập nhật realtime khi có bid mới.
     *
     * @param newBid giao dịch đặt giá mới nhất
     */
    public void addDataPoint(BidTransaction newBid) {
        if (series == null) return;
        PriceChartHelper.updateChartRealTime(series, newBid);
    }
}
