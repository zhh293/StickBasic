package com.tmd.controller;

import com.tmd.entity.QRCodeStatus;
import com.tmd.service.QRCodeService;
import com.tmd.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
@RequestMapping("/qrcode")
public class QRCodeController {
    @Autowired
    private QRCodeService qrCodeService;
    @Autowired
    private UserService userService;
    /*@GetMapping("/generate")
    public ResponseEntity<QRCodeStatus>generateQRCode(){
        QRCodeStatus qrCodeStatus = qrCodeService.generateQRCode();
        log.info("QR Code generated with id: {}", qrCodeStatus.getQrCodeId());
        return ResponseEntity.ok(qrCodeStatus);
    }*/
    //获取二维码图片

}
