package com.pmsjl.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Aliyun OSS client configuration.
 */
@Configuration
@ConfigurationProperties(prefix = "oss.client")
@Data
public class CosClientConfig {

    /**
     * Aliyun AccessKey ID.
     */
    private String accessKey;

    /**
     * Aliyun AccessKey Secret.
     */
    private String secretKey;

    /**
     * OSS endpoint, for example https://oss-cn-shenzhen.aliyuncs.com.
     */
    private String endpoint;

    /**
     * Bucket name.
     */
    private String bucket;

    /**
     * Public URL prefix used to access uploaded files.
     */
    private String host;

    @Bean
    public OSS ossClient() {
        return new OSSClientBuilder().build(endpoint, accessKey, secretKey);
    }
}
