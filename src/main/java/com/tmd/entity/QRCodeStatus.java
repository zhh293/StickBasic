package com.tmd.entity;

import lombok.Data;

@Data
public class QRCodeStatus {

    public enum Status {
        NEW,         // 新生成，未扫描
        SCANNED,     // 已扫描
        CONFIRMED,   // 确认登录
        CANCELLED    // 已取消
    }

    private String qrCodeId;
    private Status status;
    private UserInfo userInfo; // 登录确认后绑定用户信息
}