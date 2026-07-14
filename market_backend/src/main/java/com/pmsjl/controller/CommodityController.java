package com.pmsjl.controller;
import static com.pmsjl.constant.RedisConstant.*;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pmsjl.annotation.AuthCheck;
import com.pmsjl.common.DeleteRequest;
import com.pmsjl.common.ErrorCode;
import com.pmsjl.common.Result;
import com.pmsjl.constant.UserConstant;
import com.pmsjl.exception.BusinessException;
import com.pmsjl.model.dto.commodity.BuyCommodityRequest;
import com.pmsjl.model.dto.commodity.CommodityAddRequest;
import com.pmsjl.model.dto.commodity.CommodityQueryRequest;
import com.pmsjl.model.dto.commodity.CommodityUpdateRequest;
import com.pmsjl.model.dto.commodityOrder.PayCommodityOrderRequest;
import com.pmsjl.model.entity.Commodity;
import com.pmsjl.model.vo.CommodityVO;
import com.pmsjl.service.CommodityService;
import com.pmsjl.utils.ResultUtils;
import com.pmsjl.utils.ThrowUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author pmsjl
 * @since 2026-04-07
 */
@RestController
@RequestMapping("/commodity")
public class CommodityController {
    @Autowired
    private CommodityService commodityService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /***
     * 添加商品
     * @param commodityAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public Result<Long> addCommodity(@RequestBody CommodityAddRequest commodityAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(commodityAddRequest == null, ErrorCode.PARAMS_ERROR);
        Commodity commodity = new Commodity();
        BeanUtils.copyProperties(commodityAddRequest, commodity);
        Long id = commodityService.addCommodity(commodity, request);
        return ResultUtils.success(id);
    }


    /***
     * 这里就是普通用户购买商品进行订单创建的接口
     * @param buyCommodityRequest
     * @param request
     * @return
     */
    @PostMapping("/buy")
    public Result<Map<String, Object>> buyCommodity(@RequestBody BuyCommodityRequest buyCommodityRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(buyCommodityRequest == null, ErrorCode.PARAMS_ERROR, "购买参数不能为空");
        Map<String, Object> result = commodityService.buyCommodity(buyCommodityRequest, request);
        return ResultUtils.success(result);
    }


    @PostMapping("/pay")
    public Result<Boolean>payCommodity(@RequestBody PayCommodityOrderRequest payRequest, HttpServletRequest request){
        ThrowUtils.throwIf(payRequest == null, ErrorCode.PARAMS_ERROR, "购买参数不能为空");
        Boolean result=commodityService.payCommodity(payRequest,request);
        return ResultUtils.success(result);
    }
    /***
     * //TODO 这里原本采取了自定义isAdmin方法，为什么不用注解我不理解
     * 删除商品,仅管理员可删除
     * @param deleteRequest
     * @return
     */
    @PostMapping("/delete")
    public Result<Boolean> deleteCommodity(@RequestBody DeleteRequest deleteRequest,HttpServletRequest request) {
        Long id = deleteRequest.getId();
        ThrowUtils.throwIf(id == null || id < 0, ErrorCode.PARAMS_ERROR);
        Boolean result = commodityService.deleteCommodity(id,request);
        ThrowUtils.throwIf(result==false,ErrorCode.OPERATION_ERROR);

        return ResultUtils.success(result);

    }

    /***
     * 管理员可更新商品所有信息
     * @param commodityUpdateRequest
     * @return
     */

    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @PostMapping("/update")
    public Result<Boolean> updateCommodity(@RequestBody CommodityUpdateRequest commodityUpdateRequest) {
        if (ObjectUtils.anyNull(commodityUpdateRequest, commodityUpdateRequest.getId()) || commodityUpdateRequest.getId() < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Commodity commodity = new Commodity();
        BeanUtils.copyProperties(commodityUpdateRequest, commodity);
        Boolean result = commodityService.updateCommodity(commodity);
        ThrowUtils.throwIf(result==false,ErrorCode.OPERATION_ERROR);
        Long id = commodity.getId();
        stringRedisTemplate.delete(CACHE_COMMODITY_KEY+id);
        return ResultUtils.success(result);

    }


    /***
     * 用户可更新商品的部分信息
     * @param commodityEditRequest
     * @return
     */
    /***
     * 根据id查询商品
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public Result<CommodityVO> getCommodityVOById(@RequestParam("id") Long id,HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id < 0, ErrorCode.PARAMS_ERROR);
        CommodityVO commodityVO = commodityService.getCommodityVOById(id,request);
        return ResultUtils.success(commodityVO);
    }

    /***
     * 管理员分页查询商品
     * @param commodityQueryRequest
     * @return
     */
    @PostMapping("/list/page/vo")
    public Result<Page<CommodityVO>>listCommodityVOByPage(@RequestBody CommodityQueryRequest commodityQueryRequest){
        Page<CommodityVO>page=commodityService.listCommodityVOByPage(commodityQueryRequest);
        return ResultUtils.success(page);
    }

    @PostMapping("my/list/page/vo")
    public Result<Page<CommodityVO>>listMyCommodityVOByPage(@RequestBody CommodityQueryRequest commodityQueryRequest,HttpServletRequest request){
        Page<CommodityVO>page=commodityService.listMyCommodityVOByPage(commodityQueryRequest,request);
        return ResultUtils.success(page);
    }






}
