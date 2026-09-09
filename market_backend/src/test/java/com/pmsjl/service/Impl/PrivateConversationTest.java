package com.pmsjl.service.Impl;

import com.pmsjl.common.PageRequest;
import com.pmsjl.mapper.PrivateMessageMapper;
import com.pmsjl.model.entity.User;
import com.pmsjl.service.UserService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Connection;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes the real mapper SQL against an isolated database, without application services. */
class PrivateConversationTest {
    private SqlSession session;
    private PrivateMessageMapper mapper;

    @BeforeEach
    void setup() throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE private_message (id BIGINT PRIMARY KEY, senderId BIGINT, recipientId BIGINT, content VARCHAR(4096), createTime TIMESTAMP, isDelete INT)");
            sql.execute("CREATE TABLE user (id BIGINT PRIMARY KEY, userName VARCHAR(255), userAvatar VARCHAR(255), isDelete INT)");
            sql.execute("INSERT INTO user VALUES (1, 'me', '', 0), (2, 'Alice', '', 0), (3, 'Bob', '', 0), (4, 'deleted', '', 1)");
            sql.execute("INSERT INTO private_message VALUES " +
                    "(10, 2, 1, 'incoming', '2026-01-01 10:00:00', 0)," +
                    "(11, 1, 2, 'reply', '2026-01-01 11:00:00', 0)," +
                    "(12, 1, 3, 'outgoing only', '2026-01-01 12:00:00', 0)," +
                    "(13, 2, 3, 'not mine', '2026-01-01 13:00:00', 0)," +
                    "(14, 4, 1, 'old contact', '2025-01-01 00:00:00', 0)," +
                    "(15, 5, 1, 'deleted message', '2026-01-01 14:00:00', 1)," +
                    "(16, 1, 1, 'self legacy', '2026-01-01 15:00:00', 0)");
        }
        var config = new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        String resource = "com/pmsjl/mapper/PrivateMessageMapper.xml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream);
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        session = new SqlSessionFactoryBuilder().build(config).openSession();
        mapper = session.getMapper(PrivateMessageMapper.class);
    }

    @AfterEach
    void close() throws Exception {
        if (session != null) {
            session.getConnection().createStatement().execute("DROP ALL OBJECTS");
            session.close();
        }
    }

    @Test
    void includesBothDirectionsAndSeparatesIncomingFromOwnReply() {
        assertEquals(3, mapper.countMyConversations(1L));
        var rows = mapper.listMyConversations(1L, 0, 20);
        assertEquals(3L, rows.get(0).getContactUserId());
        assertNull(rows.get(0).getLastReceivedMessageId());
        assertEquals("reply", rows.get(1).getLastMessageContent());
        assertEquals(10L, rows.get(1).getLastReceivedMessageId());
        assertEquals("Alice", rows.get(1).getUserName());
        assertEquals("对方用户", rows.get(2).getUserName());
    }

    @Test
    void paginatesContactsRatherThanRecentMessagesAndHasDeterministicTies() throws Exception {
        session.getConnection().createStatement().execute(
                "INSERT INTO private_message VALUES (17, 2, 1, 'same time newer id', '2026-01-01 12:00:00', 0)");
        var first = mapper.listMyConversations(1L, 0, 1);
        assertEquals(2L, first.get(0).getContactUserId());
        assertEquals(17L, first.get(0).getLastMessageId());
        assertEquals(4L, mapper.listMyConversations(1L, 2, 1).get(0).getContactUserId());
        assertTrue(mapper.listMyConversations(1L, 3, 1).isEmpty());
    }

    @Test
    void isolatesUsersAndIgnoresDeletedMessages() {
        assertEquals(0, mapper.countMyConversations(99L));
        assertTrue(mapper.listMyConversations(99L, 0, 20).isEmpty());
        var rows = mapper.listMyConversations(3L, 0, 20);
        assertEquals(2, rows.size());
        assertEquals("not mine", rows.get(0).getLastMessageContent());
        assertEquals(2L, rows.get(0).getContactUserId());
    }

    @Test
    void serviceUsesAuthenticatedUserAndBoundsPagination() {
        var service = new PrivateMessageServiceImpl();
        var users = mock(UserService.class);
        var user = new User();
        user.setId(1L);
        when(users.getLoginUser()).thenReturn(user);
        ReflectionTestUtils.setField(service, "userService", users);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        var query = new PageRequest();
        query.setCurrent(-1);
        query.setPageSize(10000);
        query.setSortField("untrusted SQL");
        var page = service.listMyConversations(query);
        assertEquals(1, page.getCurrent());
        assertEquals(100, page.getSize());
        assertEquals(3, page.getTotal());
        assertEquals(3, page.getRecords().size());
        query.setCurrent(Integer.MAX_VALUE);
        assertTrue(service.listMyConversations(query).getRecords().isEmpty());
        assertThrows(RuntimeException.class, () -> service.listMyConversations(null));
    }
}
