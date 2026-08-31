package com.pmsjl.service.Impl;

import com.pmsjl.model.dto.user.UserQueryRequest;
import com.pmsjl.model.dto.user.UserUpdateRequest;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.UserVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceImplTest {

    @Test
    @SuppressWarnings("unchecked")
    void userSortWhitelistMatchesCurrentUserContract() {
        Set<String> sortFields = (Set<String>) ReflectionTestUtils.getField(
                UserServiceImpl.class, "ALLOWED_USER_SORT_FIELDS");

        assertNotNull(sortFields);
        assertEquals(Set.of(
                "id", "userName", "userRole", "balance",
                "editTime", "createTime", "updateTime"), sortFields);
    }

    @Test
    void balanceRemainsInternalAndCannotBeSelfUpdatedOrPubliclyExposed() throws NoSuchFieldException {
        for (Class<?> modelClass : Set.of(User.class, UserQueryRequest.class)) {
            assertNotNull(modelClass.getDeclaredField("balance"));
        }
        assertThrows(NoSuchFieldException.class,
                () -> UserUpdateRequest.class.getDeclaredField("balance"));
        assertThrows(NoSuchFieldException.class,
                () -> UserVO.class.getDeclaredField("balance"));
    }
}
