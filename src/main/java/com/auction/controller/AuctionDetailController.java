package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.*;
import com.auction.notification.ConfirmDialog;
import com.auction.notification.FormValidator;
import com.auction.notification.NotificationService;
import com.auction.notification.NotificationType;
import com.auction.protocol.ActionType;
import com.auction.protocol.AutoBidPayload;
import com.auction.protocol.BidPayload;
import com.auction.protocol.Request;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import com.auction.utils.CurrencyFormatter;
import com.auction.utils.PriceChartHelper;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.animation.ParallelTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * AuctionDetailController — FIX HOÀN CHỈNH:
 *
 * 1. FIX LISTENER: đổi setOnResponseReceived → addEventListener("auctionDetail", ...)
 *    → không còn bị ghi đè bởi màn hình khác. Giải quyết bug #1 (xem phiên) và #3 (đặt giá).
 *
 * 2. THÊM THÔNG TIN THỜI GIAN: hiển thị startTime + endTime trong UI.
 *
 * 3. THÊM AUTOBID PANEL: TextField maxBid + increment, nút BẬT/TẮT AutoBid.
 *    Giao tiếp qua ActionType.SET_AUTOBID với AutoBidPayload.
 *    Giải quyết bug #5.
 */
public class AuctionDetailController {

    // Cột trái: ảnh + mô tả
    @FXML private StackPane productImagePane;
    @FXML private ImageView imgProduct;
    @FXML private Label lblCategoryIcon;
    @FXML private Label lblCategoryLabel;
    @FXML private Label lblStatusBadge;
    @FXML private Label lblProductName;
    @FXML private Label lblItemCategory;
    @FXML private Label lblStartingPrice;
    @FXML private Label lblSellerName;
    @FXML private Label lblDescription;
    @FXML private Label lblMessage;

    // Thời gian (THÊM MỚI)
    @FXML private Label lblStartTime;
    @FXML private Label lblEndTime;

    // Cột phải: đặt giá thủ công
    @FXML private Label lblCurrentPrice;
    @FXML private Label lblTimeLeft;
    @FXML private Label lblBidCount;
    @FXML private TextField txtBidAmount;
    @FXML private Button btnBid;
    @FXML private Button btnQuick10;
    @FXML private Button btnQuick50;
    @FXML private Button btnQuick100;

    // AUTOBID PANEL (THÊM MỚI — cần thêm vào FXML)
    @FXML private TitledPane autoBidPane;
    @FXML private TextField txtAutoBidMax;
    @FXML private TextField txtAutoBidIncrement;
    @FXML private Button btnAutoBidToggle;
    @FXML private Label lblAutoBidStatus;

    // Lịch sử bid
    @FXML private VBox bidHistoryContainer;

    // LineChart biểu đồ tiến trình giá (FIX #3)
    @FXML private LineChart<String, Number> priceLineChart;
    @FXML private CategoryAxis chartXAxis;
    @FXML private NumberAxis chartYAxis;
    @FXML private Label lblChartInfo;
    private XYChart.Series<String, Number> priceSeries;

    // Notification toast — góc dưới phải
    @FXML private HBox   notificationBanner;
    @FXML private Region notifAccentBar;
    @FXML private Label  lblNotifIcon;
    @FXML private Label  lblNotifTitle;
    @FXML private Label  lblNotifSub;

    private PauseTransition notifPause;

    private Auction currentAuction;
    private User sessionUser;
    private boolean canBid = false;
    private boolean autoBidActive = false;
    private Timeline countdownTimeline;

    // Bid amount realtime validator
    private FormValidator bidValidator;
    // Ngưỡng giá lớn — cần confirm khi bid > threshold này
    private static final double LARGE_BID_THRESHOLD = 5_000_000; // 5 triệu đồng
    // Cảnh báo sắp hết giờ — toast warning khi còn dưới số giây này
    private static final long WARNING_SECONDS = 120;
    private boolean endingWarningSent = false;

    // FIX: Key riêng cho màn hình này — không bị ghi đè
    private static final String LISTENER_KEY = "auctionDetail";
    private static final DateTimeFormatter DTF     = DateTimeFormatter.ofPattern("HH:mm:ss dd/MM");
    private static final DateTimeFormatter DTF_FULL = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    @FXML
    public void initialize() {
        // Chỉ cho nhập số trong ô đặt giá
        if (txtBidAmount != null) {
            txtBidAmount.setTextFormatter(new TextFormatter<>(change ->
                    change.getControlNewText().matches("\\d*(\\.\\d*)?") ? change : null));
        }
        // AutoBid — chỉ cho nhập số
        if (txtAutoBidMax != null) {
            txtAutoBidMax.setTextFormatter(new TextFormatter<>(change ->
                    change.getControlNewText().matches("\\d*(\\.\\d*)?") ? change : null));
        }
        if (txtAutoBidIncrement != null) {
            txtAutoBidIncrement.setTextFormatter(new TextFormatter<>(change ->
                    change.getControlNewText().matches("\\d*(\\.\\d*)?") ? change : null));
        }
        // Bid validator sẽ được khởi tạo sau khi setAuctionData() được gọi
    }

    /**
     * Điểm vào chính — được gọi từ các màn hình list khi người dùng chọn phiên.
     */
    public void setAuctionData(Auction auction) {
        this.currentAuction = auction;
        this.sessionUser = AppContext.getCurrentUser();

        // Bidder mới được đặt giá; phiên đã kết thúc thì không ai đặt được
        canBid = sessionUser instanceof Bidder
                && auction.getStatus() != AuctionStatus.FINISHED
                && auction.getStatus() != AuctionStatus.PAID
                && auction.getStatus() != AuctionStatus.CANCELED;

        setupBidControls();
        setupAutoBidPanel();
        loadProductImage();
        updateUI();
        startCountdown();
        renderBidHistory();
        initPriceChart();

        // Khởi tạo bid validator sau khi có auction data
        if (canBid && txtBidAmount != null) {
            bidValidator = FormValidator.of(txtBidAmount)
                    .bidAmount(
                            () -> currentAuction.getCurrentHighestBid(),
                            () -> sessionUser instanceof Bidder b ? b.getBalance() : 0.0
                    )
                    .attach();
        }

        // FIX: Đăng ký listener với key riêng — không bị ghi đè
        registerNetworkListener();
    }

    // ─── Cài đặt controls đặt giá ────────────────────────────────────────────

    /**
     * FIX #3: Khởi tạo biểu đồ tiến trình giá từ lịch sử bid hiện tại.
     */
    private void initPriceChart() {
        if (priceLineChart == null) return;
        priceLineChart.setLegendVisible(false);
        priceLineChart.setAnimated(false);
        priceSeries = PriceChartHelper.buildHistoricalChart(
                priceLineChart, currentAuction.getBidHistory());
        updateChartLabel();
    }

    /** Cập nhật label thống kê số lượt đặt giá trên chart */
    private void updateChartLabel() {
        if (lblChartInfo == null) return;
        int count = currentAuction.getBidHistory().size();
        lblChartInfo.setText(count == 0
                ? "Chưa có lượt đặt giá nào"
                : count + " lượt đặt giá · Cập nhật realtime");
    }

    private void setupBidControls() {
        if (btnBid != null) {
            btnBid.setDisable(!canBid);
            if (!canBid) {
                if (sessionUser instanceof Seller) {
                    btnBid.setText("👁 Chỉ Xem");
                } else if (sessionUser instanceof Admin) {
                    btnBid.setText("👑 Admin View");
                } else {
                    btnBid.setText("ĐÃ KẾT THÚC");
                }
                btnBid.setStyle("-fx-background-color: #2A2A2A; -fx-text-fill: #666; " +
                        "-fx-border-color: #333; -fx-border-radius: 4; -fx-background-radius: 4;");
            }
        }
        if (txtBidAmount  != null) txtBidAmount.setDisable(!canBid);
        if (btnQuick10    != null) btnQuick10.setDisable(!canBid);
        if (btnQuick50    != null) btnQuick50.setDisable(!canBid);
        if (btnQuick100   != null) btnQuick100.setDisable(!canBid);
    }

    // ─── AUTOBID PANEL (THÊM MỚI) ────────────────────────────────────────────

    /**
     * Setup trạng thái ban đầu cho AutoBid panel.
     * Chỉ Bidder mới thấy và dùng được.
     */
    private void setupAutoBidPanel() {
        if (autoBidPane == null) return;

        // Ẩn hoàn toàn với Seller / Admin / phiên đã kết thúc
        boolean showPanel = canBid;
        autoBidPane.setVisible(showPanel);
        autoBidPane.setManaged(showPanel);

        if (!showPanel) return;

        // Giao diện ban đầu — AutoBid đang TẮT
        updateAutoBidButtonStyle(false);
        if (lblAutoBidStatus != null) {
            lblAutoBidStatus.setText("AutoBid đang TẮT. Nhập mức giá tối đa và bước nhảy rồi bật.");
            lblAutoBidStatus.setStyle("-fx-text-fill: #666; -fx-font-size: 11px;");
        }
    }

    /**
     * Xử lý khi nhấn nút BẬT/TẮT AutoBid.
     */
    @FXML
    public void onAutoBidToggle() {
        if (!canBid) {
            setMessage("⛔ Chỉ Bidder mới có thể dùng AutoBid!", "#e74c3c");
            return;
        }

        if (!autoBidActive) {
            // Đang TẮT → BẬT: validate và gửi lên server
            String maxStr = txtAutoBidMax != null ? txtAutoBidMax.getText().trim() : "";
            String incStr = txtAutoBidIncrement != null ? txtAutoBidIncrement.getText().trim() : "";

            if (maxStr.isEmpty() || incStr.isEmpty()) {
                setAutoBidStatus("⚠️ Vui lòng nhập đầy đủ Giá tối đa và Bước nhảy!", "#f39c12");
                return;
            }

            double maxBid, increment;
            try {
                maxBid    = Double.parseDouble(maxStr);
                increment = Double.parseDouble(incStr);
            } catch (NumberFormatException e) {
                setAutoBidStatus("❌ Số tiền không hợp lệ!", "#e74c3c");
                return;
            }

            if (maxBid <= currentAuction.getCurrentHighestBid()) {
                setAutoBidStatus("⚠️ Giá tối đa phải lớn hơn giá hiện tại: "
                        + CurrencyFormatter.format(currentAuction.getCurrentHighestBid()), "#f39c12");
                return;
            }
            if (increment <= 0) {
                setAutoBidStatus("⚠️ Bước nhảy phải lớn hơn 0!", "#f39c12");
                return;
            }
            Bidder bidder = (Bidder) sessionUser;
            if (bidder.getBalance() < maxBid) {
                setAutoBidStatus("❌ Số dư không đủ! Bạn có: "
                        + CurrencyFormatter.format(bidder.getBalance()), "#e74c3c");
                return;
            }

            // Gửi lên server — enable = true
            AutoBidPayload payload = new AutoBidPayload(
                    currentAuction.getAuctionId(), bidder.getUserName(), maxBid, increment, true);
            NetworkClient.getInstance().sendRequest(new Request(ActionType.SET_AUTOBID, payload));
            setAutoBidStatus("⏳ Đang bật AutoBid...", "#A0A0A0");

            // Khoá input khi đang hoạt động
            if (txtAutoBidMax       != null) txtAutoBidMax.setDisable(true);
            if (txtAutoBidIncrement != null) txtAutoBidIncrement.setDisable(true);

        } else {
            // Đang BẬT → TẮT
            Bidder bidder = (Bidder) sessionUser;
            AutoBidPayload payload = new AutoBidPayload(
                    currentAuction.getAuctionId(), bidder.getUserName(), 0, 0, false);
            NetworkClient.getInstance().sendRequest(new Request(ActionType.SET_AUTOBID, payload));
            setAutoBidStatus("⏳ Đang tắt AutoBid...", "#A0A0A0");
        }
    }

    private void updateAutoBidButtonStyle(boolean active) {
        autoBidActive = active;
        if (btnAutoBidToggle == null) return;
        if (active) {
            btnAutoBidToggle.setText("🤖 TẮT AutoBid");
            btnAutoBidToggle.setStyle(
                    "-fx-background-color: #c0392b; -fx-text-fill: white; " +
                            "-fx-font-weight: bold; -fx-background-radius: 5; " +
                            "-fx-cursor: hand; -fx-padding: 8 18;");
        } else {
            btnAutoBidToggle.setText("🤖 BẬT AutoBid");
            btnAutoBidToggle.setStyle(
                    "-fx-background-color: #8e44ad; -fx-text-fill: white; " +
                            "-fx-font-weight: bold; -fx-background-radius: 5; " +
                            "-fx-cursor: hand; -fx-padding: 8 18;");
        }
    }

    private void setAutoBidStatus(String text, String color) {
        if (lblAutoBidStatus != null) {
            lblAutoBidStatus.setText(text);
            lblAutoBidStatus.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 11px; -fx-font-weight: bold;");
        }
    }

    // ─── Ảnh sản phẩm ────────────────────────────────────────────────────────

    private void loadProductImage() {
        if (imgProduct == null) return;
        String imgPath = currentAuction.getItem().getImagePath();
        if (imgPath != null && !imgPath.isEmpty()) {
            try {
                Image img = new Image("file:" + imgPath, 700, 420, true, true);
                imgProduct.setImage(img);
                imgProduct.setVisible(true);
                if (lblCategoryIcon  != null) lblCategoryIcon.setVisible(false);
                if (lblCategoryLabel != null) lblCategoryLabel.setVisible(false);
            } catch (Exception ignored) {
                showCategoryPlaceholder();
            }
        } else {
            showCategoryPlaceholder();
        }
    }

    private void showCategoryPlaceholder() {
        String cat  = currentAuction.getItem().getClass().getSimpleName();
        String icon = switch (cat) {
            case "Art"         -> "🎨";
            case "Electronics" -> "💻";
            case "Vehicle"     -> "🚗";
            default            -> "📦";
        };
        if (lblCategoryIcon  != null) { lblCategoryIcon.setText(icon);  lblCategoryIcon.setVisible(true); }
        if (lblCategoryLabel != null) { lblCategoryLabel.setText(cat);  lblCategoryLabel.setVisible(true); }
        if (imgProduct       != null) imgProduct.setVisible(false);
    }

    // ─── Cập nhật UI ──────────────────────────────────────────────────────────

    private void updateUI() {
        Item item = currentAuction.getItem();

        if (lblProductName   != null) lblProductName.setText(item.getNameItem());
        if (lblCurrentPrice  != null) lblCurrentPrice.setText(CurrencyFormatter.format(currentAuction.getCurrentHighestBid()));
        if (lblSellerName    != null) lblSellerName.setText(currentAuction.getSeller().getUserName());
        if (lblItemCategory  != null) lblItemCategory.setText(item.getClass().getSimpleName());
        if (lblStartingPrice != null) lblStartingPrice.setText(CurrencyFormatter.format(item.getStartingPrice()));
        if (lblDescription   != null) {
            String desc = item.getDescriptionItem();
            lblDescription.setText(desc != null && !desc.isEmpty() ? desc : "Chưa có mô tả chi tiết.");
        }
        if (lblBidCount != null) lblBidCount.setText(String.valueOf(currentAuction.getBidHistory().size()));

        // FIX #2: Hiển thị thời gian bắt đầu và kết thúc
        if (lblStartTime != null) {
            String startTxt = currentAuction.getStartTime() != null
                    ? currentAuction.getStartTime().format(DTF_FULL)
                    : "Ngay lập tức";
            lblStartTime.setText(startTxt);
        }
        if (lblEndTime != null && currentAuction.getEndTime() != null) {
            lblEndTime.setText(currentAuction.getEndTime().format(DTF_FULL));
        }

        // Badge trạng thái
        if (lblStatusBadge != null) {
            AuctionStatus st = currentAuction.getStatus();
            switch (st) {
                case FINISHED, PAID -> {
                    lblStatusBadge.setText("● ĐÃ KẾT THÚC");
                    lblStatusBadge.setStyle("-fx-background-color: rgba(39,174,96,0.3); " +
                            "-fx-text-fill: #27ae60; -fx-padding: 4 12; -fx-background-radius: 4; " +
                            "-fx-font-size: 11px; -fx-font-weight: bold;");
                }
                case APPROVED -> {
                    lblStatusBadge.setText("● SẮP DIỄN RA");
                    lblStatusBadge.setStyle("-fx-background-color: rgba(52,152,219,0.3); " +
                            "-fx-text-fill: #3498db; -fx-padding: 4 12; -fx-background-radius: 4; " +
                            "-fx-font-size: 11px; -fx-font-weight: bold;");
                }
                case CANCELED -> {
                    lblStatusBadge.setText("● ĐÃ HỦY");
                    lblStatusBadge.setStyle("-fx-background-color: rgba(127,140,141,0.3); " +
                            "-fx-text-fill: #7f8c8d; -fx-padding: 4 12; -fx-background-radius: 4; " +
                            "-fx-font-size: 11px; -fx-font-weight: bold;");
                }
                default -> {
                    lblStatusBadge.setText("● ĐANG DIỄN RA");
                    lblStatusBadge.setStyle("-fx-background-color: rgba(245,197,24,0.25); " +
                            "-fx-text-fill: #F5C518; -fx-padding: 4 12; -fx-background-radius: 4; " +
                            "-fx-font-size: 11px; -fx-font-weight: bold;");
                }
            }
        }
    }

    // ─── Countdown ────────────────────────────────────────────────────────────

    private void startCountdown() {
        if (countdownTimeline != null) countdownTimeline.stop();
        endingWarningSent = false;
        countdownTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (lblTimeLeft == null || currentAuction.getEndTime() == null) return;
            long secs = ChronoUnit.SECONDS.between(LocalDateTime.now(), currentAuction.getEndTime());
            if (secs <= 0) {
                lblTimeLeft.setText("ĐÃ KẾT THÚC");
                lblTimeLeft.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 18px;");
                countdownTimeline.stop();
            } else {
                long h = secs / 3600, m = (secs % 3600) / 60, s = secs % 60;
                lblTimeLeft.setText(String.format("%02d:%02d:%02d", h, m, s));
                // Cảnh báo đổi màu khi còn < 60 giây
                lblTimeLeft.setStyle(secs < 60
                        ? "-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 22px; -fx-font-family: monospace;"
                        : "-fx-text-fill: #F5C518; -fx-font-weight: bold; -fx-font-size: 22px; -fx-font-family: monospace;");
                // Toast cảnh báo khi còn dưới ngưỡng WARNING_SECONDS
                if (canBid && !endingWarningSent && secs <= WARNING_SECONDS && secs > 0) {
                    endingWarningSent = true;
                    NotificationService.get().toast(
                            "⏳ Chỉ còn " + secs + " giây! Đặt giá ngay nếu muốn thắng!",
                            NotificationType.WARNING,
                            lblTimeLeft,
                            5
                    );
                }
            }
        }));
        countdownTimeline.setCycleCount(Timeline.INDEFINITE);
        countdownTimeline.play();
    }

    // ─── Lịch sử bid ─────────────────────────────────────────────────────────

    private void renderBidHistory() {
        if (bidHistoryContainer == null) return;
        bidHistoryContainer.getChildren().clear();

        List<BidTransaction> history = currentAuction.getBidHistory();
        if (history.isEmpty()) {
            Label empty = new Label("Chưa có ai đặt giá. Hãy là người đầu tiên!");
            empty.setStyle("-fx-text-fill: #555; -fx-font-style: italic; -fx-font-size: 12px;");
            bidHistoryContainer.getChildren().add(empty);
            return;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            bidHistoryContainer.getChildren().add(buildHistoryRow(history.get(i), i == history.size() - 1));
        }
    }

    private HBox buildHistoryRow(BidTransaction tx, boolean isTop) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        row.setStyle(isTop
                ? "-fx-background-color: rgba(245,197,24,0.1); -fx-border-color: #F5C518; " +
                "-fx-border-radius: 6; -fx-background-radius: 6; -fx-border-width: 1;"
                : "-fx-background-color: #1A1A1A; -fx-border-color: #2A2A2A; " +
                "-fx-border-radius: 6; -fx-background-radius: 6; -fx-border-width: 1;");

        Label rankIcon = new Label(isTop ? "🏆" : "•");
        rankIcon.setStyle("-fx-font-size: 14px; -fx-padding: 0 8 0 0; -fx-text-fill: "
                + (isTop ? "#F5C518" : "#555") + ";");

        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label bidder = new Label(tx.getBidder().getUserName());
        bidder.setStyle("-fx-text-fill: " + (isTop ? "#FFFFFF" : "#A0A0A0")
                + "; -fx-font-size: 13px; -fx-font-weight: " + (isTop ? "bold" : "normal") + ";");
        Label time = new Label(tx.getTimestamp().format(DTF));
        time.setStyle("-fx-text-fill: #444; -fx-font-size: 10px;");
        info.getChildren().addAll(bidder, time);

        Label amount = new Label(CurrencyFormatter.format(tx.getBidAmount()));
        amount.setStyle("-fx-text-fill: " + (isTop ? "#F5C518" : "#666")
                + "; -fx-font-weight: bold; -fx-font-size: " + (isTop ? "15" : "13") + "px;");

        row.getChildren().addAll(rankIcon, info, amount);
        return row;
    }

    // ─── Đặt giá thủ công ────────────────────────────────────────────────────

    @FXML public void onQuickBid10()  { addQuickAmount(10_000); }
    @FXML public void onQuickBid50()  { addQuickAmount(50_000); }
    @FXML public void onQuickBid100() { addQuickAmount(100_000); }

    private void addQuickAmount(double increment) {
        if (txtBidAmount == null) return;
        double base = currentAuction.getCurrentHighestBid();
        try {
            if (!txtBidAmount.getText().isEmpty())
                base = Double.parseDouble(txtBidAmount.getText());
        } catch (NumberFormatException ignored) {}
        txtBidAmount.setText(String.valueOf((long)(base + increment)));
    }

    @FXML
    public void onBidButtonClick() {
        if (!canBid) {
            setMessage("⛔ Chỉ người mua (Bidder) mới có thể đặt giá!", "#e74c3c");
            NotificationService.get().warning("⛔ Chỉ Bidder mới có thể đặt giá!", lblMessage);
            return;
        }
        if (currentAuction.getStatus() == AuctionStatus.FINISHED
                || currentAuction.getStatus() == AuctionStatus.PAID) {
            setMessage("⛔ Phiên đấu giá đã kết thúc!", "#e74c3c");
            NotificationService.get().error("⛔ Phiên đấu giá đã kết thúc!", lblMessage);
            return;
        }
        if (currentAuction.getStatus() == AuctionStatus.CANCELED) {
            setMessage("🚫 Phiên đấu giá đã bị hủy!", "#7f8c8d");
            return;
        }
        if (currentAuction.getStatus() == AuctionStatus.APPROVED
                || currentAuction.getStatus() == AuctionStatus.PENDING_APPROVAL) {
            setMessage("⏳ Phiên chưa bắt đầu! Hãy đợi đến giờ mở.", "#f39c12");
            NotificationService.get().info("⏳ Phiên chưa bắt đầu!", lblMessage);
            return;
        }

        String text = txtBidAmount != null ? txtBidAmount.getText().trim() : "";
        if (text.isEmpty()) {
            setMessage("⚠️ Vui lòng nhập số tiền đặt giá!", "#f39c12");
            NotificationService.get().warning("Vui lòng nhập số tiền trước khi đặt giá!", lblMessage);
            return;
        }

        try {
            double amount = Double.parseDouble(text);

            // Validate giá bid
            if (amount <= currentAuction.getCurrentHighestBid()) {
                String errMsg = "⚠️ Giá phải cao hơn giá hiện tại: "
                        + CurrencyFormatter.format(currentAuction.getCurrentHighestBid());
                setMessage(errMsg, "#f39c12");
                NotificationService.get().warning(errMsg, lblMessage);
                if (bidValidator != null) bidValidator.validate();
                return;
            }

            Bidder bidder = (Bidder) sessionUser;
            if (amount > bidder.getBalance()) {
                String errMsg = "❌ Số dư không đủ! Ví có: "
                        + CurrencyFormatter.format(bidder.getBalance())
                        + " — Cần thêm: "
                        + CurrencyFormatter.format(amount - bidder.getBalance());
                setMessage(errMsg, "#e74c3c");
                NotificationService.get().error(errMsg, lblMessage);
                return;
            }

            // ConfirmDialog khi bid giá trị lớn
            if (amount >= LARGE_BID_THRESHOLD) {
                Window owner = lblMessage != null && lblMessage.getScene() != null
                        ? lblMessage.getScene().getWindow() : null;
                java.util.Optional<Boolean> confirmed = ConfirmDialog.placeLargeBid(owner, amount);
                if (confirmed.isEmpty() || !confirmed.get()) return; // User hủy
            }

            // Gửi request
            BidPayload payload = new BidPayload(
                    currentAuction.getAuctionId(), bidder.getUserName(), amount);
            NetworkClient.getInstance().sendRequest(new Request(ActionType.PLACE_BID, payload));
            setMessage("🚀 Đang gửi giá thầu " + CurrencyFormatter.format(amount) + "...", "#A0A0A0");
            if (btnBid != null) btnBid.setDisable(true);

        } catch (NumberFormatException ex) {
            setMessage("❌ Số tiền không hợp lệ!", "#e74c3c");
            NotificationService.get().error("❌ Số tiền không hợp lệ!", lblMessage);
        }
    }

    // ─── Network listener (FIX CHÍNH) ────────────────────────────────────────

    /**
     * FIX #1 + #3: Dùng addEventListener("auctionDetail", ...) thay vì setOnResponseReceived.
     * Đảm bảo màn hình này nhận được response ngay cả khi đang hiển thị cùng lúc
     * với các listener khác (bidder dashboard, seller dashboard...).
     */
    private void registerNetworkListener() {
        NetworkClient.getInstance().addEventListener(LISTENER_KEY, response -> {
            Platform.runLater(() -> {
                String msg = response.getMessage();

                // FIX: Xử lý FORCE_LOGOUT — user bị admin khóa khi đang xem phiên
                if (msg != null && msg.startsWith("FORCE_LOGOUT|")) {
                    String logoutTarget = msg.split("\\|")[1];
                    if (sessionUser != null && logoutTarget.equals(sessionUser.getUserName())) {
                        if (countdownTimeline != null) countdownTimeline.stop();
                        NetworkClient.getInstance().removeEventListener(LISTENER_KEY);
                        AppContext.logout();
                        try {
                            Parent root = FXMLLoader.load(getClass().getResource("/com/auction/view/Login.fxml"));
                            Stage stage = (Stage) (lblProductName != null && lblProductName.getScene() != null
                                    ? lblProductName.getScene().getWindow() : null);
                            if (stage != null) {
                                stage.setScene(new Scene(root, 900, 600));
                                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                                        javafx.scene.control.Alert.AlertType.WARNING);
                                alert.setTitle("⚠️ Tài khoản bị khóa");
                                alert.setHeaderText(null);
                                alert.setContentText("🚫 Tài khoản của bạn đã bị Admin khóa.\nBạn đã được đăng xuất tự động.");
                                alert.show();
                            }
                        } catch (IOException ex) { ex.printStackTrace(); }
                    }
                    return;
                }

                // Cập nhật khi có bid mới (broadcast UPDATE_AUCTION kèm List<Auction>)
                if ("UPDATE_AUCTION".equals(msg) && response.getData() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Auction> list = (List<Auction>) response.getData();
                    list.stream()
                            .filter(a -> a.getAuctionId().equals(currentAuction.getAuctionId()))
                            .findFirst()
                            .ifPresent(updated -> {
                                boolean wasExtended = updated.getEndTime().isAfter(currentAuction.getEndTime());
                                currentAuction = updated;
                                updateUI();
                                renderBidHistory();
                                startCountdown();
                                // FIX #3: Cập nhật biểu đồ realtime khi có bid mới
                                if (priceLineChart != null && priceSeries != null) {
                                    priceSeries = PriceChartHelper.buildHistoricalChart(
                                            priceLineChart, currentAuction.getBidHistory());
                                    updateChartLabel();
                                }
                                if (wasExtended) {
                                    showBidNotification("⏱️", "Gia hạn thêm thời gian",
                                            "Phiên được mở rộng thêm",
                                            "#f39c12", 2.0);
                                } else if (canBid && updated.getHighestBidder() != null
                                        && !updated.getHighestBidder().getUserName().equals(sessionUser.getUserName())) {
                                    showBidNotification("🔥", "Bạn vừa bị vượt giá!",
                                            CurrencyFormatter.format(updated.getCurrentHighestBid()),
                                            "#e74c3c", 1.5);
                                }
                            });
                }

                // Phiên kết thúc broadcast
                if ("AUCTION_ENDED".equals(msg) && response.getData() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Auction> list = (List<Auction>) response.getData();
                    list.stream()
                            .filter(a -> a.getAuctionId().equals(currentAuction.getAuctionId()))
                            .findFirst()
                            .ifPresent(ended -> {
                                currentAuction = ended;
                                updateUI();
                                renderBidHistory();
                                // FIX #3: Cập nhật biểu đồ lần cuối khi phiên kết thúc
                                if (priceLineChart != null) {
                                    priceSeries = PriceChartHelper.buildHistoricalChart(
                                            priceLineChart, currentAuction.getBidHistory());
                                    updateChartLabel();
                                }
                                if (countdownTimeline != null) countdownTimeline.stop();
                                if (lblTimeLeft != null) {
                                    lblTimeLeft.setText("ĐÃ KẾT THÚC");
                                    lblTimeLeft.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 18px;");
                                }
                                // Thông báo kết quả
                                if (canBid && ended.getHighestBidder() != null) {
                                    boolean won = ended.getHighestBidder().getUserName()
                                            .equals(sessionUser.getUserName());
                                    if (won) {
                                        showWinDialog(ended);
                                    } else {
                                        showLoseDialog(ended);
                                    }
                                } else if (ended.getHighestBidder() == null
                                        && (ended.getStatus() == AuctionStatus.FINISHED
                                        || ended.getStatus() == AuctionStatus.PAID)) {
                                    showEndedNoWinnerDialog();
                                }
                                // Tắt controls
                                canBid = false;
                                setupBidControls();
                                if (autoBidPane != null) { autoBidPane.setVisible(false); autoBidPane.setManaged(false); }
                            });
                }

                // Phiên bị hủy giữa chừng — broadcast
                if ("AUCTION_CANCELED".equals(msg) && response.getData() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Auction> list = (List<Auction>) response.getData();
                    list.stream()
                            .filter(a -> a.getAuctionId().equals(currentAuction.getAuctionId()))
                            .findFirst()
                            .ifPresent(canceled -> {
                                currentAuction = canceled;
                                updateUI();
                                if (countdownTimeline != null) countdownTimeline.stop();
                                if (lblTimeLeft != null) {
                                    lblTimeLeft.setText("ĐÃ HỦY");
                                    lblTimeLeft.setStyle("-fx-text-fill: #7f8c8d; -fx-font-weight: bold; -fx-font-size: 18px;");
                                }
                                showBidNotification("🚫", "Phiên đã bị hủy",
                                        "Tiền đặt cọc đã được hoàn lại",
                                        "#7f8c8d", 3.0);
                                canBid = false;
                                setupBidControls();
                                if (autoBidPane != null) { autoBidPane.setVisible(false); autoBidPane.setManaged(false); }
                            });
                }

                // Response trực tiếp cho PLACE_BID thành công
                if (response.getStatus() == StatusType.SUCCESS
                        && "Đặt giá thành công!".equals(msg)) {
                    String bidAmountText = (txtBidAmount != null && !txtBidAmount.getText().isEmpty())
                            ? txtBidAmount.getText().trim() : null;
                    String bidAmountDisplay = "---";
                    try {
                        if (bidAmountText != null)
                            bidAmountDisplay = CurrencyFormatter.format(Double.parseDouble(bidAmountText));
                    } catch (NumberFormatException ignored) {}
                    showBidNotification("✅", "Đặt giá thành công!",
                            bidAmountDisplay,
                            "#27ae60", 1.5);
                    // Toast riêng biệt để rõ ràng hơn
                    NotificationService.get().success(
                            "✅ Đặt giá " + bidAmountDisplay + " thành công!",
                            lblMessage
                    );
                    if (txtBidAmount != null) txtBidAmount.clear();
                    if (bidValidator != null) bidValidator.clearValidation();
                    if (btnBid != null) btnBid.setDisable(false);
                    // Cập nhật số dư local
                    if (response.getData() instanceof Double newBalance) {
                        ((Bidder) sessionUser).setBalance(newBalance);
                    }
                }

                // Response lỗi PLACE_BID — phân loại lỗi cụ thể
                if (response.getStatus() == StatusType.ERROR) {
                    String errMsg = response.getMessage();
                    String toastMsg;
                    if (errMsg != null && errMsg.toLowerCase().contains("giá")) {
                        toastMsg = "⚠️ " + errMsg; // Giá quá thấp
                        showBidNotification("⚠️", "Giá không hợp lệ", errMsg, "#f39c12", 1.5);
                        NotificationService.get().warning(toastMsg, lblMessage);
                    } else if (errMsg != null && errMsg.toLowerCase().contains("số dư")) {
                        toastMsg = "❌ Số dư không đủ!";
                        showBidNotification("❌", "Số dư không đủ", errMsg, "#e74c3c", 1.5);
                        NotificationService.get().error(toastMsg, lblMessage);
                    } else if (errMsg != null && errMsg.toLowerCase().contains("kết thúc")) {
                        toastMsg = "⛔ Phiên đấu giá đã kết thúc!";
                        showBidNotification("⛔", "Phiên đã kết thúc", errMsg, "#e74c3c", 1.5);
                        NotificationService.get().error(toastMsg, lblMessage);
                    } else {
                        toastMsg = "❌ Đặt giá thất bại: " + errMsg;
                        showBidNotification("❌", "Đặt giá thất bại", errMsg, "#e74c3c", 1.5);
                        NotificationService.get().error(toastMsg, lblMessage);
                    }
                    setMessage("❌ " + errMsg, "#e74c3c");
                    if (btnBid != null) btnBid.setDisable(false);
                }

                // Response cho SET_AUTOBID
                if ("AUTOBID_OK".equals(msg)) {
                    boolean nowActive = !autoBidActive;
                    updateAutoBidButtonStyle(nowActive);
                    if (nowActive) {
                        setAutoBidStatus("🤖 AutoBid đang hoạt động! Hệ thống sẽ tự trả giá thay bạn.", "#27ae60");
                        showBidNotification("🤖", "AutoBid đã bật",
                                "Tự đặt giá khi bị vượt", "#8e44ad", 1.5);
                    } else {
                        setAutoBidStatus("AutoBid đã TẮT.", "#666");
                        if (txtAutoBidMax       != null) txtAutoBidMax.setDisable(false);
                        if (txtAutoBidIncrement != null) txtAutoBidIncrement.setDisable(false);
                        setMessage("ℹ️ AutoBid đã tắt.", "#A0A0A0");
                    }
                }
                if ("AUTOBID_ERROR".equals(msg)) {
                    setAutoBidStatus("❌ " + response.getMessage(), "#e74c3c");
                    if (txtAutoBidMax       != null) txtAutoBidMax.setDisable(false);
                    if (txtAutoBidIncrement != null) txtAutoBidIncrement.setDisable(false);
                }
            });
        });
    }

    /**
     * Hiển thị popup chiến thắng đẹp mắt khi bidder thắng phiên đấu giá.
     * Dùng JavaFX Stage riêng để không block UI thread.
     */
    private void showWinDialog(Auction ended) {
        // Cập nhật message ngắn trong màn hình chính
        setMessage("🏆 Chúc mừng! Bạn đã THẮNG phiên đấu giá này!", "#27ae60");

        // Tạo popup Stage riêng
        Stage popup = new Stage();
        popup.initOwner(lblProductName != null && lblProductName.getScene() != null
                ? lblProductName.getScene().getWindow() : null);
        popup.initStyle(javafx.stage.StageStyle.UNDECORATED);
        popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);

        // ─── Root container ───────────────────────────────────────────────────
        VBox root = new VBox(18);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(40, 50, 36, 50));
        root.setStyle("-fx-background-color: #0D0D0D; "
                + "-fx-border-color: #F5C518; -fx-border-width: 2; "
                + "-fx-border-radius: 16; -fx-background-radius: 16; "
                + "-fx-effect: dropshadow(gaussian, rgba(245,197,24,0.6), 30, 0, 0, 0);");
        root.setPrefWidth(480);

        // ─── Confetti emoji row ────────────────────────────────────────────────
        Label confetti = new Label("🎊 🏆 🎉");
        confetti.setStyle("-fx-font-size: 40px;");

        // ─── Tiêu đề ──────────────────────────────────────────────────────────
        Label title = new Label("CHÚC MỪNG CHIẾN THẮNG!");
        title.setStyle("-fx-text-fill: #F5C518; -fx-font-size: 26px; -fx-font-weight: bold; "
                + "-fx-font-family: 'Arial Black';");
        title.setAlignment(Pos.CENTER);

        Label subtitle = new Label("Bạn đã trở thành chủ nhân của:");
        subtitle.setStyle("-fx-text-fill: #888; -fx-font-size: 13px;");

        // ─── Tên sản phẩm ─────────────────────────────────────────────────────
        Label itemName = new Label(ended.getItem().getNameItem());
        itemName.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-font-weight: bold;");
        itemName.setAlignment(Pos.CENTER);
        itemName.setWrapText(true);
        itemName.setMaxWidth(380);

        // ─── Separator ────────────────────────────────────────────────────────
        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setStyle("-fx-background-color: #2A2A2A;");

        // ─── Giá chốt ─────────────────────────────────────────────────────────
        VBox priceBox = new VBox(4);
        priceBox.setAlignment(Pos.CENTER);
        priceBox.setStyle("-fx-background-color: rgba(245,197,24,0.08); "
                + "-fx-border-color: rgba(245,197,24,0.3); -fx-border-radius: 10; "
                + "-fx-background-radius: 10; -fx-padding: 16 32;");
        Label priceLabel = new Label("GIÁ THẮNG CUỐI CÙNG");
        priceLabel.setStyle("-fx-text-fill: #666; -fx-font-size: 10px; -fx-font-weight: bold;");
        Label priceValue = new Label(CurrencyFormatter.format(ended.getCurrentHighestBid()));
        priceValue.setStyle("-fx-text-fill: #F5C518; -fx-font-size: 32px; -fx-font-weight: bold;");
        priceBox.getChildren().addAll(priceLabel, priceValue);

        // ─── Nút đóng ─────────────────────────────────────────────────────────
        Button btnClose = new Button("🎊  Tuyệt vời! Đóng");
        btnClose.setStyle("-fx-background-color: #F5C518; -fx-text-fill: #000; "
                + "-fx-font-weight: bold; -fx-font-size: 14px; "
                + "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 12 36;");
        btnClose.setMaxWidth(Double.MAX_VALUE);
        btnClose.setOnAction(e -> popup.close());

        root.getChildren().addAll(confetti, title, subtitle, itemName, sep, priceBox, btnClose);

        // ─── Scene ────────────────────────────────────────────────────────────
        javafx.scene.Scene scene = new javafx.scene.Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        popup.setScene(scene);

        // ─── Animation scale-in ──────────────────────────────────────────────
        root.setScaleX(0.6); root.setScaleY(0.6); root.setOpacity(0);
        popup.show();

        javafx.animation.ScaleTransition scale = new javafx.animation.ScaleTransition(
                Duration.millis(350), root);
        scale.setFromX(0.6); scale.setFromY(0.6);
        scale.setToX(1.0);   scale.setToY(1.0);
        scale.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(
                Duration.millis(300), root);
        fade.setFromValue(0); fade.setToValue(1);

        javafx.animation.ParallelTransition anim = new javafx.animation.ParallelTransition(scale, fade);
        anim.play();
    }

    /**
     * Popup kết quả THUA — to đẹp ở giữa màn hình, tương tự win dialog.
     */
    private void showLoseDialog(Auction ended) {
        Platform.runLater(() -> {
            Stage popup = new Stage();
            popup.initOwner(lblProductName != null && lblProductName.getScene() != null
                    ? lblProductName.getScene().getWindow() : null);
            popup.initStyle(javafx.stage.StageStyle.UNDECORATED);
            popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);

            VBox root = new VBox(16);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(38, 48, 32, 48));
            root.setStyle("-fx-background-color: #0D0D0D; "
                    + "-fx-border-color: #4a4a4a; -fx-border-width: 2; "
                    + "-fx-border-radius: 16; -fx-background-radius: 16; "
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 30, 0, 0, 0);");
            root.setPrefWidth(440);

            Label icon = new Label("😔");
            icon.setStyle("-fx-font-size: 52px;");

            Label title = new Label("PHIÊN ĐẤU GIÁ ĐÃ KẾT THÚC");
            title.setStyle("-fx-text-fill: #aaa; -fx-font-size: 20px; -fx-font-weight: bold; "
                    + "-fx-font-family: 'Arial Black';");
            title.setAlignment(Pos.CENTER);

            Label msg = new Label("Bạn không giành được lần này.");
            msg.setStyle("-fx-text-fill: #666; -fx-font-size: 14px;");

            // Tên sản phẩm
            Label itemName = new Label(ended.getItem().getNameItem());
            itemName.setStyle("-fx-text-fill: #ccc; -fx-font-size: 16px; -fx-font-weight: bold;");
            itemName.setAlignment(Pos.CENTER);
            itemName.setWrapText(true);
            itemName.setMaxWidth(340);

            javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
            sep.setStyle("-fx-background-color: #2A2A2A;");

            // Giá chốt
            VBox priceBox = new VBox(4);
            priceBox.setAlignment(Pos.CENTER);
            priceBox.setStyle("-fx-background-color: rgba(80,80,80,0.15); "
                    + "-fx-border-color: rgba(100,100,100,0.3); -fx-border-radius: 10; "
                    + "-fx-background-radius: 10; -fx-padding: 14 28;");
            Label priceLbl = new Label("GIÁ THẮNG CUỐI CÙNG");
            priceLbl.setStyle("-fx-text-fill: #555; -fx-font-size: 10px; -fx-font-weight: bold;");
            Label priceVal = new Label(CurrencyFormatter.format(ended.getCurrentHighestBid()));
            priceVal.setStyle("-fx-text-fill: #aaa; -fx-font-size: 28px; -fx-font-weight: bold;");
            priceBox.getChildren().addAll(priceLbl, priceVal);

            Label encourage = new Label("💪 Hãy thử lại lần sau!");
            encourage.setStyle("-fx-text-fill: #555; -fx-font-size: 12px;");

            Button btnClose = new Button("✕  Đóng");
            btnClose.setStyle("-fx-background-color: #2A2A2A; -fx-text-fill: #aaa; "
                    + "-fx-font-weight: bold; -fx-font-size: 13px; "
                    + "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 36;");
            btnClose.setMaxWidth(Double.MAX_VALUE);
            btnClose.setOnAction(e -> popup.close());

            root.getChildren().addAll(icon, title, msg, itemName, sep, priceBox, encourage, btnClose);

            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            popup.setScene(scene);

            root.setScaleX(0.65); root.setScaleY(0.65); root.setOpacity(0);
            popup.show();
            popup.centerOnScreen();

            javafx.animation.ScaleTransition scale = new javafx.animation.ScaleTransition(
                    Duration.millis(320), root);
            scale.setFromX(0.65); scale.setFromY(0.65);
            scale.setToX(1.0);   scale.setToY(1.0);
            scale.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
            javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(
                    Duration.millis(280), root);
            fade.setFromValue(0); fade.setToValue(1);
            new javafx.animation.ParallelTransition(scale, fade).play();
        });
    }

    /**
     * Popup khi phiên kết thúc mà không có ai đặt giá.
     */
    private void showEndedNoWinnerDialog() {
        Platform.runLater(() -> {
            Stage popup = new Stage();
            popup.initOwner(lblProductName != null && lblProductName.getScene() != null
                    ? lblProductName.getScene().getWindow() : null);
            popup.initStyle(javafx.stage.StageStyle.UNDECORATED);
            popup.initModality(javafx.stage.Modality.APPLICATION_MODAL);

            VBox root = new VBox(14);
            root.setAlignment(Pos.CENTER);
            root.setPadding(new Insets(36, 48, 30, 48));
            root.setStyle("-fx-background-color: #0D0D0D; "
                    + "-fx-border-color: #333; -fx-border-width: 2; "
                    + "-fx-border-radius: 16; -fx-background-radius: 16; "
                    + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.9), 24, 0, 0, 0);");
            root.setPrefWidth(380);

            Label icon = new Label("📫");
            icon.setStyle("-fx-font-size: 48px;");

            Label title = new Label("PHIÊN KẾT THÚC");
            title.setStyle("-fx-text-fill: #777; -fx-font-size: 18px; -fx-font-weight: bold;");

            Label msg = new Label("Không có ai đặt giá trong phiên này.");
            msg.setStyle("-fx-text-fill: #555; -fx-font-size: 13px;");
            msg.setWrapText(true);

            Button btnClose = new Button("✕  Đóng");
            btnClose.setStyle("-fx-background-color: #222; -fx-text-fill: #888; "
                    + "-fx-font-weight: bold; -fx-font-size: 13px; "
                    + "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 36;");
            btnClose.setMaxWidth(Double.MAX_VALUE);
            btnClose.setOnAction(e -> popup.close());

            root.getChildren().addAll(icon, title, msg, btnClose);

            javafx.scene.Scene scene = new javafx.scene.Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            popup.setScene(scene);

            root.setScaleX(0.7); root.setScaleY(0.7); root.setOpacity(0);
            popup.show();
            popup.centerOnScreen();

            javafx.animation.ScaleTransition scale = new javafx.animation.ScaleTransition(
                    Duration.millis(280), root);
            scale.setFromX(0.7); scale.setFromY(0.7);
            scale.setToX(1.0);  scale.setToY(1.0);
            scale.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
            javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(
                    Duration.millis(250), root);
            fade.setFromValue(0); fade.setToValue(1);
            new javafx.animation.ParallelTransition(scale, fade).play();
        });
    }

    // ─── Toast thông báo nhỏ gọn — góc dưới phải, tự ẩn sau 1.5s ────────────────────

    /**
     * Hiển toast thông báo nhỏ gọn góc dưới phải.
     *
     * @param icon        Emoji icon nhỏ
     * @param title       Dòng chầu in đậm
     * @param sub         Dòng phụ (null = ẩn)
     * @param accentColor Màu thanh accent trái (hex)
     * @param durationSec Thời gian tải hiện, thường 1.5
     */
    private void showBidNotification(String icon, String title, String sub,
                                     String accentColor, double durationSec) {
        if (notificationBanner == null) return;

        // Hủy pause cũ nếu đang chạy (tránh chồng)
        if (notifPause != null) notifPause.stop();

        // ― Cập nhật nội dung ―――――――――――――――――――――――――――――――――――――――
        if (lblNotifIcon  != null) lblNotifIcon.setText(icon);
        if (lblNotifTitle != null) lblNotifTitle.setText(title);
        if (lblNotifSub != null) {
            boolean hasSub = sub != null && !sub.isBlank();
            lblNotifSub.setText(hasSub ? sub : "");
            lblNotifSub.setVisible(hasSub);
            lblNotifSub.setManaged(hasSub);
        }

        // ― Đổi màu accent bar ―――――――――――――――――――――――――――――――――――
        if (notifAccentBar != null)
            notifAccentBar.setStyle("-fx-background-color: " + accentColor
                    + "; -fx-background-radius: 0 2 2 0;");

        // ― Buộc HBox co đúng theo nội dung — không để StackPane kéo căng cả 2 chiều ―
        notificationBanner.setMaxWidth(Region.USE_PREF_SIZE);
        notificationBanner.setMaxHeight(Region.USE_PREF_SIZE);

        // ― Chuẩn bị: reset translateX + opacity ――――――――――――――――――――――――
        notificationBanner.setTranslateX(340);
        notificationBanner.setOpacity(0);
        notificationBanner.setVisible(true);
        notificationBanner.setManaged(true);

        // ― Slide-in từ phải + fade-in (200ms) ――――――――――――――――――――――
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(200), notificationBanner);
        slideIn.setFromX(340);
        slideIn.setToX(0);
        slideIn.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), notificationBanner);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        ParallelTransition showAnim = new ParallelTransition(slideIn, fadeIn);

        // ― Tự ẩn sau durationSec giây ――――――――――――――――――――――――――――――
        notifPause = new PauseTransition(Duration.seconds(durationSec));
        notifPause.setOnFinished(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(250), notificationBanner);
            fadeOut.setFromValue(1);
            fadeOut.setToValue(0);

            TranslateTransition slideOut = new TranslateTransition(Duration.millis(220), notificationBanner);
            slideOut.setFromX(0);
            slideOut.setToX(340);
            slideOut.setInterpolator(javafx.animation.Interpolator.EASE_IN);

            ParallelTransition hideAnim = new ParallelTransition(fadeOut, slideOut);
            hideAnim.setOnFinished(ev -> {
                notificationBanner.setVisible(false);
                notificationBanner.setManaged(false);
            });
            hideAnim.play();
        });

        showAnim.setOnFinished(ev -> notifPause.play());
        showAnim.play();
    }

    private void setMessage(String text, String color) {
        if (lblMessage != null) {
            lblMessage.setText(text);
            lblMessage.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 12px; -fx-font-weight: bold;");
        }
    }

    // ─── Back ────────────────────────────────────────────────────────────────

    @FXML
    public void onBackButtonClick(javafx.event.ActionEvent event) {
        if (countdownTimeline != null) countdownTimeline.stop();
        // FIX: Gỡ đúng key của màn hình này, không ảnh hưởng listeners khác
        NetworkClient.getInstance().removeEventListener(LISTENER_KEY);

        try {
            String path;
            if (sessionUser instanceof Admin) {
                path = "/com/auction/view/AdminDashboard.fxml";
            } else if (sessionUser instanceof Seller) {
                path = "/com/auction/view/SellerDashboard.fxml";
            } else {
                path = "/com/auction/view/BidderDashboard.fxml";
            }
            Parent root = FXMLLoader.load(getClass().getResource(path));
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root, 1280, 800));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
