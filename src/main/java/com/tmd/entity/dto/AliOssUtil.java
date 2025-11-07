package com.tmd.entity.dto;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.PutObjectRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Data
@AllArgsConstructor
@Slf4j
public class AliOssUtil {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

    /**
     * 文件上传（使用字节数组，适用于小文件）
     *
     * @param bytes      文件字节数组
     * @param objectName 对象名称
     * @return 文件访问URL
     */
    public String upload(byte[] bytes, String objectName) {
        return upload(new ByteArrayInputStream(bytes), objectName);
    }

    /**
     * 文件上传（使用输入流，适用于大文件，流式上传不占用大量内存）
     * 
     * 阿里云OSS支持流式上传，SDK内部会以流的方式读取数据并上传，
     * 不会将整个文件加载到内存中，适合上传大文件（最大支持5GB）。
     * 超过5GB的文件需要使用分片上传（Multipart Upload）。
     *
     * @param inputStream 文件输入流
     * @param objectName  对象名称
     * @return 文件访问URL
     */
    public String upload(InputStream inputStream, String objectName) {
        // 创建OSSClient实例
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            // 使用PutObjectRequest进行流式上传（官方推荐方式）
            // SDK内部会以流的方式读取InputStream并上传，不会一次性加载到内存
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, inputStream);

            // 执行上传，OSS SDK会流式读取InputStream
            ossClient.putObject(putObjectRequest);
            log.info("文件流式上传成功: objectName={}", objectName);
        } catch (OSSException oe) {
            log.error("OSS上传异常: Error Message={}, Error Code={}, Request ID={}, Host ID={}",
                    oe.getErrorMessage(), oe.getErrorCode(), oe.getRequestId(), oe.getHostId());
            throw new RuntimeException("OSS上传失败: " + oe.getErrorMessage(), oe);
        } catch (ClientException ce) {
            log.error("OSS客户端异常: {}", ce.getMessage());
            throw new RuntimeException("OSS客户端异常: " + ce.getMessage(), ce);
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }

        // 文件访问路径规则 https://BucketName.Endpoint/ObjectName
        StringBuilder stringBuilder = new StringBuilder("https://");
        stringBuilder
                .append(bucketName)
                .append(".")
                .append(endpoint)
                .append("/")
                .append(objectName);

        log.info("文件上传到:{}", stringBuilder.toString());

        return stringBuilder.toString();
    }

    /**
     * 删除文件
     *
     * @param objectName 文件对象名
     * @return 是否删除成功
     */
    public boolean delete(String objectName) {
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            // 删除文件
            ossClient.deleteObject(bucketName, objectName);
            log.info("文件删除成功: objectName={}", objectName);
            return true;
        } catch (OSSException oe) {
            log.error("删除文件时发生OSS异常: Error Message={}, Error Code={}, Request ID={}, Host ID={}",
                    oe.getErrorMessage(), oe.getErrorCode(), oe.getRequestId(), oe.getHostId());
            return false;
        } catch (ClientException ce) {
            log.error("删除文件时发生客户端异常: {}", ce.getMessage());
            return false;
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }
}
