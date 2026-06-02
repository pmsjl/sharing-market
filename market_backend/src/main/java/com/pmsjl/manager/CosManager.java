package com.pmsjl.manager;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.PutObjectResult;
import com.pmsjl.config.CosClientConfig;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Aliyun OSS object storage operations.
 */
@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private OSS ossClient;

    /**
     * Upload object.
     *
     * @param key local object key
     * @param localFilePath local file path
     * @return upload result
     */
    public PutObjectResult putObject(String key, String localFilePath) {
        return ossClient.putObject(cosClientConfig.getBucket(), normalizeKey(key), new File(localFilePath));
    }

    /**
     * Upload object.
     *
     * @param key local object key
     * @param file local file
     * @return upload result
     */
    public PutObjectResult putObject(String key, File file) {
        return ossClient.putObject(cosClientConfig.getBucket(), normalizeKey(key), file);
    }

    private String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.startsWith("/") ? key.substring(1) : key;
    }
}
