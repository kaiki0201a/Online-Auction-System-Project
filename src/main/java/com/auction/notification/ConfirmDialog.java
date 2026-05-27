package com.auction.notification;

import com.auction.utils.CurrencyFormatter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.Optional;

/**
 * ConfirmDialog — Dialog xác nhận YES/NO theo dark theme.
 *
 * Tính năng:
 *  - Dark theme đồng nhất với toàn app
 *  - Nút YES style theo danger/warning/safe
 *  - Preset shortcuts cho các tình huống phổ biến
 *  - Trả về Optional<Boolean> — caller xử lý, không biết về UI
 *
 * Sử dụng:
 * <pre>
 *   ConfirmDialog.banUser(window, "bidder1").ifPresent(yes -> {
 *       if (yes) doAction();
 *   });
 * </pre>
 */
public class ConfirmDialog {

    // ═══════════════════════════════════════════════════════════════════════════
    //  CORE — Generic confirm dialog
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Hiển thị dialog xác nhận YES/NO.
     *
     * @param owner    Cửa sổ cha
     * @param title    Tiêu đề
     * @param body     Nội dung câu hỏi
     * @param yesLabel Nhãn nút xác nhận
     * @param noLabel  Nhãn nút hủy
     * @param danger   true → nút YES màu đỏ, false → màu cam/xanh
     * @return Optional.of(true) nếu bấm YES, Optional.of(false) nếu NO, empty nếu đóng
     */
    public static Optional<Boolean> show(Window owner, String title, String body,
                                          String yesLabel, String noLabel, boolean danger) {
        final boolean[] result = {false};
        final boolean[] acted  = {false};

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);

        // ─── Root container ───────────────────────────────────────────────────
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(32, 44, 28, 44));
        root.setPrefWidth(440);
        root.setStyle(
            "-fx-background-color: #151515;" +
            "-fx-border-color: #2A2A2A;" +
            "-fx-border-width: 1;" +
            "-fx-border-radius: 14;" +
            "-fx-background-radius: 14;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.8), 30, 0, 0, 6);"
        );

        // ─── Icon + Title ─────────────────────────────────────────────────────
        String icon = danger ? "⚠️" : "🔔";
        String titleColor = danger ? "#e74c3c" : "#f39c12";

        Label lblTitle = new Label(icon + "  " + title);
        lblTitle.setStyle("-fx-text-fill: " + titleColor + "; " +
                          "-fx-font-size: 17px; -fx-font-weight: bold;");
        lblTitle.setWrapText(true);
        lblTitle.setMaxWidth(352);

        // ─── Body ─────────────────────────────────────────────────────────────
        Label lblBody = new Label(body);
        lblBody.setStyle("-fx-text-fill: #BBBBBB; -fx-font-size: 13px; -fx-line-spacing: 3;");
        lblBody.setWrapText(true);
        lblBody.setMaxWidth(352);
        lblBody.setAlignment(Pos.CENTER_LEFT);

        // ─── Separator ────────────────────────────────────────────────────────
        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setStyle("-fx-background-color: #222;");

        // ─── Buttons ──────────────────────────────────────────────────────────
        String yesBgColor = danger ? "#c0392b" : "#e67e22";
        String yesHoverColor = danger ? "#e74c3c" : "#f39c12";

        Button btnYes = new Button(yesLabel);
        btnYes.setPrefWidth(170);
        btnYes.setStyle(
            "-fx-background-color: " + yesBgColor + "; " +
            "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;" +
            "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 22;"
        );
        btnYes.setOnMouseEntered(e -> btnYes.setStyle(
            "-fx-background-color: " + yesHoverColor + "; " +
            "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;" +
            "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 22;"
        ));
        btnYes.setOnMouseExited(e -> btnYes.setStyle(
            "-fx-background-color: " + yesBgColor + "; " +
            "-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 13px;" +
            "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 22;"
        ));
        btnYes.setOnAction(e -> { result[0] = true; acted[0] = true; dialog.close(); });

        Button btnNo = new Button(noLabel);
        btnNo.setPrefWidth(110);
        btnNo.setStyle(
            "-fx-background-color: #1E1E1E; -fx-text-fill: #888;" +
            "-fx-font-size: 13px;" +
            "-fx-border-color: #333; -fx-border-radius: 8; -fx-border-width: 1;" +
            "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 22;"
        );
        btnNo.setOnMouseEntered(e -> btnNo.setStyle(
            "-fx-background-color: #2A2A2A; -fx-text-fill: #BBB;" +
            "-fx-font-size: 13px;" +
            "-fx-border-color: #444; -fx-border-radius: 8; -fx-border-width: 1;" +
            "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 22;"
        ));
        btnNo.setOnMouseExited(e -> btnNo.setStyle(
            "-fx-background-color: #1E1E1E; -fx-text-fill: #888;" +
            "-fx-font-size: 13px;" +
            "-fx-border-color: #333; -fx-border-radius: 8; -fx-border-width: 1;" +
            "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 22;"
        ));
        btnNo.setOnAction(e -> { result[0] = false; acted[0] = true; dialog.close(); });

        HBox btnBox = new HBox(10, btnNo, btnYes);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(lblTitle, lblBody, sep, btnBox);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);

        // Scale-in animation
        root.setScaleX(0.88); root.setScaleY(0.88); root.setOpacity(0);
        dialog.show();
        javafx.animation.ParallelTransition anim = new javafx.animation.ParallelTransition(
            createScale(root, 0.88, 1.0, 200),
            createFade(root, 0, 1, 180)
        );
        anim.play();
        dialog.showAndWait();

        return acted[0] ? Optional.of(result[0]) : Optional.empty();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PRESET SHORTCUTS — gọi nhanh cho các tình huống phổ biến
    // ═══════════════════════════════════════════════════════════════════════════

    /** Xác nhận đăng xuất */
    public static Optional<Boolean> logout(Window owner) {
        return show(owner,
            "Đăng xuất",
            "Bạn có chắc muốn đăng xuất không?\nMọi hoạt động đang xử lý sẽ bị dừng lại.",
            "🚪  Đăng Xuất",
            "Ở Lại",
            false
        );
    }

    /** Xác nhận dừng phiên đấu giá */
    public static Optional<Boolean> cancelAuction(Window owner, String itemName) {
        return show(owner,
            "Dừng Phiên Đấu Giá",
            "Bạn có chắc muốn DỪNG phiên:\n\"" + itemName + "\"?\n\n" +
            "⚠️ Tiền đặt cọc sẽ được hoàn lại cho Bidder đang dẫn đầu.\n" +
            "Hành động này KHÔNG thể hoàn tác.",
            "🚫  Dừng Phiên",
            "Hủy",
            true
        );
    }

    /** Xác nhận khóa tài khoản */
    public static Optional<Boolean> banUser(Window owner, String username) {
        return show(owner,
            "Khóa Tài Khoản",
            "Khóa tài khoản \"" + username + "\"?\n\n" +
            "Người dùng này sẽ không thể đăng nhập cho đến khi được mở khóa.",
            "🔒  Khóa Tài Khoản",
            "Hủy",
            true
        );
    }

    /** Xác nhận mở khóa tài khoản */
    public static Optional<Boolean> unbanUser(Window owner, String username) {
        return show(owner,
            "Mở Khóa Tài Khoản",
            "Mở khóa tài khoản \"" + username + "\"?\n\n" +
            "Người dùng này sẽ đăng nhập được trở lại.",
            "🔓  Mở Khóa",
            "Hủy",
            false
        );
    }

    /** Xác nhận đặt giá lớn (khi bid > 10x giá hiện tại hoặc > 1 triệu) */
    public static Optional<Boolean> placeLargeBid(Window owner, double amount) {
        return show(owner,
            "Xác Nhận Giá Thầu Lớn",
            "Bạn sắp đặt giá " + CurrencyFormatter.format(amount) + ".\n\n" +
            "Đây là một giá trị lớn. Bạn có chắc chắn muốn tiếp tục không?\n" +
            "Nếu thắng, số tiền này sẽ bị trừ từ ví của bạn.",
            "✅  Xác Nhận Đặt Giá",
            "Xem Lại",
            false
        );
    }

    /** Xác nhận rút tiền */
    public static Optional<Boolean> withdraw(Window owner, double amount) {
        return show(owner,
            "Xác Nhận Rút Tiền",
            "Bạn sắp rút " + CurrencyFormatter.format(amount) + " từ ví.\n\n" +
            "Giao dịch này sẽ được xử lý ngay lập tức.",
            "💸  Rút Tiền Ngay",
            "Hủy",
            false
        );
    }

    /** Xác nhận từ chối sản phẩm (Admin) */
    public static Optional<Boolean> rejectProduct(Window owner, String itemName) {
        return show(owner,
            "Từ Chối Sản Phẩm",
            "Từ chối sản phẩm \"" + itemName + "\"?\n\n" +
            "Seller sẽ nhận được thông báo từ chối.\n" +
            "Sản phẩm có thể được đăng lại sau khi chỉnh sửa.",
            "❌  Từ Chối",
            "Hủy",
            true
        );
    }

    // ─── Animation helpers ────────────────────────────────────────────────────

    private static javafx.animation.ScaleTransition createScale(
            Node node, double from, double to, int ms) {
        javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(
            javafx.util.Duration.millis(ms), node);
        st.setFromX(from); st.setFromY(from);
        st.setToX(to);     st.setToY(to);
        st.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        return st;
    }

    private static javafx.animation.FadeTransition createFade(
            Node node, double from, double to, int ms) {
        javafx.animation.FadeTransition ft = new javafx.animation.FadeTransition(
            javafx.util.Duration.millis(ms), node);
        ft.setFromValue(from); ft.setToValue(to);
        return ft;
    }
}
