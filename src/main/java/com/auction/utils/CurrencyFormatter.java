package com.auction.utils;

import java.text.DecimalFormat;

public class CurrencyFormatter {

    // 1. Khóa constructor lại để không ai có thể dùng từ khóa 'new' tạo object
    private CurrencyFormatter() {
        throw new UnsupportedOperationException("Đây là class tiện ích, không được khởi tạo!");
    }

    /**
     * 2. Hàm định dạng tiền tệ (Sử dụng VNĐ)
     * Ví dụ: 1000.0 -> 1,000 VNĐ
     */
    public static String format(double amount) {
        // "#,###" tự động chèn dấu phẩy phân cách hàng nghìn
        DecimalFormat formatter = new DecimalFormat("#,### VNĐ");
        return formatter.format(amount);
    }
}