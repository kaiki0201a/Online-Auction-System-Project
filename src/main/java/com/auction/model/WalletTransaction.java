package com.auction.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * WalletTransaction — lưu lịch sử một giao dịch nạp/rút tiền của ví.
 */
public class WalletTransaction implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type { DEPOSIT, WITHDRAW }

    private final Type type;
    private final double amount;
    private final double balanceAfter;
    private final LocalDateTime timestamp;

    public WalletTransaction(Type type, double amount, double balanceAfter) {
        this.type        = type;
        this.amount      = amount;
        this.balanceAfter = balanceAfter;
        this.timestamp   = LocalDateTime.now();
    }

    public Type getType()          { return type; }
    public double getAmount()      { return amount; }
    public double getBalanceAfter(){ return balanceAfter; }
    public LocalDateTime getTimestamp() { return timestamp; }

    public String getTypeLabel() {
        return type == Type.DEPOSIT ? "Nạp tiền" : "Rút tiền";
    }

    public String getFormattedTime() {
        return timestamp.format(DateTimeFormatter.ofPattern("dd/MM HH:mm:ss"));
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s → Số dư: %s",
            getFormattedTime(), getTypeLabel(),
            amount, balanceAfter);
    }
}
