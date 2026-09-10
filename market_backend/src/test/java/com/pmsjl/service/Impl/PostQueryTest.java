package com.pmsjl.service.Impl;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.pmsjl.mapper.PostMapper;
import com.pmsjl.mapper.PostFavourMapper;
import com.pmsjl.mapper.PostThumbMapper;
import com.pmsjl.model.dto.post.PostAdminQueryRequest;
import com.pmsjl.model.dto.post.PostQueryRequest;
import com.pmsjl.model.dto.postfavour.PostFavourQueryRequest;
import com.pmsjl.model.entity.User;
import com.pmsjl.service.UserService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Exercises real query and pagination SQL without Redis or application startup. */
class PostQueryTest {
    private SqlSession session;
    private PostServiceImpl posts;
    private PostFavourServiceImpl favourites;

    @BeforeEach
    void setup() throws Exception {
        var source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE post (id BIGINT PRIMARY KEY, title VARCHAR(255), content VARCHAR(255), tags VARCHAR(255), userId BIGINT, thumbNum INT, favourNum INT, createTime TIMESTAMP, updateTime TIMESTAMP, isDelete INT)");
            sql.execute("CREATE TABLE post_thumb (id BIGINT PRIMARY KEY, userId BIGINT, postId BIGINT, createTime TIMESTAMP, updateTime TIMESTAMP)");
            sql.execute("INSERT INTO post_thumb VALUES (1, 1, 20, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), (2, 2, 40, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), (3, 1, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            sql.execute("CREATE TABLE post_favour (id BIGINT PRIMARY KEY, userId BIGINT, postId BIGINT, createTime TIMESTAMP, updateTime TIMESTAMP)");
            sql.execute("""
                    INSERT INTO post VALUES
                    (10, 'Java guide', 'basics', '["Java","Spring"]', 1, 0, 1, '2026-01-01', '2026-01-01', 0),
                    (20, 'Other guide', 'Java basics', '["JavaScript","Spring"]', 2, 0, 1, '2026-01-02', '2026-01-02', 0),
                    (30, 'Deleted guide', 'Java basics', '["Java","Spring"]', 1, 0, 1, '2026-01-03', '2026-01-03', 1),
                    (40, 'Java notes', 'advanced', '["Java"]', 2, 0, 1, '2026-01-04', '2026-01-04', 0)
                    """);
            sql.execute("""
                    INSERT INTO post_favour VALUES
                    (1, 1, 20, '2026-01-01', '2026-01-01'),
                    (2, 1, 30, '2026-01-02', '2026-01-02'),
                    (3, 2, 10, '2026-01-03', '2026-01-03'),
                    (4, 1, 40, '2026-01-04', '2026-01-04')
                    """);
        }
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(false);
        config.setEnvironment(new Environment("test", new JdbcTransactionFactory(), source));
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        config.addInterceptor(pagination);
        config.addMapper(PostMapper.class);
        config.addMapper(PostThumbMapper.class);
        String resource = "com/pmsjl/mapper/PostFavourMapper.xml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream);
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        session = new MybatisSqlSessionFactoryBuilder().build(config).openSession();
        var users = mock(UserService.class);
        var me = new User();
        me.setId(1L);
        when(users.getLoginUser()).thenReturn(me);
        when(users.listByIds(any())).thenReturn(List.of(me));
        posts = new PostServiceImpl();
        ReflectionTestUtils.setField(posts, "baseMapper", session.getMapper(PostMapper.class));
        ReflectionTestUtils.setField(posts, "userService", users);
        ReflectionTestUtils.setField(posts, "postThumbMapper", session.getMapper(PostThumbMapper.class));
        ReflectionTestUtils.setField(posts, "postFavourMapper", session.getMapper(PostFavourMapper.class));
        favourites = new PostFavourServiceImpl();
        ReflectionTestUtils.setField(favourites, "baseMapper", session.getMapper(PostFavourMapper.class));
        ReflectionTestUtils.setField(favourites, "userService", users);
        ReflectionTestUtils.setField(favourites, "postThumbMapper", session.getMapper(PostThumbMapper.class));
    }

    @AfterEach
    void close() throws Exception {
        if (session != null) {
            try (var sql = session.getConnection().createStatement()) {
                sql.execute("DROP ALL OBJECTS");
            }
            session.close();
        }
    }

    @Test
    void publicSearchMatchesTitleOrContentAndPaginates() {
        var query = new PostQueryRequest();
        query.setSearchText("Java");
        query.setPageSize(1);
        query.setCurrent(2);
        var page = posts.listPostVOByPage(query, null);
        assertEquals(3, page.getTotal());
        assertEquals(20L, page.getRecords().get(0).getId());
        assertTrue(page.getRecords().get(0).getHasFavour());
        var searchPage = posts.searchPostVOByPage(query, null);
        assertEquals(page.getTotal(), searchPage.getTotal());
        assertEquals(20L, searchPage.getRecords().get(0).getId());
    }

    @Test
    void myPostsRestrictsBothSearchBranchesToAuthenticatedAuthor() {
        var query = new PostQueryRequest();
        query.setSearchText("Java");
        var page = posts.listMyPostVOByPage(query, null);
        assertEquals(1, page.getTotal());
        assertEquals(10L, page.getRecords().get(0).getId());
    }

    @Test
    void adminFiltersAuthorContentAndAllWholeTags() {
        var query = new PostAdminQueryRequest();
        query.setTitle("guide");
        query.setContent("basics");
        query.setTags(List.of("Java", "Spring"));
        var page = posts.listPostByPage(query);
        assertEquals(1, page.getTotal());
        assertEquals(10L, page.getRecords().get(0).getId());
        query.setUserId(2L);
        assertEquals(0, posts.listPostByPage(query).getTotal());
        query.setTags(List.of("JavaScript"));
        assertEquals(20L, posts.listPostByPage(query).getRecords().get(0).getId());
    }

    @Test
    void favouritesUseAuthenticatedCollectorAndTitleOnlyWithAccurateTotal() {
        var query = new PostFavourQueryRequest();
        query.setPageSize(1);
        var page = favourites.listMyFavourPostByPage(query, null);
        assertEquals(2, page.getTotal());
        assertEquals(40L, page.getRecords().get(0).getId());
        query.setCurrent(2);
        assertEquals(20L, favourites.listMyFavourPostByPage(query, null).getRecords().get(0).getId());
        query.setCurrent(1);
        query.setTitle("Java");
        page = favourites.listMyFavourPostByPage(query, null);
        assertEquals(1, page.getTotal());
        assertEquals(40L, page.getRecords().get(0).getId());
    }

    @Test
    void allUserListsSupportCombinedTagsAndIgnoreBlankTags() {
        var query = new PostQueryRequest();
        query.setSearchText("Java");
        query.setTags(List.of("Java", "Spring"));
        var page = posts.listPostVOByPage(query, null);
        assertEquals(1, page.getTotal());
        assertEquals(10L, page.getRecords().get(0).getId());
        assertEquals(List.of("Java", "Spring"), page.getRecords().get(0).getTagList());
        assertEquals(1, posts.searchPostVOByPage(query, null).getTotal());
        assertEquals(1, posts.listMyPostVOByPage(query, null).getTotal());
        query.setTags(List.of("JavaScript"));
        assertEquals(20L, posts.listPostVOByPage(query, null).getRecords().get(0).getId());
        assertEquals(0, posts.listMyPostVOByPage(query, null).getTotal());
        query.setTags(java.util.Arrays.asList(null, "", " "));
        assertEquals(3, posts.listPostVOByPage(query, null).getTotal());
    }

    @Test
    void favouriteTagsFilterBeforePaginationAndKeepTitleConstraint() {
        var query = new PostFavourQueryRequest();
        query.setTags(List.of("Java"));
        query.setPageSize(1);
        var page = favourites.listMyFavourPostByPage(query, null);
        assertEquals(1, page.getTotal());
        assertEquals(40L, page.getRecords().get(0).getId());
        query.setTags(List.of("JavaScript", "Spring"));
        assertEquals(20L, favourites.listMyFavourPostByPage(query, null).getRecords().get(0).getId());
        query.setTitle("Java");
        assertEquals(0, favourites.listMyFavourPostByPage(query, null).getTotal());
        query.setTitle(null);
        query.setTags(java.util.Arrays.asList(null, "", " "));
        assertEquals(2, favourites.listMyFavourPostByPage(query, null).getTotal());
        query.setTags(List.of());
        assertEquals(2, favourites.listMyFavourPostByPage(query, null).getTotal());
    }

    @Test
    void favouritesReturnActualThumbStateAndParsedTagList() throws Exception {
        var page = favourites.listMyFavourPostByPage(new PostFavourQueryRequest(), null);
        var byId = page.getRecords().stream().collect(java.util.stream.Collectors.toMap(
                com.pmsjl.model.vo.PostVO::getId, post -> post));
        assertTrue(byId.get(20L).getHasThumb());
        // 用户 2 点赞过 40，但当前用户 1 没点赞，不能混用其他用户的记录。
        assertFalse(byId.get(40L).getHasThumb());
        assertTrue(byId.values().stream().allMatch(post -> post.getHasFavour()));
        assertEquals(List.of("JavaScript", "Spring"), byId.get(20L).getTagList());
        assertEquals(List.of("Java"), byId.get(40L).getTagList());
        try (var sql = session.getConnection().createStatement()) {
            sql.execute("UPDATE post SET tags = '[]' WHERE id = 40");
        }
        session.clearCache();
        assertTrue(favourites.listMyFavourPostByPage(new PostFavourQueryRequest(), null)
                .getRecords().get(0).getTagList().isEmpty());
    }

    @Test
    void anyTagsRemainGroupedWithSearchAuthorAndRequiredTags() {
        var query = new PostQueryRequest();
        query.setOrTags(List.of("JavaScript", "Java"));
        assertEquals(3, posts.listPostVOByPage(query, null).getTotal());
        assertEquals(3, posts.searchPostVOByPage(query, null).getTotal());
        assertEquals(1, posts.listMyPostVOByPage(query, null).getTotal());
        query.setSearchText("advanced");
        assertEquals(1, posts.listPostVOByPage(query, null).getTotal());
        assertEquals(0, posts.listMyPostVOByPage(query, null).getTotal());
        query.setSearchText(null);
        query.setTags(List.of("Spring"));
        assertEquals(2, posts.listPostVOByPage(query, null).getTotal());

        var admin = new PostAdminQueryRequest();
        admin.setOrTags(List.of("Java", "JavaScript"));
        admin.setUserId(1L);
        assertEquals(1, posts.listPostByPage(admin).getTotal());
        admin.setContent("advanced");
        assertEquals(0, posts.listPostByPage(admin).getTotal());
    }

    @Test
    void favouriteAnyTagsRespectCollectorTitlePaginationAndBlankLists() {
        var query = new PostFavourQueryRequest();
        query.setOrTags(List.of("Java", "Spring"));
        query.setPageSize(1);
        var page = favourites.listMyFavourPostByPage(query, null);
        assertEquals(2, page.getTotal());
        assertEquals(40L, page.getRecords().get(0).getId());
        query.setTitle("Other");
        assertEquals(1, favourites.listMyFavourPostByPage(query, null).getTotal());
        query.setTags(List.of("Java"));
        assertEquals(0, favourites.listMyFavourPostByPage(query, null).getTotal());
        query.setTags(List.of());
        query.setTitle(null);
        query.setOrTags(java.util.Arrays.asList(null, "", " "));
        assertEquals(2, favourites.listMyFavourPostByPage(query, null).getTotal());
        query.setOrTags(List.of("Java"));
        assertEquals(1, favourites.listMyFavourPostByPage(query, null).getTotal());
        var ordinary = new PostQueryRequest();
        ordinary.setOrTags(java.util.Arrays.asList(null, "", " "));
        assertEquals(3, posts.listPostVOByPage(ordinary, null).getTotal());
    }
}
