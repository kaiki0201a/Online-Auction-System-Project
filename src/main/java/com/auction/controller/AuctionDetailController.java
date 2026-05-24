package com.auction.controller;

import com.auction.client.NetworkClient;
import com.auction.model.*;
import com.auction.protocol.ActionType;
import com.auction.protocol.AutoBidPayload;
import com.auction.protocol.BidPayload;
import com.auction.protocol.Request;
import com.auction.protocol.StatusType;
import com.auction.utils.AppContext;
import com.auction.utils.CurrencyFormatter;
import com.auction.utils.PriceChartHelper;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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

    private Auction currentAuction;
    private User sessionUser;
    private boolean canBid = false;
    private boolean autoBidActive = false;
    private Timeline countdownTimeline;

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
        initPriceChart(); // FIX #3: Khởi tạo biểu đồ tiến trình giá

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
                lblTimeLeft.setStyle(secs < 60
                        ? "-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 22px; -fx-font-family: monospace;"
                        : "-fx-text-fill: #F5C518; -fx-font-weight: bold; -fx-font-size: 22px; -fx-font-family: monospace;");
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
            return;
        }
        if (currentAuction.getStatus() == AuctionStatus.FINISHED
                || currentAuction.getStatus() == AuctionStatus.PAID) {
            setMessage("⛔ Phiên đấu giá đã kết thúc!", "#e74c3c");
            return;
        }

        String text = txtBidAmount != null ? txtBidAmount.getText().trim() : "";
        if (text.isEmpty()) {
            setMessage("⚠️ Vui lòng nhập số tiền đặt giá!", "#f39c12");
            return;
        }
        try {
            double amount = Double.parseDouble(text);
            if (amount <= currentAuction.getCurrentHighestBid()) {
                setMessage("⚠️ Giá phải cao hơn giá hiện tại: "
                        + CurrencyFormatter.format(currentAuction.getCurrentHighestBid()), "#f39c12");
                return;
            }
            Bidder bidder = (Bidder) sessionUser;
            if (amount > bidder.getBalance()) {
                setMessage("❌ Số dư không đủ! Bạn có: "
                        + CurrencyFormatter.format(bidder.getBalance()), "#e74c3c");
                return;
            }

            BidPayload payload = new BidPayload(
                    currentAuction.getAuctionId(), bidder.getUserName(), amount);
            NetworkClient.getInstance().sendRequest(new Request(ActionType.PLACE_BID, payload));
            setMessage("🚀 Đang gửi giá thầu...", "#A0A0A0");
            if (btnBid != null) btnBid.setDisable(true);

        } catch (NumberFormatException ex) {
            setMessage("❌ Số tiền không hợp lệ!", "#e74c3c");
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
                                    // Rebuild chart với toàn bộ lịch sử mới nhất
                                    priceSeries = PriceChartHelper.buildHistoricalChart(
                                        priceLineChart, currentAuction.getBidHistory());
                                    updateChartLabel();
                                }
                                if (wasExtended) setMessage("⏱️ Hệ thống vừa gia hạn thêm thời gian!", "#f39c12");
                                // Thông báo bị vượt giá
                                if (canBid && updated.getHighestBidder() != null
                                        && !updated.getHighestBidder().getUserName().equals(sessionUser.getUserName())) {
                                    setMessage("🔥 Ai đó vừa trả giá cao hơn bạn!", "#e74c3c");
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
                                    setMessage(won
                                                    ? "🏆 Chúc mừng! Bạn đã THẮNG phiên đấu giá này!"
                                                    : "😔 Phiên kết thúc. Bạn không thắng lần này.",
                                            won ? "#27ae60" : "#e74c3c");
                                }
                                // Tắt controls
                                canBid = false;
                                setupBidControls();
                                if (autoBidPane != null) { autoBidPane.setVisible(false); autoBidPane.setManaged(false); }
                            });
                }

                // Response trực tiếp cho PLACE_BID thành công
                if (response.getStatus() == StatusType.SUCCESS
                        && "Đặt giá thành công!".equals(msg)) {
                    setMessage("✅ Đặt giá thành công!", "#27ae60");
                    if (txtBidAmount != null) txtBidAmount.clear();
                    if (btnBid != null) btnBid.setDisable(false);
                    // Cập nhật số dư local
                    if (response.getData() instanceof Double newBalance) {
                        ((Bidder) sessionUser).setBalance(newBalance);
                    }
                }

                // Response lỗi PLACE_BID
                if (response.getStatus() == StatusType.ERROR) {
                    setMessage("❌ " + response.getMessage(), "#e74c3c");
                    if (btnBid != null) btnBid.setDisable(false);
                }

                // Response cho SET_AUTOBID
                if ("AUTOBID_OK".equals(msg)) {
                    boolean nowActive = !autoBidActive;
                    updateAutoBidButtonStyle(nowActive);
                    if (nowActive) {
                        setAutoBidStatus("🤖 AutoBid đang hoạt động! Hệ thống sẽ tự trả giá thay bạn.", "#27ae60");
                        setMessage("✅ AutoBid đã bật thành công!", "#27ae60");
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

    private void setMessage(String text, String color) {
        if (lblMessage != null) {
            lblMessage.setText(text);
            lblMessage.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px; -fx-font-weight: bold;");
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
