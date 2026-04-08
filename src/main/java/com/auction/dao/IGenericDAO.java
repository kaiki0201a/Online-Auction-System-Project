package com.auction.dao;

import java.util.List;

public interface IGenericDAO<T> {
    void save(T obj);
    void update(T obj);
    void delete(String id);
    T findById(String id);
    List<T> findAll();
}