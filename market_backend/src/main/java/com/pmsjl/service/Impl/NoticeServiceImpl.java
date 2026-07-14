package com.pmsjl.service.Impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.notice.NoticeAddRequest;
import com.pmsjl.model.dto.notice.NoticeQueryRequest;
import com.pmsjl.model.dto.notice.NoticeUpdateRequest;
import com.pmsjl.model.entity.Notice;
import com.pmsjl.mapper.NoticeMapper;
import com.pmsjl.model.entity.User;
import com.pmsjl.model.vo.NoticeVO;
import com.pmsjl.model.vo.UserVO;
import com.pmsjl.service.NoticeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pmsjl.service.UserService;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.pmsjl.constant.RedisConstant.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author pmsjl
 * @since 2026-06-09
 */
@Service
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements NoticeService {
    private static final Set<String> ALLOWED_NOTICE_SORT_FIELDS = Set.of("id", "createTime", "updateTime");

    @Autowired
    UserService userService;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Qualifier("jacksonObjectMapper")
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Long addNotice(NoticeAddRequest noticeAddRequest, HttpServletRequest request) {
        Notice notice = new Notice();
        BeanUtils.copyProperties(noticeAddRequest, notice);
        User loginUser = userService.getLoginUser(request);
        notice.setNoticeAdminId(loginUser.getId());
        validNotice(notice);
        boolean result = save(notice);
        ThrowUtils.throwIf(result == false, ErrorCode.OPERATION_ERROR);
        stringRedisTemplate.delete(CACHE_NOTICE_LIST_VO_PAGE_KEY);
        return notice.getId();

    }

    @Override
    public void validNotice(Notice notice) {
        ThrowUtils.throwIf(notice == null, ErrorCode.PARAMS_ERROR);
        String noticeTitle = notice.getNoticeTitle();
        String noticeContent = notice.getNoticeContent();
        Long noticeAdminId = notice.getNoticeAdminId();
        // 修改数据时，有参数则校验
        ThrowUtils.throwIf(StringUtils.isBlank(noticeTitle), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(StringUtils.isBlank(noticeContent), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(noticeAdminId == null || noticeAdminId <= 0, ErrorCode.PARAMS_ERROR);
    }

    @Override
    public Boolean deleteNotice(DeleteRequest deleteRequest, HttpServletRequest request) {
        Long id = deleteRequest.getId();
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        boolean result = removeById(id);
        ThrowUtils.throwIf(result == false, ErrorCode.OPERATION_ERROR);
        stringRedisTemplate.delete(CACHE_NOTICE_LIST_VO_PAGE_KEY);
        stringRedisTemplate.delete(CACHE_NOTICE_VO_KEY+id);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateNotice(NoticeUpdateRequest noticeUpdateRequest, HttpServletRequest request) {
        // 判断是否存在
        long id = noticeUpdateRequest.getId();
        Notice oldNotice = getById(id);
        ThrowUtils.throwIf(oldNotice == null, ErrorCode.NOT_FOUND_ERROR);
        Notice notice = new Notice();
        BeanUtils.copyProperties(noticeUpdateRequest, notice);
        notice.setNoticeAdminId(oldNotice.getNoticeAdminId());
        validNotice(notice);
        boolean result = updateById(notice);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        stringRedisTemplate.delete(CACHE_NOTICE_VO_KEY+id);
        stringRedisTemplate.delete(CACHE_NOTICE_LIST_VO_PAGE_KEY);
        return result;

    }

    @SneakyThrows
    @Override
    public NoticeVO getNoticeVO(long id, HttpServletRequest request) {
        String noticeVOJson = stringRedisTemplate.opsForValue().get(CACHE_NOTICE_VO_KEY + id);
        if (noticeVOJson != null && noticeVOJson.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "公告不存在");
        }
        if (noticeVOJson == null) {
            Notice notice = getById(id);
            if (notice == null) {
                stringRedisTemplate.opsForValue().set(CACHE_NOTICE_VO_KEY + id, "", 5, TimeUnit.MINUTES);
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "公告不存在");
            } else {
                NoticeVO noticeVO = new NoticeVO();
                BeanUtils.copyProperties(notice, noticeVO);
                Long noticeAdminId = notice.getNoticeAdminId();
                if (noticeAdminId != null && noticeAdminId > 0) {
                    User user = userService.getById(noticeAdminId);
                    fillNoticeUser(noticeVO, user);
                }
                String voString = objectMapper.writeValueAsString(noticeVO);
                stringRedisTemplate.opsForValue().set(CACHE_NOTICE_VO_KEY + id, voString);
                return noticeVO;
            }
        } else {
            NoticeVO noticeVO = objectMapper.readValue(noticeVOJson, NoticeVO.class);
            return noticeVO;
        }
    }

    private Page<Notice> listNoticeByPage(NoticeQueryRequest noticeQueryRequest) {
        ThrowUtils.throwIf(noticeQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int current = noticeQueryRequest.getCurrent();
        int pageSize = noticeQueryRequest.getPageSize();
        Long id = noticeQueryRequest.getId();
        String noticeTitle = noticeQueryRequest.getNoticeTitle();
        String noticeContent = noticeQueryRequest.getNoticeContent();
        Long noticeAdminId = noticeQueryRequest.getNoticeAdminId();
        String sortField = noticeQueryRequest.getSortField();
        String sortOrder = noticeQueryRequest.getSortOrder();

        if (current <= 0) current = 1;
        if (pageSize <= 0 || pageSize > 100) pageSize = 10;
        Page<Notice> page = new Page<>(current, pageSize);
        if (StringUtils.isNotBlank(sortField) && ALLOWED_NOTICE_SORT_FIELDS.contains(sortField)) {
            if ("asc".equalsIgnoreCase(sortOrder)) {
                page.addOrder(OrderItem.asc(sortField));
            } else {
                page.addOrder(OrderItem.desc(sortField));
            }
        } else {
            page.addOrder(OrderItem.desc("createTime"));
        }

        return lambdaQuery()
                .eq(ObjectUtils.isNotEmpty(id), Notice::getId, id)
                .like(StringUtils.isNotBlank(noticeTitle), Notice::getNoticeTitle, noticeTitle)
                .like(StringUtils.isNotBlank(noticeContent), Notice::getNoticeContent, noticeContent)
                .eq(ObjectUtils.isNotEmpty(noticeAdminId), Notice::getNoticeAdminId, noticeAdminId)
                .page(page);
    }

    @Override
    @SneakyThrows
    public Page<NoticeVO> listNoticeVOByPage(NoticeQueryRequest noticeQueryRequest, HttpServletRequest request) {
        boolean isHotUserNoticePage =
                noticeQueryRequest.getCurrent() == 1
                        && noticeQueryRequest.getPageSize() == 15
                        && noticeQueryRequest.getId() == null
                        && StringUtils.isBlank(noticeQueryRequest.getNoticeTitle())
                        && StringUtils.isBlank(noticeQueryRequest.getNoticeContent())
                        && noticeQueryRequest.getNoticeAdminId() == null
                        && StringUtils.isBlank(noticeQueryRequest.getSortField());
        if (isHotUserNoticePage) {
            String voListPage = stringRedisTemplate.opsForValue().get(CACHE_NOTICE_LIST_VO_PAGE_KEY);
            if (voListPage != null && !voListPage.isEmpty()) {
                Page<NoticeVO> page = objectMapper.readValue(voListPage, new TypeReference<Page<NoticeVO>>() {
                });
                return page;
            }
        }
        Page<Notice> noticePage = listNoticeByPage(noticeQueryRequest);
        long current = noticePage.getCurrent();
        long sizes = noticePage.getSize();
        long total = noticePage.getTotal();
        List<Notice> records = noticePage.getRecords();
        Page<NoticeVO> page = new Page<>(current, sizes, total);
        if (records == null || records.isEmpty()) {
            page.setRecords(List.of());
            return page;
        }
        List<NoticeVO> list = records.stream().map(this::getNoticeVO).toList();
        Set<Long> adminIdList = records.stream().map(Notice::getNoticeAdminId).filter(ObjectUtils::isNotEmpty).
                collect(Collectors.toSet());
        //这里其实是和favorite问题类似的，这里的adminId如果不存在或者查找不到对应的user，就会导致records和total数量不一致
        //那这里需要联表查询吗，不一定要，我们前端原本是没有user VO的呈现的，我们是自己优化加上去这个数据的呈现的
        //所以他是选择性的是可以没有的，那么这样的话没有就没有当作null就好了，就不会出现问题了
        if (!adminIdList.isEmpty()) {
            Map<Long, User> userMap = userService.listByIds(adminIdList).stream().collect(Collectors.toMap(User::getId, user -> user));
            list.forEach(noticeVO -> {
                User user = userMap.get(noticeVO.getNoticeAdminId());
                fillNoticeUser(noticeVO, user);
                //没有user就直接过不用管，有了才设置
            });
        }
        page.setRecords(list);

        if (isHotUserNoticePage) {
            String pageJson = objectMapper.writeValueAsString(page);
            stringRedisTemplate.opsForValue().set(CACHE_NOTICE_LIST_VO_PAGE_KEY, pageJson,15,TimeUnit.MINUTES);
        }

        return page;


    }


    private NoticeVO getNoticeVO(Notice notice) {
        ThrowUtils.throwIf(notice == null, ErrorCode.PARAMS_ERROR);
        NoticeVO noticeVO = new NoticeVO();
        BeanUtils.copyProperties(notice, noticeVO);
        return noticeVO;
    }

    private void fillNoticeUser(NoticeVO noticeVO, User user) {
        if (noticeVO == null || user == null) {
            return;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        noticeVO.setUser(userVO);
    }


}
