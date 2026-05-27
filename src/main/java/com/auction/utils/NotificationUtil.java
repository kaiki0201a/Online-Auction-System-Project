package com.auction.utils;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * NotificationUtil — Bộ công cụ hiển thị thông báo cho toàn bộ ứng dụng.
 *
 * Bao gồm:
 *  - showToast()    : Thông báo nhỏ góc dưới phải (3 giây tự ẩn)
 *  - showConfirm()  : Dialog xác nhận YES/NO theo theme dark
 *  - showAlert()    : Dialog thông báo (OK) theo theme dark
 */
public class NotificationUtil {

    // ═══════════════════════════════════════════════════════════════════════
    //  TOAST — thông báo nhỏ tự ẩn, góc dưới phải
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Hiển thị toast notification.
     *
     * @param message    Nội dung thông báo
     * @param anchorNode Node trong scene cùng cửa sổ
     * @param type       "success" | "error" | "warning" | null (default)
     */
    public static void showToast(String message, Node anchorNode, String type) {
        if (anchorNode == null || anchorNode.getScene() == null) return;
        Window window = anchorNode.getScene().getWindow();
        showToastOnWindow(message, window, type);
    }

    /**
     * Hiển thị toast từ Window (khi không có Node tham chiếu).
     */
    public static void showToastOnWindow(String message, Window window, String type) {
        showToastOnWindowInternal(message, window, type, 3.0, false);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  BID TOAST — toast góc TRÊN PHẢI, 1.5 giây, dành cho đặt giá / bị vượt
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Hiển thị toast nhỏ ở GÓC TRÊN PHẢI màn hình, tự biến sau 1.5 giây.
     *
     * @param message   Nội dung
     * @param window    Cửa sổ chứa toast
     * @param type      "success" (xanh) | "outbid" (cam) | "warning" | "error"
     */
    public static void showBidToast(String message, Window window, String type) {
        showToastOnWindowInternal(message, window, type, 1.5, true);
    }

    /**
     * Overload hỗ trợ Node thay vì Window.
     */
    public static void showBidToast(String message, Node anchorNode, String type) {
        if (anchorNode == null || anchorNode.getScene() == null) return;
        showBidToast(message, anchorNode.getScene().getWindow(), type);
    }

    /**
     * Internal: Hiển thị toast với tuùy chọn vị trí + thời gian.
     *
     * @param topRight true = góc trên phải, false = góc dưới phải
     */
    private static void showToastOnWindowInternal(
            String message, Window window, String type,
            double displaySeconds, boolean topRight) {

        if (window == null) return;

        // ─── Container ────────────────────────────────────────────────────
        HBox container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(12, 20, 12, 16));
        container.setMaxWidth(340);
        // Quan trọng: xóa background mặc định của JavaFX để tránh nền trắng
        container.setBackground(Background.EMPTY);

        // Màu sắc theo type
        String bgColor, borderColor, iconText, textColor;
        switch (type != null ? type : "") {
            case "success", "outbid_win" -> {
                bgColor     = "rgba(18, 60, 35, 0.97)";
                borderColor = "#27ae60";
                iconText    = type != null && type.equals("outbid_win") ? "🔥" : "✅";
                textColor   = "#A8F0C0";
            }
            case "outbid" -> {
                // Bị vượt giá → nền tối đỏ, viền đỏ, chữ đỏ nhạt
                bgColor     = "rgba(80, 10, 10, 0.97)";
                borderColor = "#e74c3c";
                iconText    = "🔔";
                textColor   = "#FF8080";
            }
            case "error" -> {
                bgColor     = "rgba(80, 10, 10, 0.97)";
                borderColor = "#e74c3c";
                iconText    = "❌";
                textColor   = "#FF8080";
            }
            case "warning" -> {
                bgColor     = "rgba(70, 50, 5, 0.97)";
                borderColor = "#f39c12";
                iconText    = "⚠️";
                textColor   = "#FFD580";
            }
            default -> {
                bgColor     = "rgba(18, 18, 22, 0.97)";
                borderColor = "#555";
                iconText    = "ℹ️";
                textColor   = "#C8C8C8";
            }
        }

        container.setStyle(
            "-fx-background-color: " + bgColor + "; " +
            "-fx-border-color: " + borderColor + "; " +
            "-fx-border-width: 1.5; " +
            "-fx-border-radius: 8; -fx-background-radius: 8; " +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.65), 16, 0, 0, 4);"
        );

        // Icon
        Label icon = new Label(iconText);
        icon.setStyle("-fx-font-size: 16px;");

        // Text — màu khác nhau theo từng loại thông báo
        Label lbl = new Label(message);
        lbl.setStyle("-fx-text-fill: " + textColor + "; -fx-font-size: 13px; -fx-font-weight: bold; " +
                     "-fx-font-family: 'Segoe UI', Arial, sans-serif;");
        lbl.setWrapText(true);
        lbl.setMaxWidth(280);

        container.getChildren().addAll(icon, lbl);
        container.setOpacity(0);

        Popup popup = new Popup();
        popup.getContent().add(container);
        popup.setAutoFix(true);
        // Không cho popup tự điều chỉnh vị trí lại (gây offset)
        popup.setAutoHide(false);

        popup.setOnShown(e -> {
            // Xóa nền trắng mặc định của Scene nội bộ Popup — đây là nguyên nhân "rìa trắng"
            if (popup.getScene() != null) {
                popup.getScene().setFill(Color.TRANSPARENT);
            }
            // Căn vị trí góc trên phải (hoặc dưới phải)
            double x = window.getX() + window.getWidth()  - popup.getWidth()  - 24;
            double y = topRight
                    ? window.getY() + 24
                    : window.getY() + window.getHeight() - popup.getHeight() - 24;
            popup.setX(x);
            popup.setY(y);
        });

        popup.show(window);

        // Slide-in từ phải
        TranslateTransition slide = new TranslateTransition(Duration.millis(280), container);
        slide.setFromX(60); slide.setToX(0);
        slide.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(250), container);
        fadeIn.setFromValue(0); fadeIn.setToValue(1);

        ParallelTransition enter = new ParallelTransition(slide, fadeIn);

        FadeTransition fadeOut = new FadeTransition(Duration.millis(400), container);
        fadeOut.setFromValue(1); fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> popup.hide());

        PauseTransition hold = new PauseTransition(Duration.seconds(displaySeconds));
        hold.setOnFinished(e -> fadeOut.play());
        enter.setOnFinished(e -> hold.play());
        enter.play();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  AUCTION RESULT DIALOG — Modal trung tâm hoành tráng khi kết thúc phên
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Hiển thị dialog kết quả cuối phên đấu giá ở GIỮ A MÀN HÌNH với nút OK.
     *
     * @param owner        Cửa sổ cha
     * @param resultType   "WIN" | "LOSE" | "NO_BID"
     * @param itemName     Tên sản phẩm
     * @param finalPrice   Giá chốt (chỉ dùng khi WIN)
     */
    public static void showAuctionResultDialog(
            Window owner, String resultType, String itemName, double finalPrice) {

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);

        VBox root = buildResultContent(resultType, itemName, finalPrice, dialog);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);

        // Animation scale-in + fade-in
        root.setScaleX(0.7); root.setScaleY(0.7); root.setOpacity(0);
        dialog.show();

        // Căn giữa màn hình
        if (owner != null) {
            dialog.setX(owner.getX() + (owner.getWidth()  - dialog.getWidth())  / 2);
            dialog.setY(owner.getY() + (owner.getHeight() - dialog.getHeight()) / 2);
        }

        ScaleTransition scale = new ScaleTransition(Duration.millis(400), root);
        scale.setFromX(0.7); scale.setFromY(0.7);
        scale.setToX(1.0);   scale.setToY(1.0);
        scale.setInterpolator(javafx.animation.Interpolator.EASE_OUT);

        FadeTransition fade = new FadeTransition(Duration.millis(350), root);
        fade.setFromValue(0); fade.setToValue(1);

        new ParallelTransition(scale, fade).play();
    }

    /** Xây dựng nội dung dialog theo 3 trường hợp kết quả */
    private static VBox buildResultContent(
            String resultType, String itemName, double finalPrice, Stage dialog) {

        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(44, 56, 40, 56));
        root.setPrefWidth(500);
        root.setMaxWidth(500);

        switch (resultType) {

            // ───────────────── THẪNG ─────────────────────────────────────
            case "WIN" -> {
                root.setStyle(
                    "-fx-background-color: #080E08; " +
                    "-fx-border-color: #27ae60; -fx-border-width: 2.5; " +
                    "-fx-border-radius: 20; -fx-background-radius: 20; " +
                    "-fx-effect: dropshadow(gaussian, rgba(39,174,96,0.55), 40, 0, 0, 0);"
                );

                Label confetti = new Label("🎊 🏆 🎉");
                confetti.setStyle("-fx-font-size: 44px;");

                Label title = new Label("CHÚC MỪNG!");
                title.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 34px; " +
                        "-fx-font-weight: bold; -fx-font-family: 'Arial Black';");

                Label subtitle = new Label("🏆  Bạn là người chiến thắng phiên đấu giá!");
                subtitle.setStyle("-fx-text-fill: #A8F0C0; -fx-font-size: 15px; -fx-font-weight: bold;");
                subtitle.setAlignment(Pos.CENTER);

                Label lblItem = new Label(itemName);
                lblItem.setStyle("-fx-text-fill: white; -fx-font-size: 19px; -fx-font-weight: bold;");
                lblItem.setWrapText(true); lblItem.setMaxWidth(400);
                lblItem.setAlignment(Pos.CENTER);
                lblItem.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

                Separator sep = new Separator();
                sep.setStyle("-fx-background-color: rgba(39,174,96,0.4);");

                VBox priceBox = new VBox(5);
                priceBox.setAlignment(Pos.CENTER);
                priceBox.setStyle(
                    "-fx-background-color: rgba(39,174,96,0.10); " +
                    "-fx-border-color: rgba(39,174,96,0.35); " +
                    "-fx-border-radius: 12; -fx-background-radius: 12; -fx-padding: 16 36;"
                );
                Label priceHdr = new Label("GIÁ THẪNG CUỐI CÙNG");
                priceHdr.setStyle("-fx-text-fill: #666; -fx-font-size: 10px; -fx-font-weight: bold;");
                Label priceVal = new Label(CurrencyFormatter.format(finalPrice));
                priceVal.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 34px; -fx-font-weight: bold;");
                priceBox.getChildren().addAll(priceHdr, priceVal);

                Button btnOk = buildOkButton("🎊  Tuyệt vời! Đóng", "#27ae60", "#000");
                btnOk.setOnAction(e -> dialog.close());

                root.getChildren().addAll(confetti, title, subtitle, lblItem, sep, priceBox, btnOk);
            }

            // ───────────────── THUA ───────────────────────────────────────
            case "LOSE" -> {
                root.setStyle(
                    "-fx-background-color: #0D0808; " +
                    "-fx-border-color: #c0392b; -fx-border-width: 2; " +
                    "-fx-border-radius: 20; -fx-background-radius: 20; " +
                    "-fx-effect: dropshadow(gaussian, rgba(192,57,43,0.45), 36, 0, 0, 0);"
                );

                Label icon = new Label("😔");
                icon.setStyle("-fx-font-size: 50px;");

                Label title = new Label("RẤT TIẾC...");
                title.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 30px; " +
                        "-fx-font-weight: bold; -fx-font-family: 'Arial Black';");

                Label subtitle = new Label("Bạn không chiến thắng phiên đấu giá này");
                subtitle.setStyle("-fx-text-fill: #F0A0A0; -fx-font-size: 15px; -fx-font-weight: bold;");
                subtitle.setAlignment(Pos.CENTER);

                Label lblItem = new Label(itemName);
                lblItem.setStyle("-fx-text-fill: #888; -fx-font-size: 16px;");
                lblItem.setWrapText(true); lblItem.setMaxWidth(400);
                lblItem.setAlignment(Pos.CENTER);
                lblItem.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

                Label encourage = new Label("🍀  Đừng nản lòng! Còn nhiều phiên khác đang chờ bạn.");
                encourage.setStyle("-fx-text-fill: #666; -fx-font-size: 13px; -fx-font-style: italic;");
                encourage.setWrapText(true); encourage.setMaxWidth(400);
                encourage.setAlignment(Pos.CENTER);
                encourage.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

                Button btnOk = buildOkButton("  OK  ", "#c0392b", "white");
                btnOk.setOnAction(e -> dialog.close());

                root.getChildren().addAll(icon, title, subtitle, lblItem, encourage, btnOk);
            }

            // ───────────────── KHAI KHÔNG THAM GIA ───────────────────────
            default -> { // "NO_BID"
                root.setStyle(
                    "-fx-background-color: #0A0A0D; " +
                    "-fx-border-color: #555; -fx-border-width: 2; " +
                    "-fx-border-radius: 20; -fx-background-radius: 20; " +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 36, 0, 0, 0);"
                );

                Label icon = new Label("📭");
                icon.setStyle("-fx-font-size: 50px;");

                Label title = new Label("PHIÊN ĐÃ KẾT THÚC");
                title.setStyle("-fx-text-fill: #888; -fx-font-size: 26px; " +
                        "-fx-font-weight: bold; -fx-font-family: 'Arial Black';");

                Label subtitle = new Label("Bạn chưa tham gia đặt giá trong phiên này");
                subtitle.setStyle("-fx-text-fill: #777; -fx-font-size: 14px;");
                subtitle.setAlignment(Pos.CENTER);

                Label lblItem = new Label(itemName);
                lblItem.setStyle("-fx-text-fill: #555; -fx-font-size: 15px;");
                lblItem.setWrapText(true); lblItem.setMaxWidth(400);
                lblItem.setAlignment(Pos.CENTER);
                lblItem.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

                Label hint = new Label("💡  Tham gia sớm hơn ở phên tiếp theo để có cơ hội thắng!");
                hint.setStyle("-fx-text-fill: #555; -fx-font-size: 12px; -fx-font-style: italic;");
                hint.setWrapText(true); hint.setMaxWidth(400);
                hint.setAlignment(Pos.CENTER);
                hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

                Button btnOk = buildOkButton("  OK  ", "#444", "white");
                btnOk.setOnAction(e -> dialog.close());

                root.getChildren().addAll(icon, title, subtitle, lblItem, hint, btnOk);
            }
        }

        return root;
    }

    /** Tạo nút OK chuẩn cho các dialog kết quả */
    private static Button buildOkButton(String text, String bgColor, String textColor) {
        Button btn = new Button(text);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setStyle(
            "-fx-background-color: " + bgColor + "; " +
            "-fx-text-fill: " + textColor + "; " +
            "-fx-font-weight: bold; -fx-font-size: 14px; " +
            "-fx-background-radius: 10; -fx-cursor: hand; -fx-padding: 13 40;"
        );
        // Hover effect
        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited (e -> btn.setOpacity(1.0));
        return btn;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CONFIRM DIALOG — xác nhận YES / NO theo dark theme
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Hiển thị dialog xác nhận YES/NO với dark theme nhất quán.
     *
     * @param owner    Cửa sổ cha (có thể null)
     * @param title    Tiêu đề dialog
     * @param message  Nội dung câu hỏi
     * @param yesLabel Nhãn nút xác nhận (ví dụ: "🔒 Khóa tài khoản")
     * @param noLabel  Nhãn nút hủy (ví dụ: "Hủy")
     * @return Optional<Boolean> — true nếu bấm YES, false nếu bấm NO, empty nếu đóng
     */
    public static Optional<Boolean> showConfirm(Window owner, String title,
                                                 String message,
                                                 String yesLabel, String noLabel) {
        final boolean[] result = {false};
        final boolean[] acted  = {false};

        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setTitle(title);

        // ─── Layout ───────────────────────────────────────────────────────
        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(32, 40, 28, 40));
        root.setPrefWidth(420);
        root.setStyle(
            "-fx-background-color: #1A1A1A;" +
            "-fx-border-color: #333;" +
            "-fx-border-width: 1.5;" +
            "-fx-border-radius: 12;" +
            "-fx-background-radius: 12;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.7), 24, 0, 0, 4);"
        );

        // Icon + Tiêu đề
        Label lblTitle = new Label("⚠️  " + title);
        lblTitle.setStyle("-fx-text-fill: #F5C518; -fx-font-size: 16px; -fx-font-weight: bold;");
        lblTitle.setAlignment(Pos.CENTER);
        lblTitle.setWrapText(true);

        // Nội dung
        Label lblMessage = new Label(message);
        lblMessage.setStyle("-fx-text-fill: #CCC; -fx-font-size: 13px;");
        lblMessage.setWrapText(true);
        lblMessage.setMaxWidth(340);
        lblMessage.setAlignment(Pos.CENTER);
        lblMessage.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        // Nút YES
        Button btnYes = new Button(yesLabel);
        btnYes.setPrefWidth(160);
        btnYes.setStyle(
            "-fx-background-color: #e74c3c; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-font-size: 13px;" +
            "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 20;"
        );
        btnYes.setOnAction(e -> { result[0] = true; acted[0] = true; dialog.close(); });

        // Nút NO
        Button btnNo = new Button(noLabel);
        btnNo.setPrefWidth(120);
        btnNo.setStyle(
            "-fx-background-color: #2A2A2A; -fx-text-fill: #AAA;" +
            "-fx-font-size: 13px;" +
            "-fx-border-color: #444; -fx-border-radius: 8; -fx-border-width: 1;" +
            "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 10 20;"
        );
        btnNo.setOnAction(e -> { result[0] = false; acted[0] = true; dialog.close(); });

        HBox btnBox = new HBox(12, btnNo, btnYes);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(lblTitle, lblMessage, btnBox);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();

        return acted[0] ? Optional.of(result[0]) : Optional.empty();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  INFO DIALOG — thông báo chỉ có nút OK, dark theme
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Hiển thị dialog thông báo thông tin (chỉ có nút OK).
     *
     * @param owner   Cửa sổ cha
     * @param title   Tiêu đề
     * @param message Nội dung
     * @param type    "success" | "error" | "warning" | "info"
     */
    public static void showAlert(Window owner, String title, String message, String type) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);

        VBox root = new VBox(18);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(30, 40, 26, 40));
        root.setPrefWidth(380);
        root.setStyle(
            "-fx-background-color: #1A1A1A;" +
            "-fx-border-color: #333;" +
            "-fx-border-width: 1.5;" +
            "-fx-border-radius: 12;" +
            "-fx-background-radius: 12;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.7), 24, 0, 0, 4);"
        );

        // Icon theo loại
        String icon = switch (type) {
            case "success" -> "✅";
            case "error"   -> "❌";
            case "warning" -> "⚠️";
            default        -> "ℹ️";
        };
        String titleColor = switch (type) {
            case "success" -> "#27ae60";
            case "error"   -> "#e74c3c";
            case "warning" -> "#f39c12";
            default        -> "#3498db";
        };

        Label lblTitle = new Label(icon + "  " + title);
        lblTitle.setStyle("-fx-text-fill: " + titleColor + "; -fx-font-size: 15px; -fx-font-weight: bold;");
        lblTitle.setWrapText(true);

        Label lblMessage = new Label(message);
        lblMessage.setStyle("-fx-text-fill: #CCC; -fx-font-size: 13px;");
        lblMessage.setWrapText(true);
        lblMessage.setMaxWidth(300);
        lblMessage.setAlignment(Pos.CENTER);
        lblMessage.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Button btnOk = new Button("  OK  ");
        btnOk.setStyle(
            "-fx-background-color: " + titleColor + "; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-font-size: 13px;" +
            "-fx-background-radius: 8; -fx-cursor: hand; -fx-padding: 8 28;"
        );
        btnOk.setOnAction(e -> dialog.close());

        HBox btnBox = new HBox(btnOk);
        btnBox.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(lblTitle, lblMessage, btnBox);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();
    }
}