package com.example.demo.service.market;

import com.example.demo.model.market.MarketOrder;
import com.example.demo.model.market.Trade;

import java.util.List;

/** Una orden en su estado final del pedido y las operaciones que ejecutó en ese pedido. */
public record OrderResult(MarketOrder order, List<Trade> trades) {

    public OrderResult {
        trades = List.copyOf(trades);
    }
}
