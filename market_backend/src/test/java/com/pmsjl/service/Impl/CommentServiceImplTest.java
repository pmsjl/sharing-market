package com.pmsjl.service.Impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.controller.CommentController;
import com.pmsjl.exception.GlobalExceptionHandler;
import com.pmsjl.mapper.CommentMapper;
import com.pmsjl.mapper.UserMapper;
import com.pmsjl.model.entity.Post;
import com.pmsjl.model.entity.User;
import com.pmsjl.service.PostService;
import com.pmsjl.service.UserService;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MyBatis SQL and HTTP business responses without external MySQL or Redis. */
class CommentServiceImplTest {
    private SqlSession session;
    private CommentServiceImpl comments;
    private UserService users;
    private User me;
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setup() throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver", "jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("""
                    CREATE TABLE comment (id BIGINT PRIMARY KEY, postId BIGINT NOT NULL, userId BIGINT NOT NULL,
                    content TEXT NOT NULL, parentId BIGINT, ancestorId BIGINT,
                    createTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updateTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    isDelete INT DEFAULT 0)
                    """);
            sql.execute("""
                    CREATE TABLE user (id BIGINT PRIMARY KEY, userAccount VARCHAR(50), userPassword VARCHAR(50),
                    userName VARCHAR(50), userAvatar VARCHAR(50), userProfile VARCHAR(50), userRole VARCHAR(20),
                    userPhone VARCHAR(50), balance DECIMAL, createTime TIMESTAMP, updateTime TIMESTAMP,
                    isDelete INT DEFAULT 0)
                    """);
            sql.execute("INSERT INTO user(id,userName,userRole) VALUES (1,'author','user'),(2,'reader','user')");
        }
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(false);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        config.addMapper(CommentMapper.class);
        config.addMapper(UserMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);

        me = new User();
        me.setId(1L);
        me.setUserRole("user");
        users = mock(UserService.class);
        when(users.getLoginUser()).thenReturn(me);
        when(users.lambdaQuery()).thenAnswer(invocation -> new LambdaQueryChainWrapper<>(session.getMapper(UserMapper.class)));
        var posts = mock(PostService.class);
        for (long id : new long[]{7L, 8L}) {
            var post = new Post();
            post.setId(id);
            when(posts.getById(id)).thenReturn(post);
        }
        comments = new CommentServiceImpl();
        ReflectionTestUtils.setField(comments, "baseMapper", session.getMapper(CommentMapper.class));
        ReflectionTestUtils.setField(comments, "userService", users);
        ReflectionTestUtils.setField(comments, "postService", posts);
        var controller = new CommentController();
        ReflectionTestUtils.setField(controller, "commentService", comments);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @AfterEach
    void close() throws Exception {
        if (session != null) {
            try (var sql = session.getConnection().createStatement()) {
                sql.execute("DROP ALL OBJECTS");
            } finally {
                session.close();
            }
        }
    }

    @Test
    void emptyCommentsReturnEmptyArrayWithoutQueryingUsers() throws Exception {
        mvc.perform(get("/comment/get/questonComment").param("postId", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray()).andExpect(jsonPath("$.data").isEmpty());
        verifyNoInteractions(users);
    }

    @Test
    void firstCommentPersistsAndIsReadableThroughHttp() throws Exception {
        long id = add(7L, "这篇经验很有帮助", null);
        var stored = comments.getById(id);
        assertEquals(1L, stored.getUserId());
        assertEquals("这篇经验很有帮助", stored.getContent());
        assertNull(stored.getAncestorId());
        mvc.perform(get("/comment/get/questonComment").param("postId", "7"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].content").value("这篇经验很有帮助"))
                .andExpect(jsonPath("$.data[0].user.userName").value("author"));
    }

    @Test
    void consecutiveCommentsSucceed() throws Exception {
        long first = add(7L, "感谢分享", null);
        long second = add(7L, "已了解", null);
        assertNotEquals(first, second);
        assertEquals(2, comments.getCommentsByPostId(7L, null).size());
    }

    @Test
    void nestedRepliesKeepRootAncestorAndAssembleUsers() throws Exception {
        long root = add(7L, "分享经验", null);
        var reader = new User();
        reader.setId(2L);
        when(users.getLoginUser()).thenReturn(reader);
        long reply = add(7L, "感谢分享", root);
        when(users.getLoginUser()).thenReturn(me);
        long nested = add(7L, "已了解", reply);
        assertEquals(root, comments.getById(reply).getAncestorId());
        assertEquals(root, comments.getById(nested).getAncestorId());
        var tree = comments.getCommentsByPostId(7L, null);
        assertEquals(1, tree.size());
        var child = tree.get(0).getReplies().get(0);
        assertEquals(reply, child.getId());
        assertEquals(2L, child.getUser().getId());
        assertEquals(1L, child.getRepliedUser().getId());
        assertEquals(nested, child.getReplies().get(0).getId());
        assertEquals(2L, child.getReplies().get(0).getRepliedUser().getId());
    }

    @Test
    void deletingRootLogicallyDeletesDescendantsButKeepsOtherRoots() throws Exception {
        long root = add(7L, "分享经验", null);
        long reply = add(7L, "感谢分享", root);
        long nested = add(7L, "已了解", reply);
        long other = add(7L, "另一条经验", null);
        var request = new DeleteRequest();
        request.setId(root);
        assertTrue(comments.deleteComment(request, null));
        assertNull(comments.getById(root));
        assertNull(comments.getById(reply));
        assertNull(comments.getById(nested));
        assertNotNull(comments.getById(other));
        assertEquals(1, comments.getCommentsByPostId(7L, null).size());
        try (var sql = session.getConnection().createStatement();
             var rows = sql.executeQuery("SELECT COUNT(*) FROM comment WHERE isDelete=1")) {
            assertTrue(rows.next());
            assertEquals(3, rows.getInt(1));
        }
    }

    @Test
    void deletingLastCommentReturnsToEmptyListWithoutQueryingUsers() throws Exception {
        long root = add(7L, "分享经验", null);
        var request = new DeleteRequest();
        request.setId(root);
        assertTrue(comments.deleteComment(request, null));
        clearInvocations(users);
        assertTrue(comments.getCommentsByPostId(7L, null).isEmpty());
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    void blankContentKeepsBusinessError(String content) throws Exception {
        assertRejected(7L, content, null, ErrorCode.OPERATION_ERROR);
    }

    @Test
    void excessiveContentKeepsParameterError() throws Exception {
        assertRejected(7L, "x".repeat(1025), null, ErrorCode.PARAMS_ERROR);
    }

    @Test
    void forbiddenContentKeepsBusinessErrorAndDoesNotPersist() throws Exception {
        String word;
        try (var input = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("forbiddenWords.txt"))) {
            word = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(line -> !line.isEmpty()).findFirst().orElseThrow();
        }
        assertRejected(7L, word, null, ErrorCode.WORD_FORBIDDEN_ERROR);
    }

    @Test
    void missingPostKeepsBusinessError() throws Exception {
        assertRejected(999L, "分享经验", null, ErrorCode.OPERATION_ERROR);
    }

    @Test
    void missingParentKeepsNotFoundError() throws Exception {
        assertRejected(7L, "分享经验", 999L, ErrorCode.NOT_FOUND_ERROR);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void invalidParentKeepsParameterError(long parent) throws Exception {
        assertRejected(7L, "分享经验", parent, ErrorCode.PARAMS_ERROR);
    }

    @Test
    void crossPostReplyKeepsParameterError() throws Exception {
        long root = add(7L, "分享经验", null);
        assertRejected(8L, "感谢分享", root, ErrorCode.PARAMS_ERROR);
    }

    private long add(long postId, String content, Long parentId) throws Exception {
        JsonNode response = submit(postId, content, parentId);
        assertEquals(200, response.path("code").asInt());
        assertTrue(response.path("data").asLong() > 0);
        return response.path("data").asLong();
    }

    private void assertRejected(long postId, String content, Long parentId, ErrorCode error) throws Exception {
        long count = comments.count();
        assertEquals(error.getCode(), submit(postId, content, parentId).path("code").asInt());
        assertEquals(count, comments.count());
    }

    private JsonNode submit(long postId, String content, Long parentId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        // Match the frontend's string IDs as well as its request and response contracts.
        body.put("postId", Long.toString(postId));
        body.put("content", content);
        if (parentId != null) body.put("parentId", Long.toString(parentId));
        var result = mvc.perform(post("/comment/add").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray());
    }
}
