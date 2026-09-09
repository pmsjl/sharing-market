package com.pmsjl.mapper;

import com.pmsjl.model.entity.PrivateMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pmsjl.model.vo.PrivateConversationVO;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author pmsjl
 * @since 2026-07-08
 */
public interface PrivateMessageMapper extends BaseMapper<PrivateMessage> {
    long countMyConversations(@Param("userId") Long userId);
    List<PrivateConversationVO> listMyConversations(@Param("userId") Long userId,
            @Param("offset") long offset, @Param("pageSize") int pageSize);
}
