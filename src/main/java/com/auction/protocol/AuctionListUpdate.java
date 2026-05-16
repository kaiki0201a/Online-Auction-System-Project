package com.auction.protocol;

import com.auction.model.Auction;
import java.io.Serializable;

public class AuctionListUpdate implements Serializable {
    private static final long serialVersionUID = 1L;

    private Auction updatedAuction;

    public AuctionListUpdate(Auction updatedAuction) {
        this.updatedAuction = updatedAuction;
    }

    public Auction getUpdatedAuction() { return updatedAuction; }
}