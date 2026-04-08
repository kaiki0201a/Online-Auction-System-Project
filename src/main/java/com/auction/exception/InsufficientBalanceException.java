package com.auction.exception;

public class InsufficientBalanceException extends AuctionException {
    private double currentBalance;
    private double requiredAmount;

    public InsufficientBalanceException(String message, double currentBalance, double requiredAmount) {
        super(message);
        this.currentBalance = currentBalance;
        this.requiredAmount = requiredAmount;
    }

    public double getCurrentBalance() {
        return currentBalance;
    }

    public double getRequiredAmount() {
        return requiredAmount;
    }
}