package com.tmd.service;

import com.tmd.entity.QRCodeStatus;
import com.tmd.entity.dto.UserProfile;

public interface QRCodeService {
    QRCodeStatus getQRCodeStatus(String qrCodeId);

    boolean cancelLogin(String qrCodeId);

    boolean confirmLogin(String qrCodeId, UserProfile userinfo);

    boolean scanQRCode(String qrCodeId, QRCodeStatus.Status status);

    byte[] getQRCodeImage(String qrCodeId, String baseUrl);

    QRCodeStatus generateQRCode();
}
