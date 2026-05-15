package com.auction.ui;

import com.auction.model.BidTransaction;
import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class PriceChartHelper {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * : Chuyển List<BidTransaction> thành biểu đồ lúc mới vào phòng
     * @param lineChart Biểu đồ được kéo thả từ JavaFX FXML
     * @param bidHistory Danh sách lịch sử đấu giá từ Backend
     * @return Sợi dây (Series) để Controller giữ lại và tiếp tục update realtime
     */
    public static XYChart.Series<String, Number> buildHistoricalChart(
            LineChart<String, Number> lineChart, List<BidTransaction> bidHistory) {
        
        lineChart.getData().clear(); // Xóa sạch dữ liệu cũ/mặc định
        lineChart.setAnimated(false); // TẮT ANIMATION (Bắt buộc để vẽ realtime không bị giật/lỗi đồ họa)

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Mức giá trúng thầu ($)");

        // Quét toàn bộ lịch sử và biến thành các điểm trên biểu đồ
        for (BidTransaction tx : bidHistory) {
            String timeString = tx.getTimestamp().format(TIME_FORMATTER);
            series.getData().add(new XYChart.Data<>(timeString, tx.getBidAmount()));
        }

        lineChart.getData().add(series);
        return series; // Trả về sợi dây này cho Controller quản lý
    }

    /**
     * N Cập nhật biểu đồ ngay khi processBid thông báo giá mới
     * @param series Sợi dây đang hiển thị trên biểu đồ
     * @param newBid Giao dịch đấu giá mới nhất vừa thành công
     */
    public static void updateChartRealTime(XYChart.Series<String, Number> series, BidTransaction newBid) {
        String timeString = newBid.getTimestamp().format(TIME_FORMATTER);

        // Q Đang ở luồng Backend (Observer), phải đẩy việc vẽ giao diện về luồng JavaFX
        Platform.runLater(() -> {
            // Thêm 1 điểm giá mới vào sợi dây
            series.getData().add(new XYChart.Data<>(timeString, newBid.getBidAmount()));

            // Giới hạn hiển thị 20 mốc giá gần nhất để trục X không bị rác và kẹt cứng
            if (series.getData().size() > 20) {
                series.getData().remove(0);
            }
        });
    }
}