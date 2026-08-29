package com.pmsjl.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * Aliyun OSS client configuration.
 */
@Configuration
@ConfigurationProperties(prefix = "oss.client")
@Validated
@Data
public class CosClientConfig {

    /**
     * Aliyun AccessKey ID.
     */
    @NotBlank(message = "OSS_ACCESS_KEY 未配置")
    private String accessKey;

    /**
     * Aliyun AccessKey Secret.
     */
    @NotBlank(message = "OSS_SECRET_KEY 未配置")
    private String secretKey;

    /**
     * OSS endpoint, for example https://oss-cn-shenzhen.aliyuncs.com.
     */
    @NotBlank(message = "OSS_ENDPOINT 未配置")
    private String endpoint;

    /**
     * Bucket name.
     */
    @NotBlank(message = "OSS_BUCKET 未配置")
    private String bucket;

    /**
     * Public URL prefix used to access uploaded files.
     */
    @NotBlank(message = "OSS_HOST 未配置")
    private String host;

    @Bean
    public OSS ossClient() {
        return new OSSClientBuilder().build(endpoint, accessKey, secretKey);
    }
}
