package com.tmd.controller;

import com.tmd.entity.QRCodeStatus;
import com.tmd.entity.dto.UserProfile;
import com.tmd.service.QRCodeService;
import com.tmd.service.UserService;
import com.tmd.tools.BaseContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/qrcode")
public class QRCodeController {
    @Autowired
    private QRCodeService qrCodeService;
    @Autowired
    private UserService userService;
    @GetMapping("/generate")
    public ResponseEntity<QRCodeStatus>generateQRCode(){
        QRCodeStatus qrCodeStatus = qrCodeService.generateQRCode();
        log.info("QR Code generated with id: {}", qrCodeStatus.getQrCodeId());
        return ResponseEntity.ok(qrCodeStatus);
    }
    //获取二维码图片

    @GetMapping(value = "/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> getQRCodeImage(@PathVariable String qrCodeId, HttpServletRequest request) {
        String baseUrl=request.getScheme()+"://"+request.getServerName();
        if(request.getServerPort()!=80&&request.getServerPort()!=443){
            baseUrl+=":"+request.getServerPort();
        }
        byte[] image = qrCodeService.getQRCodeImage(qrCodeId,baseUrl);
        if(image!= null){
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image);
        }else{
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    @PostMapping("/scan")
    public ResponseEntity<String>scanQRCode(@RequestBody Map<String,String> request){
        String qrCodeId = request.get("qrCodeId");
        if(qrCodeId == null){
            return ResponseEntity.badRequest().body("Invalid request");
        }

        boolean updated=qrCodeService.scanQRCode(qrCodeId,QRCodeStatus.Status.SCANNED);
        if(!updated){
            return ResponseEntity.status(HttpStatus.CONFLICT).body("QR Code already scanned");
        }
        else{
            return ResponseEntity.ok("QR Code scanned successfully");
        }
    }
    //确认登陆
    @PostMapping("/confirm")
    public ResponseEntity<String>confirmLogin(@RequestParam String qrCodeId){
        if(qrCodeId == null){
            return ResponseEntity.badRequest().body("Invalid request");
        }
        Long l = BaseContext.get();
        UserProfile userinfo = userService.getProfile(l);
        if (userinfo == null){
           return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found");
       }
       boolean updated=qrCodeService.confirmLogin(qrCodeId,userinfo);
       if(!updated){
           return ResponseEntity.status(HttpStatus.CONFLICT).body("QR Code already confirmed");
       }
       else{
           return ResponseEntity.ok("QR Code confirmed successfully");
       }
    }

    //取消登录
    @PostMapping("/cancel")
    public ResponseEntity<String>cancelLogin(@RequestParam String qrCodeId){
        if(qrCodeId == null){
            return ResponseEntity.badRequest().body("Invalid request");
        }
        boolean cacelled=qrCodeService.cancelLogin(qrCodeId);
        if(!cacelled){
            return ResponseEntity.status(HttpStatus.CONFLICT).body("QR Code already cancelled");
        }
        else{
            return ResponseEntity.ok("QR Code cancelled successfully");
        }
    }
    //获取二维码的状态
    @GetMapping("/status/{qrCodeId}")
    public ResponseEntity<QRCodeStatus>getQRCodeStatus(@PathVariable String qrCodeId){
        QRCodeStatus qrCodeStatus = qrCodeService.getQRCodeStatus(qrCodeId);
        if(qrCodeStatus == null){
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(qrCodeStatus);
    }
}
