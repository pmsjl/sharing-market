package com.pmsjl.utils;

import com.pmsjl.common.AuthTokenProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TokenUtilsTest {

    @BeforeEach
    void setUp() {
        new TokenUtils().setAuthTokenProperties(
                new AuthTokenProperties("Authorization", "Bearer")
        );
    }

    @Test
    void extractsRedisSessionTokenFromBearerHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer redis-session-token");

        assertEquals("redis-session-token", TokenUtils.getToken(request));
    }

    @Test
    void returnsNullWhenRequestOrHeaderIsMissing() {
        assertNull(TokenUtils.getToken(null));
        assertNull(TokenUtils.getToken(new MockHttpServletRequest()));
    }

    @Test
    void returnsNullWhenHeaderPrefixDoesNotMatch() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic redis-session-token");

        assertNull(TokenUtils.getToken(request));
    }
}
