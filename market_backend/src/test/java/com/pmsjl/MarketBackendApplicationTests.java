package com.pmsjl;

import com.aliyun.oss.OSS;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "oss.client.access-key=test-access-key",
        "oss.client.secret-key=test-secret-key",
        "oss.client.bucket=test-bucket",
        "oss.client.host=https://assets.test.invalid",
        "ai.agent.internal-token=test-internal-token"
})
@AutoConfigureMockMvc
class MarketBackendApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OSS ossClient;

    @MockBean
    private RedissonClient redissonClient;

    @Test
    void contextLoads() {
    }

    @Test
    void livenessProbeIsPubliclyReachable() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

}
