package com.pmsjl;

import com.aliyun.oss.OSS;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MarketBackendApplicationTests {

    @MockBean
    private OSS ossClient;

    @MockBean
    private RedissonClient redissonClient;

    @Test
    void contextLoads() {
    }

}
