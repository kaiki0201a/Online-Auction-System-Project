package com.auction.model;

import com.auction.App;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AppTest {
    @Test
    public void testAdd() {
        assertEquals(15, App.add(5, 10));
    }
}