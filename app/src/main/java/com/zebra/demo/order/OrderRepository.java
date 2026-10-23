package com.zebra.demo.order;

import java.io.IOException;
import java.util.List;

/**
 * Where orders come from. All methods block, so call them off the main thread
 * (OrderService does that). Swap implementations without touching the screens.
 */
public interface OrderRepository {

    /** Orders that are new or in progress. */
    List<Order> fetchOpenOrders() throws IOException;

    /**
     * Marks an order as being picked by this device. Returns false if somebody else
     * already took it. Claiming an order you already hold returns true.
     */
    boolean claim(String orderId, String deviceId) throws IOException;

    /** Records a finished pick. Must be idempotent on completion.completionId. */
    void complete(Completion completion) throws IOException;
}
