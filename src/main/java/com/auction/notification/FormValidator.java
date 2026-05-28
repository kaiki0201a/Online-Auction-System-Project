package com.auction.notification;

import com.auction.utils.CurrencyFormatter;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * FormValidator — Realtime form validation helper.
 *
 * Sử dụng fluent API:
 * <pre>
 *   FormValidator.of(txtUsername)
 *       .required("Vui lòng nhập tên đăng nhập")
 *       .minLength(4, "Tối thiểu 4 ký tự")
 *       .attach();   // Bắt đầu lắng nghe text changes
 *
 *   // Khi submit:
 *   boolean ok = validator.validate();
 * </pre>
 *
 * Styling:
 *  - Chỉ hiển thị label lỗi nhỏ bên dưới field khi có lỗi
 *  - KHÔNG đổi màu viền/nền ô input — giữ nguyên style gốc
 */
public class FormValidator {

    // ─── Regex constants ──────────────────────────────────────────────────────

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // ─── Label error style ────────────────────────────────────────────────────

    private static final String LABEL_ERROR_STYLE =
        "-fx-text-fill: #e74c3c; -fx-font-size: 11px; -fx-padding: 2 0 0 4;";

    // ─── State ────────────────────────────────────────────────────────────────

    private final TextField             field;
    private final List<Rule>            rules   = new ArrayList<>();
    private       Label                 errorLabel;
    private       ChangeListener<String> listener;
    private       boolean               touched = false;

    // ─── Factory ──────────────────────────────────────────────────────────────

    private FormValidator(TextField field) {
        this.field = field;
    }

    public static FormValidator of(TextField field) {
        return new FormValidator(field);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  RULE BUILDERS — Fluent API
    // ═══════════════════════════════════════════════════════════════════════════

    public FormValidator required() {
        return required("Trường này không được để trống");
    }

    public FormValidator required(String message) {
        rules.add(new Rule(v -> !v.isBlank(), message));
        return this;
    }

    public FormValidator minLength(int min) {
        return minLength(min, "Tối thiểu " + min + " ký tự");
    }

    public FormValidator minLength(int min, String message) {
        rules.add(new Rule(v -> v.length() >= min, message));
        return this;
    }

    public FormValidator maxLength(int max) {
        return maxLength(max, "Tối đa " + max + " ký tự");
    }

    public FormValidator maxLength(int max, String message) {
        rules.add(new Rule(v -> v.length() <= max, message));
        return this;
    }

    public FormValidator email() {
        rules.add(new Rule(
            v -> EMAIL_PATTERN.matcher(v).matches(),
            "Email không hợp lệ (ví dụ: user@example.com)"
        ));
        return this;
    }

    public FormValidator numeric() {
        rules.add(new Rule(
            v -> v.matches("\\d+(\\.\\d+)?"),
            "Vui lòng chỉ nhập số"
        ));
        return this;
    }

    public FormValidator positiveNumber() {
        rules.add(new Rule(
            v -> {
                try { return Double.parseDouble(v) > 0; }
                catch (NumberFormatException e) { return false; }
            },
            "Giá trị phải lớn hơn 0"
        ));
        return this;
    }

    /**
     * Validation đặc biệt cho bid amount.
     *
     * @param currentHighestSupplier  Supplier trả về giá cao nhất hiện tại (lazy — gọi lúc validate)
     * @param balanceSupplier         Supplier trả về số dư ví (lazy)
     */
    public FormValidator bidAmount(Supplier<Double> currentHighestSupplier,
                                   Supplier<Double> balanceSupplier) {
        rules.add(new Rule(
            v -> {
                try {
                    double amt = Double.parseDouble(v);
                    double currentHighest = currentHighestSupplier.get();
                    return amt > currentHighest;
                } catch (NumberFormatException e) { return false; }
            },
            () -> {
                try {
                    double amt = Double.parseDouble(field.getText().trim());
                    double cur = currentHighestSupplier.get();
                    if (amt <= cur) {
                        return "Giá phải cao hơn giá hiện tại: " + CurrencyFormatter.format(cur);
                    }
                } catch (NumberFormatException e) { /* handled below */ }
                return "Giá không hợp lệ";
            }
        ));
        rules.add(new Rule(
            v -> {
                try {
                    double amt = Double.parseDouble(v);
                    return amt <= balanceSupplier.get();
                } catch (NumberFormatException e) { return true; } // Lỗi khác đã check ở rule trên
            },
            () -> "Số dư không đủ! Ví bạn có: " + CurrencyFormatter.format(balanceSupplier.get())
        ));
        return this;
    }

    /**
     * Kiểm tra mật khẩu xác nhận khớp với field mật khẩu chính.
     *
     * @param primaryField Field chứa mật khẩu gốc
     */
    public FormValidator passwordMatch(TextField primaryField) {
        rules.add(new Rule(
            v -> v.equals(primaryField.getText()),
            "Mật khẩu xác nhận không khớp"
        ));
        return this;
    }

    /**
     * Chỉ cho phép username hợp lệ: chữ cái, số, dấu gạch dưới, 4-32 ký tự.
     */
    public FormValidator username() {
        rules.add(new Rule(
            v -> v.matches("[A-Za-z0-9_]{4,32}"),
            "Tên đăng nhập chỉ gồm chữ cái, số, gạch dưới (4-32 ký tự)"
        ));
        return this;
    }

    /**
     * Validation thời gian kết thúc phiên đấu giá.
     *
     * @param startTimeSupplier Supplier trả về startTime (có thể null)
     */
    public FormValidator auctionEndTime(Supplier<java.time.LocalDateTime> startTimeSupplier) {
        rules.add(new Rule(
            v -> {
                // Field chứa DateTimePicker value — validate qua supplier
                java.time.LocalDateTime end = parseDateTime(v);
                if (end == null) return false;
                return end.isAfter(java.time.LocalDateTime.now());
            },
            "Thời gian kết thúc phải ở trong tương lai"
        ));
        rules.add(new Rule(
            v -> {
                java.time.LocalDateTime start = startTimeSupplier.get();
                java.time.LocalDateTime end   = parseDateTime(v);
                if (start == null || end == null) return true; // Đã check ở rule trên
                return end.isAfter(start);
            },
            "Thời gian kết thúc phải sau thời gian bắt đầu"
        ));
        return this;
    }

    /** Rule tùy chỉnh */
    public FormValidator custom(java.util.function.Predicate<String> predicate, String message) {
        rules.add(new Rule(predicate, message));
        return this;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  ATTACH — Bắt đầu lắng nghe realtime
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Đăng ký error label để hiển thị thông báo lỗi bên dưới field.
     * Label phải đã được thêm vào layout (có thể ẩn ban đầu).
     */
    public FormValidator withErrorLabel(Label label) {
        this.errorLabel = label;
        label.setVisible(false);
        label.setManaged(false);
        label.setStyle(LABEL_ERROR_STYLE);
        return this;
    }

    /**
     * Kích hoạt realtime validation: validate khi text thay đổi.
     * Chỉ hiển thị lỗi sau khi user đã chạm vào field (focus-lost lần đầu).
     *
     * @return this (cho phép gọi validation thủ công sau)
     */
    public FormValidator attach() {
        // Validate khi text thay đổi (nếu đã touched)
        listener = (obs, oldVal, newVal) -> {
            if (touched) runValidation(newVal.trim());
        };
        field.textProperty().addListener(listener);

        // Đánh dấu touched khi focus-lost
        field.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (wasFocused && !isFocused) { // Focus-lost
                touched = true;
                runValidation(field.getText().trim());
            }
        });

        return this;
    }

    /** Dừng lắng nghe (gọi khi destroy màn hình). */
    public void detach() {
        if (listener != null) {
            field.textProperty().removeListener(listener);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  VALIDATE — Gọi thủ công khi submit form
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Validate tất cả rules. Cập nhật styling.
     *
     * @return true nếu hợp lệ, false nếu có lỗi
     */
    public boolean validate() {
        touched = true;
        return runValidation(field.getText().trim());
    }

    /**
     * Xóa mọi lỗi, reset về style gốc.
     */
    public void clearValidation() {
        touched = false;
        // Không reset style field — giữ nguyên style gốc từ FXML/CSS
        if (errorLabel != null) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  STATIC HELPERS — Utility nhanh cho các controller
    // ═══════════════════════════════════════════════════════════════════════════

    /** Hiển thị lỗi qua label — KHÔNG đổi style field. */
    public static void setError(TextField field, Label errorLabel, String message) {
        // field style không đổi — giữ nguyên
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setStyle(LABEL_ERROR_STYLE);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }

    /** Ẩn label lỗi — KHÔNG đổi style field. */
    public static void setSuccess(TextField field, Label errorLabel) {
        // field style không đổi
        if (errorLabel != null) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }

    /** Xóa lỗi — KHÔNG đổi style field. */
    public static void clearError(TextField field, Label errorLabel) {
        // field style không đổi
        if (errorLabel != null) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }

    // ─── Private ──────────────────────────────────────────────────────────────

    private boolean runValidation(String value) {
        for (Rule rule : rules) {
            if (!rule.test(value)) {
                applyErrorStyle(rule.getMessage(value));
                return false;
            }
        }
        applySuccessStyle();
        return true;
    }

    private void applyErrorStyle(String message) {
        // KHÔNG đổi style field — chỉ hiện label lỗi bên dưới
        if (errorLabel != null) {
            errorLabel.setText(message);
            errorLabel.setStyle(LABEL_ERROR_STYLE);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }

    private void applySuccessStyle() {
        // KHÔNG đổi style field — chỉ ẩn label lỗi
        if (errorLabel != null) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }

    private static java.time.LocalDateTime parseDateTime(String v) {
        try {
            return java.time.LocalDateTime.parse(v,
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception e) { return null; }
    }

    // ─── Inner Rule ──────────────────────────────────────────────────────────

    private static class Rule {
        private final java.util.function.Predicate<String> predicate;
        private final Supplier<String>                      messageSupplier;

        Rule(java.util.function.Predicate<String> predicate, String staticMessage) {
            this.predicate       = predicate;
            this.messageSupplier = () -> staticMessage;
        }

        Rule(java.util.function.Predicate<String> predicate, Supplier<String> dynamicMessage) {
            this.predicate       = predicate;
            this.messageSupplier = dynamicMessage;
        }

        boolean test(String value)        { return predicate.test(value); }
        String  getMessage(String value)  { return messageSupplier.get(); }
    }
}
