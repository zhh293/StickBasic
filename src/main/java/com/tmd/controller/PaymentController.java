package com.tmd.controller;

import com.tmd.entity.PaymentOrder;
import com.tmd.entity.PaymentRefund;
import com.tmd.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pay/wechat")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/jsapi/create")
    public ResponseEntity<?> createJsapiOrder(@RequestParam Long userId,
                                              @RequestParam String openId,
                                              @RequestParam BigDecimal amount,
                                              @RequestParam(required = false) String description) {
        PaymentOrder order = paymentService.createJsapiOrder(userId, openId, amount, description);
        Map<String, Object> resp = new HashMap<>();
        resp.put("orderNo", order.getOrderNo());
        resp.put("prepayId", order.getPrepayId());
        resp.put("status", order.getStatus());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/order/{orderNo}")
    public ResponseEntity<?> queryOrder(@PathVariable String orderNo) {
        PaymentOrder order = paymentService.queryOrder(orderNo);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/order/{orderNo}/close")
    public ResponseEntity<?> closeOrder(@PathVariable String orderNo) {
        boolean ok = paymentService.closeOrder(orderNo);
        return ResponseEntity.ok(Map.of("orderNo", orderNo, "closed", ok));
    }

    @PostMapping("/refund")
    public ResponseEntity<?> refund(@RequestParam String orderNo,
                                    @RequestParam BigDecimal amount,
                                    @RequestParam(required = false) String reason) {
        PaymentRefund refund = paymentService.refund(orderNo, amount, reason);
        return ResponseEntity.ok(refund);
    }

    @GetMapping("/refund/{refundNo}")
    public ResponseEntity<?> queryRefund(@PathVariable String refundNo) {
        PaymentRefund refund = paymentService.queryRefund(refundNo);
        return ResponseEntity.ok(refund);
    }

    // 微信支付异步通知回调（支付）
    @PostMapping("/notify")
    public ResponseEntity<String> payNotify(@RequestBody String body) {
        paymentService.handlePayNotify(body);
        // 微信回调需返回成功字符串，具体格式按官方文档；此处先简单返回
        return ResponseEntity.ok("success");
    }

    // 微信退款异步通知回调
    @PostMapping("/refund/notify")
    public ResponseEntity<String> refundNotify(@RequestBody String body) {
        paymentService.handleRefundNotify(body);
        return ResponseEntity.ok("success");
    }
}