package com.tmd.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.tmd.entity.PaymentOrder;
import com.tmd.entity.PaymentRefund;
import com.tmd.mapper.PaymentOrderMapper;
import com.tmd.mapper.PaymentRefundMapper;
import com.tmd.properties.WechatPayProperties;
import com.tmd.service.PaymentService;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.auth.AutoUpdateCertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.auth.PrivateKeySigner;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Credentials;
import com.wechat.pay.contrib.apache.httpclient.auth.WechatPay2Validator;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class WechatPayServiceImpl implements PaymentService {

    @Autowired
    private PaymentOrderMapper paymentOrderMapper;
    @Autowired
    private PaymentRefundMapper paymentRefundMapper;
    @Autowired
    private WechatPayProperties wechatPayProperties;

    @Override
    @Transactional
    public PaymentOrder createJsapiOrder(Long userId, String openId, BigDecimal amount, String description) {
        // 企业架构：此处可根据 wechatPayProperties.mock 决定走真实微信下单或Mock
        String orderNo = genOrderNo();
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setStatus("PENDING");
        order.setAmount(amount);
        order.setCurrency("CNY");
        order.setDescription(description);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // Mock模式：生成一个假prepay_id用于联调前端
        if (wechatPayProperties.isMock()) {
            order.setPrepayId("mock_prepay_" + UUID.randomUUID());
        } else {
            // 真实微信下单（JSAPI）：使用 wechatpay-java 生成签名的 HttpClient，并调用 v3 接口
            try (CloseableHttpClient client = buildWechatPayHttpClient()) {
                String url = "https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi";
                HttpPost post = new HttpPost(url);
                JSONObject payload = new JSONObject();
                payload.put("appid", wechatPayProperties.getAppId());
                payload.put("mchid", wechatPayProperties.getMchId());
                payload.put("description", description);
                payload.put("out_trade_no", orderNo);
                payload.put("notify_url", wechatPayProperties.getNotifyUrl());
                JSONObject amountObj = new JSONObject();
                amountObj.put("total", toCents(amount)); // 金额单位为分
                amountObj.put("currency", "CNY");
                payload.put("amount", amountObj);
                JSONObject payer = new JSONObject();
                payer.put("openid", openId);
                payload.put("payer", payer);

                StringEntity entity = new StringEntity(payload.toJSONString(), ContentType.APPLICATION_JSON);
                post.setEntity(entity);
                try (CloseableHttpResponse resp = client.execute(post)) {
                    int code = resp.getStatusLine().getStatusCode();
                    String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                    if (code >= 200 && code < 300) {
                        JSONObject json = JSON.parseObject(body);
                        String prepayId = json.getString("prepay_id");
                        order.setPrepayId(prepayId);
                    } else {
                        // 失败：保留订单为 PENDING，便于后续重试
                        throw new RuntimeException("WeChat JSAPI create failed: " + body);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("JSAPI order exception", e);
            }
        }

        paymentOrderMapper.insert(order);
        return paymentOrderMapper.findByOrderNo(orderNo);
    }

    @Override
    public PaymentOrder queryOrder(String orderNo) {
        return paymentOrderMapper.findByOrderNo(orderNo);
    }

    @Override
    @Transactional
    public boolean closeOrder(String orderNo) {
        // 实际微信关闭订单接口：/v3/pay/transactions/out-trade-no/{orderNo}/close
        // 这里先更新本地状态为 CLOSED
        int rows = paymentOrderMapper.updateStatus(orderNo, "CLOSED");
        return rows > 0;
    }

    @Override
    @Transactional
    public PaymentRefund refund(String orderNo, BigDecimal amount, String reason) {
        PaymentOrder order = paymentOrderMapper.findByOrderNo(orderNo);
        if (order == null) return null;

        PaymentRefund refund = new PaymentRefund();
        refund.setRefundNo(genRefundNo());
        refund.setOrderNo(orderNo);
        refund.setUserId(order.getUserId());
        refund.setAmount(amount);
        refund.setReason(reason);
        refund.setStatus("PENDING");
        refund.setTransactionId(order.getTransactionId());
        refund.setCreatedAt(LocalDateTime.now());
        refund.setUpdatedAt(LocalDateTime.now());

        paymentRefundMapper.insert(refund);

        if (!wechatPayProperties.isMock()) {
            // 真实退款：调用 /v3/refund/domestic/refunds
            try (CloseableHttpClient client = buildWechatPayHttpClient()) {
                String url = "https://api.mch.weixin.qq.com/v3/refund/domestic/refunds";
                HttpPost post = new HttpPost(url);
                JSONObject payload = new JSONObject();
                payload.put("out_refund_no", refund.getRefundNo());
                // 使用 out_trade_no 或 transaction_id 其中一个即可
                if (order.getTransactionId() != null) {
                    payload.put("transaction_id", order.getTransactionId());
                } else {
                    payload.put("out_trade_no", orderNo);
                }
                JSONObject amountObj = new JSONObject();
                amountObj.put("refund", toCents(amount));
                amountObj.put("total", toCents(order.getAmount()));
                amountObj.put("currency", "CNY");
                payload.put("amount", amountObj);
                if (reason != null) payload.put("reason", reason);

                StringEntity entity = new StringEntity(payload.toJSONString(), ContentType.APPLICATION_JSON);
                post.setEntity(entity);
                try (CloseableHttpResponse resp = client.execute(post)) {
                    int code = resp.getStatusLine().getStatusCode();
                    String body = EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8);
                    if (code >= 200 && code < 300) {
                        JSONObject json = JSON.parseObject(body);
                        String status = json.getString("status"); // e.g. SUCCESS, PROCESSING, CLOSED
                        paymentRefundMapper.updateStatus(refund.getRefundNo(), status);
                    } else {
                        throw new RuntimeException("WeChat refund failed: " + body);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException("Refund exception", e);
            }
        } else {
            // Mock：立即置成功并回写订单退款金额
            paymentRefundMapper.updateStatus(refund.getRefundNo(), "SUCCESS");
        }

        // 更新订单退款总额（仅示例）
        order.setRefundAmount(order.getRefundAmount() == null ? amount : order.getRefundAmount().add(amount));
        paymentOrderMapper.updateStatus(orderNo, order.getStatus());
        return paymentRefundMapper.findByRefundNo(refund.getRefundNo());
    }

    @Override
    public PaymentRefund queryRefund(String refundNo) {
        return paymentRefundMapper.findByRefundNo(refundNo);
    }

    @Override
    @Transactional
    public void handlePayNotify(String notifyBody) {
        // 解密通知体（微信 v3 回调），更新订单状态
        try {
            JSONObject root = JSON.parseObject(notifyBody);
            JSONObject resource = root.getJSONObject("resource");
            if (resource != null && "AEAD_AES_256_GCM".equals(resource.getString("algorithm"))) {
                String ciphertext = resource.getString("ciphertext");
                String nonce = resource.getString("nonce");
                String associatedData = resource.getString("associated_data");
                String plainText = decryptResource(associatedData, nonce, ciphertext, wechatPayProperties.getApiV3Key());
                JSONObject payObj = JSON.parseObject(plainText);
                String orderNo = payObj.getString("out_trade_no");
                String transactionId = payObj.getString("transaction_id");
                String tradeState = payObj.getString("trade_state");
                if ("SUCCESS".equalsIgnoreCase(tradeState)) {
                    paymentOrderMapper.updateOnPaid(orderNo, transactionId);
                } else {
                    paymentOrderMapper.updateStatus(orderNo, tradeState);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Handle pay notify exception", e);
        }
    }

    @Override
    @Transactional
    public void handleRefundNotify(String notifyBody) {
        // 解密退款通知，更新退款状态
        try {
            JSONObject root = JSON.parseObject(notifyBody);
            JSONObject resource = root.getJSONObject("resource");
            if (resource != null && "AEAD_AES_256_GCM".equals(resource.getString("algorithm"))) {
                String ciphertext = resource.getString("ciphertext");
                String nonce = resource.getString("nonce");
                String associatedData = resource.getString("associated_data");
                String plainText = decryptResource(associatedData, nonce, ciphertext, wechatPayProperties.getApiV3Key());
                JSONObject refundObj = JSON.parseObject(plainText);
                String refundNo = refundObj.getString("out_refund_no");
                String status = refundObj.getString("refund_status"); // SUCCESS, ABNORMAL, CLOSED
                if (refundNo != null) {
                    paymentRefundMapper.updateStatus(refundNo, status);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Handle refund notify exception", e);
        }
    }

    private String genOrderNo() {
        return "O" + System.currentTimeMillis();
    }

    private String genRefundNo() {
        return "R" + System.currentTimeMillis();
    }

    private CloseableHttpClient buildWechatPayHttpClient() throws Exception {
        String mchId = wechatPayProperties.getMchId();
        String serialNo = wechatPayProperties.getCertSerialNo();
        String privateKeyPath = wechatPayProperties.getPrivateKeyPath();

        String path = privateKeyPath != null && privateKeyPath.startsWith("file:")
                ? privateKeyPath.substring(5)
                : privateKeyPath;
        File file = new File(path);
        PrivateKey privateKey;
        try (FileInputStream fis = new FileInputStream(file)) {
            privateKey = PemUtil.loadPrivateKey(fis);
        }

        AutoUpdateCertificatesVerifier verifier = new AutoUpdateCertificatesVerifier(
                new WechatPay2Credentials(mchId, new PrivateKeySigner(serialNo, privateKey)),
                wechatPayProperties.getApiV3Key().getBytes(StandardCharsets.UTF_8));

        WechatPayHttpClientBuilder builder = WechatPayHttpClientBuilder.create()
                .withMerchant(mchId, serialNo, privateKey)
                .withValidator(new WechatPay2Validator(verifier));
        return builder.build();
    }

    private int toCents(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    private String decryptResource(String associatedData, String nonce, String ciphertext, String apiV3Key) throws Exception {
        SecretKeySpec key = new SecretKeySpec(apiV3Key.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8));
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        if (associatedData != null) {
            cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
        }
        byte[] plain = cipher.doFinal(java.util.Base64.getDecoder().decode(ciphertext));
        return new String(plain, StandardCharsets.UTF_8);
    }
}