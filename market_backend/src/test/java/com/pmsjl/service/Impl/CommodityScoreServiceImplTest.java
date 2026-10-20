package com.pmsjl.service.Impl;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.mapper.CommodityScoreMapper;
import com.pmsjl.model.dto.commodityScore.CommodityScoreQueryRequest;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.entity.CommodityScore;
import com.pmsjl.model.entity.User;
import com.pmsjl.service.CommodityService;
import com.pmsjl.service.UserService;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises inserts, unique constraints, personal queries and averages with real SQL. */
class CommodityScoreServiceImplTest {
    private UnpooledDataSource source;
    private CommodityScoreServiceImpl scores;
    private Commodity commodity;

    @BeforeEach
    void setup() throws Exception {
        source = new UnpooledDataSource("org.h2.Driver",
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        try (var connection = source.getConnection(); var sql = connection.createStatement()) {
            sql.execute("""
                    CREATE TABLE commodity_score (
                        id BIGINT PRIMARY KEY, commodityId BIGINT NOT NULL, userId BIGINT NOT NULL,
                        score INT NOT NULL, createTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        updateTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, isDelete INT DEFAULT 0,
                        CONSTRAINT scoreId UNIQUE (commodityId, userId))
                    """);
        }
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(false);
        config.setEnvironment(new Environment("test", new SpringManagedTransactionFactory(), source));
        var pagination = new MybatisPlusInterceptor();
        pagination.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
        config.addInterceptor(pagination);
        config.addMapper(CommodityScoreMapper.class);
        String resource = "com/pmsjl/mapper/CommodityScoreMapper.xml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream);
            new XMLMapperBuilder(stream, config, resource, config.getSqlFragments()).parse();
        }
        var session = new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(config));
        var mapper = session.getMapper(CommodityScoreMapper.class);
        var user = new User();
        user.setId(1L);
        var users = mock(UserService.class);
        when(users.getLoginUser()).thenReturn(user);
        when(users.listByIds(any())).thenReturn(List.of(user));
        commodity = new Commodity();
        commodity.setId(7L);
        commodity.setAdminId(2L);
        commodity.setIsListed(1);
        commodity.setIsDelete(0);
        var commodities = mock(CommodityService.class);
        when(commodities.getById(7L)).thenReturn(commodity);
        scores = new CommodityScoreServiceImpl();
        ReflectionTestUtils.setField(scores, "baseMapper", mapper);
        ReflectionTestUtils.setField(scores, "commodityScoreMapper", mapper);
        ReflectionTestUtils.setField(scores, "userService", users);
        ReflectionTestUtils.setField(scores, "commodityService", commodities);
    }

    @AfterEach
    void close() throws Exception {
        if (source != null) {
            try (var connection = source.getConnection(); var sql = connection.createStatement()) {
                sql.execute("DROP ALL OBJECTS");
            }
        }
    }

    private CommodityScore rating(int score) {
        var rating = new CommodityScore();
        rating.setCommodityId(7L);
        rating.setScore(score);
        rating.setUserId(999L); // The authenticated user must replace supplied identity.
        return rating;
    }

    @Test
    void successfulRatingCanBeQueriedAndContributesToAverage() {
        Long id = scores.addCommodityScore(rating(4), null);
        var query = new CommodityScoreQueryRequest();
        query.setCommodityId(7L);
        query.setUserId(999L);
        var page = scores.listMyCommodityScoreVOByPage(query, null);
        assertEquals(1, page.getTotal());
        assertEquals(id, page.getRecords().get(0).getId());
        assertEquals(1L, page.getRecords().get(0).getUserId());
        assertEquals(4, page.getRecords().get(0).getScore());
        assertEquals(4.0, scores.getAverageScoreById(7L));
    }

    @Test
    void duplicateRatingReturnsConflictAndPreservesOriginalScore() {
        scores.addCommodityScore(rating(3), null);
        var error = assertThrows(BusinessException.class,
                () -> scores.addCommodityScore(rating(5), null));
        assertEquals(ErrorCode.CONFLICT_ERROR.getCode(), error.getCode());
        assertEquals("您已评分，不可重复评分", error.getMessage());
        assertEquals(1, scores.count());
        assertEquals(3.0, scores.getAverageScoreById(7L));
    }

    @Test
    void ownAndUnlistedProductsReturnSpecificReasonsWithoutSaving() {
        commodity.setAdminId(1L);
        assertEquals("不能评价自己的商品", assertThrows(BusinessException.class,
                () -> scores.addCommodityScore(rating(5), null)).getMessage());
        commodity.setAdminId(2L);
        commodity.setIsListed(0);
        assertEquals("商品未上架，不能评价", assertThrows(BusinessException.class,
                () -> scores.addCommodityScore(rating(5), null)).getMessage());
        assertEquals(0, scores.count());
    }

    @Test
    void invalidScoresAndMissingProductsDoNotSave() {
        for (int score : new int[]{0, 6}) {
            assertEquals(ErrorCode.PARAMS_ERROR.getCode(), assertThrows(BusinessException.class,
                    () -> scores.addCommodityScore(rating(score), null)).getCode());
        }
        var missing = rating(5);
        missing.setCommodityId(8L);
        assertEquals("商品不存在", assertThrows(BusinessException.class,
                () -> scores.addCommodityScore(missing, null)).getMessage());
        assertEquals(0, scores.count());
    }
}
