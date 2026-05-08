package com.auction.protocol;

import java.io.Serializable;
public class Request implements Serializable {
    private static final long serialVersionUID = 1L;
    private ActionType action;
    private Object payload;
    public Request(ActionType action, Object payload) {
        this.action = action;
        this.payload = payload;
    }
    public ActionType getAction() {
        return action;
    }

    public Object getPayload() {
        return payload;
    }
}
