package com.tmd.service.impl;

import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.tmd.entity.QRCodeStatus;
import com.tmd.entity.dto.UserProfile;
import com.tmd.service.QRCodeService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QRCodeServiceImpl implements QRCodeService {
    private final ConcurrentHashMap<String, QRCodeStatus>map=new ConcurrentHashMap<>();

    //获取新的二维码id和状态
    @Override
    public QRCodeStatus generateQRCode(){
        String qrCodeId= UUID.randomUUID().toString();
        QRCodeStatus qrCodeStatus=new QRCodeStatus();
        qrCodeStatus.setQrCodeId(qrCodeId);
        qrCodeStatus.setStatus(QRCodeStatus.Status.NEW);
        map.put(qrCodeId,qrCodeStatus);
        return qrCodeStatus;
    }

    @Override
    public QRCodeStatus getQRCodeStatus(String qrCodeId) {
        return  map.get(qrCodeId);
    }

    @Override
    public boolean cancelLogin(String qrCodeId) {
        QRCodeStatus qrCodeStatus=map.get(qrCodeId);
        if (qrCodeStatus==null)
            return false;
        qrCodeStatus.setStatus(QRCodeStatus.Status.CANCELLED);
        return true;
    }


    @Override
    public boolean confirmLogin(String qrCodeId, UserProfile userinfo) {
        return false;
    }

    @Override
    public boolean scanQRCode(String qrCodeId, QRCodeStatus.Status status) {
        return false;
    }

    @Override
    public byte[] getQRCodeImage(String qrCodeId, String baseUrl) {
        String qrContent=baseUrl+"/mobile/scan?qrCodeId="+qrCodeId;
        QRCodeWriter qrCodeWriter=new QRCodeWriter();
        //如果不释放IO资源，总有一刻操作系统的IO连接数会达到最大值，系统性能急剧下降，甚至最后会导致死机
        //也可能会出现OOM，因为这些连接无法被JVM 回收
        try (ByteArrayOutputStream outputStream=new ByteArrayOutputStream()){
            BitMatrix bitMatrix=qrCodeWriter.encode(qrContent,com.google.zxing.BarcodeFormat.QR_CODE,300,300);
            MatrixToImageWriter.writeToStream(bitMatrix,"PNG",outputStream);
            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    //更新二维码状态
    public boolean updateQRCodeStatus(String qrCodeId, QRCodeStatus.Status status) {
        QRCodeStatus qrCodeStatus=map.get(qrCodeId);
        if(qrCodeStatus==null){
            return false;
        }
        qrCodeStatus.setStatus(status);
        map.put(qrCodeId,qrCodeStatus);
        return true;
    }





}
