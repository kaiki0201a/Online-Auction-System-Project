package com.auction.dao.impl;
import com.auction.dao.IAuctionDAO;
import com.auction.model.Auction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionDAOImpl implements IAuctionDAO {
    private Map<String, Auction> database = new ConcurrentHashMap<>();
    @Override
    public boolean save(Auction obj){
        if(obj != null && obj.getAuctionId() != null){
            if(database.containsKey(obj.getAuctionId())){
                return false;

            }
            database.put(obj.getAuctionId(),obj);
            return true;
        }
        return false;
    }
    @Override
    public boolean update(Auction obj){
        if(obj!=null && database.containsKey(obj.getAuctionId())){
            database.put(obj.getAuctionId(), obj);
            return true;

        }
        return false;
    }
    @Override
    public boolean delete(String id){
        if(database.containsKey(id)){
            database.remove(id);
            return true;
        }
        return false;

    }
    @Override
    public Auction findById(String id){
        return database.get(id);
    }
    @Override
    public List<Auction> findAll(){
        return new ArrayList<>(database.values());
    }
}
