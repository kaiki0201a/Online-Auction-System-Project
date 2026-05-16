package com.auction.protocol;

import com.auction.model.User;
import java.io.Serializable;

public class LoginResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private boolean isSuccess;
    private String message;
    private User user;

    public LoginResponse(boolean isSuccess, String message, User user) {
        this.isSuccess = isSuccess;
        this.message = message;
        this.user = user;
    }

    public boolean isSuccess() { return isSuccess; }
    public String getMessage() { return message; }
    public User getUser() { return user; }
}