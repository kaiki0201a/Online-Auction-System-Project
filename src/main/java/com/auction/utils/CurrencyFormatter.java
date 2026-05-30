package com.auction.utils;

import java.text.DecimalFormat;

/**
 * CurrencyFormatter — Utility class định dạng tiền tệ theo chuẩn Việt Nam (Đồng VNĐ).
 */
public class CurrencyFormatter {

    /** Utility class — không cho phép khởi tạo. */
    private CurrencyFormatter() {
        throw new UnsupportedOperationException("Đây là class tiện ích, không được khởi tạo!");
    }

    /**
     * Định dạng số thành chuỗi tiền VNĐ có dấu phẩy phân cách.
     * Ví dụ: 1000.0 → "1,000 VNĐ"
     *
     * @param amount Số tiền cần định dạng
     * @return Chuỗi đã định dạng
     */
    public static String format(double amount) {
        DecimalFormat formatter = new DecimalFormat("#,### VNĐ");
        return formatter.format(amount);
    }
}