package com.example.demo.service.market;

import com.example.demo.model.market.OrderSide;
import com.example.demo.model.market.OrderType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Motor de calce puro (spec 027, research D1 y D2): no toca la base ni muta lo que recibe. El lado opuesto del
 * libro llega ya ordenado por {@code MarketOrderRepository} (mejor precio primero y, a igual precio, la orden más
 * vieja), y cada calce se ejecuta al precio de la orden que ya estaba en el libro: quien la dejó fijó ese precio,
 * y quien llega lo acepta porque es igual o mejor que su tope. Una MARKET no tiene tope y toma cualquier precio.
 */
@Service
public class OrderMatchingEngineService {

    public static final BigDecimal BAND_LOWER = new BigDecimal("0.80");
    public static final BigDecimal BAND_UPPER = new BigDecimal("1.20");

    public MatchResult match(IncomingOrder incoming, List<BookEntry> oppositeBook) {
        List<Fill> fills = new ArrayList<>();
        int pending = incoming.quantity();
        for (BookEntry entry : oppositeBook) {
            if (pending == 0 || !crosses(incoming, entry.limitPrice())) {
                break;
            }
            // Un usuario nunca opera consigo mismo: su orden se saltea y queda en el libro.
            if (!entry.userId().equals(incoming.userId())) {
                int quantity = Math.min(pending, entry.remaining());
                fills.add(new Fill(entry.orderId(), entry.userId(), quantity, entry.limitPrice()));
                pending -= quantity;
            }
        }
        return new MatchResult(fills, pending);
    }

    /** {@code marketValue × 0.80 ≤ price ≤ marketValue × 1.20}, con los extremos incluidos y sin redondeo. */
    public boolean isWithinBand(BigDecimal price, BigDecimal marketValue) {
        return price.compareTo(marketValue.multiply(BAND_LOWER)) >= 0
                && price.compareTo(marketValue.multiply(BAND_UPPER)) <= 0;
    }

    private static boolean crosses(IncomingOrder incoming, BigDecimal bookPrice) {
        if (incoming.type() == OrderType.MARKET) {
            return true;
        }
        int comparison = bookPrice.compareTo(incoming.limitPrice());
        return incoming.side() == OrderSide.BUY ? comparison <= 0 : comparison >= 0;
    }
}
