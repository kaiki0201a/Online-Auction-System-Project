package com.auction.protocol;

import java.io.Serializable;

public class Response implements Serializable {
    private static final long serialVersionUID = 1L;

    private StatusType status;
    private String message;
    private Object data;
    public Response(StatusType status, String message, Object data) {
        this.status = status;
        this.message = message;
        this.data = data;
    }
    public StatusType getStatus() {
        return status;
    }
    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
