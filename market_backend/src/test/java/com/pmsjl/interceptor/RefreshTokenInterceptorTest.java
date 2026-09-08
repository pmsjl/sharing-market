package com.pmsjl.interceptor;

import com.pmsjl.common.AuthTokenProperties;
import com.pmsjl.model.vo.LoginUserVO;
import com.pmsjl.utils.TokenUtils;
import com.pmsjl.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.pmsjl.constant.RedisConstant.LOGIN_USER_KEY;
import static com.pmsjl.constant.RedisConstant.LOGIN_USER_TTL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenInterceptorTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @InjectMocks
    private RefreshTokenInterceptor interceptor;

    @BeforeEach
    void setUp() {
        new TokenUtils().setAuthTokenProperties(
                new AuthTokenProperties("Authorization", "Bearer")
        );
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void loadsRedisSessionAndRefreshesTtlForValidToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer session-123");
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(LOGIN_USER_KEY + "session-123"))
                .thenReturn(Map.of("id", "42", "userRole", "user"));

        boolean result = interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new Object()
        );

        assertTrue(result);
        LoginUserVO loginUser = UserHolder.getUser();
        assertEquals(42L, loginUser.getId());
        assertEquals("user", loginUser.getUserRole());
        verify(stringRedisTemplate).expire(
                LOGIN_USER_KEY + "session-123",
                LOGIN_USER_TTL,
                TimeUnit.MINUTES
        );
    }

    @Test
    void skipsRedisWhenAuthorizationHeaderIsMissing() throws Exception {
        boolean result = interceptor.preHandle(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                new Object()
        );

        assertTrue(result);
        verify(stringRedisTemplate, never()).opsForHash();
    }
}
