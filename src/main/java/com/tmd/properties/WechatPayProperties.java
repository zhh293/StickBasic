package com.tmd.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "wechat.pay")
public class WechatPayProperties {
    private boolean enabled = true;
    private boolean mock = true;
    private String appId;
    private String mchId;
    private String apiV3Key;
    private String privateKeyPath;
    private String certSerialNo;
    private String platformCertPath;
    private String notifyUrl;
    private String refundNotifyUrl;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isMock() { return mock; }
    public void setMock(boolean mock) { this.mock = mock; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getMchId() { return mchId; }
    public void setMchId(String mchId) { this.mchId = mchId; }
    public String getApiV3Key() { return apiV3Key; }
    public void setApiV3Key(String apiV3Key) { this.apiV3Key = apiV3Key; }
    public String getPrivateKeyPath() { return privateKeyPath; }
    public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }
    public String getCertSerialNo() { return certSerialNo; }
    public void setCertSerialNo(String certSerialNo) { this.certSerialNo = certSerialNo; }
    public String getPlatformCertPath() { return platformCertPath; }
    public void setPlatformCertPath(String platformCertPath) { this.platformCertPath = platformCertPath; }
    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
    public String getRefundNotifyUrl() { return refundNotifyUrl; }
    public void setRefundNotifyUrl(String refundNotifyUrl) { this.refundNotifyUrl = refundNotifyUrl; }
}