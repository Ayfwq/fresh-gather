package com.sky.utils;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.io.ByteArrayInputStream;

@Data
@AllArgsConstructor
@Slf4j
public class AliOssUtil {

    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;

    public String upload(byte[] bytes, String objectName) {
        if (endpoint == null || endpoint.trim().isEmpty()
                || accessKeyId == null || accessKeyId.trim().isEmpty()
                || accessKeySecret == null || accessKeySecret.trim().isEmpty()
                || bucketName == null || bucketName.trim().isEmpty()) {
            throw new IllegalStateException("对象存储配置不完整，请检查环境变量");
        }

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(bytes));
        } catch (OSSException e) {
            log.error("阿里云对象存储拒绝了文件上传，errorCode={}，requestId={}", e.getErrorCode(), e.getRequestId());
            throw new IllegalStateException("对象存储上传失败，请检查配置", e);
        } catch (ClientException e) {
            log.error("连接阿里云对象存储失败，objectName={}", objectName);
            throw new IllegalStateException("对象存储上传失败，请检查网络和配置", e);
        } finally {
            ossClient.shutdown();
        }

        // Support both endpoint forms, with or without the URL scheme.
        String normalizedEndpoint = endpoint.replaceFirst("^https?://", "").replaceAll("/+$", "");
        return "https://" + bucketName + "." + normalizedEndpoint + "/" + objectName;
    }
}
