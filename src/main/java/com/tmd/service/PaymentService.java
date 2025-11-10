package com.tmd.service;

import com.tmd.entity.PaymentOrder;
import com.tmd.entity.PaymentRefund;

import java.math.BigDecimal;

public interface PaymentService {
    PaymentOrder createJsapiOrder(Long userId, String openId, BigDecimal amount, String description);
    PaymentOrder queryOrder(String orderNo);
    boolean closeOrder(String orderNo);
    PaymentRefund refund(String orderNo, BigDecimal amount, String reason);
    PaymentRefund queryRefund(String refundNo);
    void handlePayNotify(String notifyBody);
    void handleRefundNotify(String notifyBody);
}