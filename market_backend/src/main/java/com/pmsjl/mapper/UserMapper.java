package com.pmsjl.mapper;

import com.pmsjl.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 用户 Mapper 接口
 * </p>
 *
 * @author pmsjl
 * @since 2026-04-04
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM `user` WHERE id = #{id} AND isDelete = 0 FOR UPDATE")
    User selectByIdForUpdate(@Param("id") Long id);
}
