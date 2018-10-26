package com.incurrency.framework;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility to manage requests
 *
 * $Id$
 */
public class RequestIDManager {

    private static RequestIDManager singleton = null;

    public static RequestIDManager singleton() {
        if (singleton == null) {
            singleton = new RequestIDManager();
        }
        return singleton;
    }
    private AtomicInteger requestId = new AtomicInteger(0);
    private AtomicInteger orderId = new AtomicInteger(-1);

    public RequestIDManager() {
    }

    public int getNextOrderId() {
        return orderId.getAndIncrement();
    }
    
    public int getNextOrderIdWithoutIncrement() {
        return orderId.get();
    }

    public int getNextRequestId() {
        return requestId.getAndIncrement();
    }

    public void initializeOrderId(int orderId) {
        this.orderId.set(orderId);
    }

    public boolean isOrderIdInitialized() {
        if (this.orderId.get() == -1) {
            return false;
        } else {
            //System.out.println("Order ID:"+orderId);
            return true;

        }
    }

    /**
     * @param requestId the requestId to set
     */
    public void setRequestId(AtomicInteger requestId) {
        this.requestId = requestId;
    }
}
