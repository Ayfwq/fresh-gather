package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.FlashSaleRabbitConstant;
import com.sky.constant.FlashSaleRedisConstant;
import com.sky.context.BaseContext;
import com.sky.dto.FlashSaleActivityDTO;
import com.sky.dto.FlashSaleActivityPageQueryDTO;
import com.sky.dto.FlashSalePurchaseDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.Dish;
import com.sky.entity.FlashSaleActivity;
import com.sky.entity.FlashSaleOrder;
import com.sky.exception.FlashSaleBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.FlashSaleActivityMapper;
import com.sky.mapper.FlashSaleOrderMapper;
import com.sky.message.FlashSaleOrderMessage;
import com.sky.result.PageResult;
import com.sky.service.FlashSaleOrderService;
import com.sky.service.FlashSaleService;
import com.sky.vo.FlashSaleActivityVO;
import com.sky.vo.FlashSalePurchaseVO;
import com.sky.vo.FlashSaleResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 特卖活动服务。
 * <p>
 * 主要负责“抢购前”和“抢购入口”的逻辑：
 * 1. 活动增删改查
 * 2. 启用/停用活动
 * 3. 活动预热到 Redis
 * 4. 抢购请求入口
 * 5. 抢购结果轮询查询
 */
@Service
@Slf4j
public class FlashSaleServiceImpl implements FlashSaleService {

    private static final long REQUEST_RESULT_TTL_DAYS = 7L;
    private static final long REQUEST_RESULT_TTL_SECONDS = Duration.ofDays(REQUEST_RESULT_TTL_DAYS).toSeconds();

    @Autowired
    private FlashSaleActivityMapper flashSaleActivityMapper;
    @Autowired
    private FlashSaleOrderMapper flashSaleOrderMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private DefaultRedisScript<Long> flashSalePurchaseScript;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private FlashSaleOrderService flashSaleOrderService;

    @Value("${sky.flash-sale.async-enabled:false}")
    private boolean flashSaleAsyncEnabled;

    /**
     * 创建活动。
     * 当前只写数据库，不直接预热 Redis。
     */
    @Override
    @Transactional
    public void saveActivity(FlashSaleActivityDTO flashSaleActivityDTO) {
        // 先做业务参数校验，防止无效活动写入数据库。
        validateActivityDTO(flashSaleActivityDTO);
        Dish dish = getAndValidateDish(flashSaleActivityDTO.getDishId());
        if (flashSaleActivityDTO.getSalePrice().compareTo(dish.getPrice()) >= 0) {
            throw new FlashSaleBusinessException("特卖价必须低于菜品原价");
        }

        FlashSaleActivity flashSaleActivity = new FlashSaleActivity();
        BeanUtils.copyProperties(flashSaleActivityDTO, flashSaleActivity);
        flashSaleActivity.setAvailableStock(flashSaleActivityDTO.getStock());
        flashSaleActivity.setStatus(flashSaleActivityDTO.getStatus() == null ? FlashSaleActivity.DISABLED : flashSaleActivityDTO.getStatus());
        flashSaleActivityMapper.insert(flashSaleActivity);
    }

    /**
     * 修改活动。
     * 启用中的活动不允许直接修改，修改成功后会清理 Redis 运行时缓存。
     */
    @Override
    @Transactional
    public void updateActivity(FlashSaleActivityDTO flashSaleActivityDTO) {
        if (flashSaleActivityDTO.getId() == null) {
            throw new FlashSaleBusinessException("活动ID不能为空");
        }
        validateActivityDTO(flashSaleActivityDTO);
        FlashSaleActivity dbActivity = getActivityOrThrow(flashSaleActivityDTO.getId());
        if (FlashSaleActivity.ENABLED.equals(dbActivity.getStatus())) {
            throw new FlashSaleBusinessException("活动启用中，请先停用后再修改");
        }

        Dish dish = getAndValidateDish(flashSaleActivityDTO.getDishId());
        if (flashSaleActivityDTO.getSalePrice().compareTo(dish.getPrice()) >= 0) {
            throw new FlashSaleBusinessException("特卖价必须低于菜品原价");
        }

        FlashSaleActivity updateEntity = new FlashSaleActivity();
        BeanUtils.copyProperties(flashSaleActivityDTO, updateEntity);
        updateEntity.setAvailableStock(flashSaleActivityDTO.getStock());
        updateEntity.setStatus(flashSaleActivityDTO.getStatus() == null ? dbActivity.getStatus() : flashSaleActivityDTO.getStatus());
        flashSaleActivityMapper.update(updateEntity);
        clearRuntimeKeys(updateEntity.getId());
    }

    /**
     * 分页查询活动。
     * 如果 Redis 中已有实时库存，会把运行时库存补回 VO。
     */
    @Override
    public PageResult pageQuery(FlashSaleActivityPageQueryDTO queryDTO) {
        int page = queryDTO.getPage() == null || queryDTO.getPage() < 1 ? 1 : queryDTO.getPage();
        int pageSize = queryDTO.getPageSize() == null || queryDTO.getPageSize() < 1 ? 10 : queryDTO.getPageSize();
        PageHelper.startPage(page, pageSize);
        Page<FlashSaleActivityVO> activityPage = flashSaleActivityMapper.pageQuery(queryDTO);
        activityPage.forEach(this::fillRuntimeStock);
        return new PageResult(activityPage.getTotal(), activityPage.getResult());
    }

    /**
     * 查询活动详情，并尝试补充 Redis 中的实时库存。
     */
    @Override
    public FlashSaleActivityVO getDetail(Long id) {
        FlashSaleActivityVO activityVO = flashSaleActivityMapper.getDetailVOById(id);
        if (activityVO == null) {
            throw new FlashSaleBusinessException("活动不存在");
        }
        fillRuntimeStock(activityVO);
        return activityVO;
    }

    /**
     * 启用活动。
     * <p>
     * 当前做法：
     * 1. 先把数据库状态改为启用
     * 2. 如果 Redis 中已存在该活动缓存，则把缓存中的 status 同步改成启用
     */
    @Override
    @Transactional
    public void enable(Long id) {
        FlashSaleActivity flashSaleActivity = getActivityOrThrow(id);
        getAndValidateDish(flashSaleActivity.getDishId());

        FlashSaleActivity updateEntity = FlashSaleActivity.builder()
                .id(id)
                .status(FlashSaleActivity.ENABLED)
                .build();
        flashSaleActivityMapper.update(updateEntity);

        String infoKey = buildActivityInfoKey(id);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(infoKey))) {
            stringRedisTemplate.opsForHash().put(infoKey, "status", String.valueOf(FlashSaleActivity.ENABLED));
        }
    }

    /**
     * 停用活动。
     * 逻辑与启用对称：数据库先改状态，Redis 中已有缓存则同步关闭。
     */
    @Override
    @Transactional
    public void disable(Long id) {
        getActivityOrThrow(id);
        FlashSaleActivity updateEntity = FlashSaleActivity.builder()
                .id(id)
                .status(FlashSaleActivity.DISABLED)
                .build();
        flashSaleActivityMapper.update(updateEntity);

        String infoKey = buildActivityInfoKey(id);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(infoKey))) {
            stringRedisTemplate.opsForHash().put(infoKey, "status", String.valueOf(FlashSaleActivity.DISABLED));
        }
    }

    /**
     * 活动预热。
     * <p>
     * 目标是把抢购入口会频繁读取的数据提前加载到 Redis：
     * 1. 活动基础信息 -> Hash
     * 2. 活动库存 -> String
     * 3. 用户已购次数 -> Hash
     */
    @Override
    public void preheat(Long id) {
        FlashSaleActivity flashSaleActivity = getActivityOrThrow(id);
        if (!FlashSaleActivity.ENABLED.equals(flashSaleActivity.getStatus())) {
            throw new FlashSaleBusinessException("请先启用活动再预热");
        }

        Dish dish = getAndValidateDish(flashSaleActivity.getDishId());
        if (!Integer.valueOf(1).equals(dish.getStatus())) {
            throw new FlashSaleBusinessException("菜品未起售，不能预热");
        }

        String infoKey = buildActivityInfoKey(id);
        String stockKey = buildActivityStockKey(id);
        String userCountKey = buildActivityUserCountKey(id);
        String readyKey = buildActivityReadyKey(id);

        // Only one application instance may rebuild a runtime snapshot at a time.
        String lockKey = "sky:flashsale:preheat:lock:" + id;
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockToken, Duration.ofSeconds(60));
        if (!Boolean.TRUE.equals(locked)) {
            for (int attempt = 0; attempt < 40; attempt++) {
                if (runtimeSnapshotReady(infoKey, stockKey, readyKey)) {
                    return;
                }
                try {
                    Thread.sleep(50L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new FlashSaleBusinessException("活动预热等待已中断");
                }
            }
            throw new FlashSaleBusinessException("活动正在预热，请稍后重试");
        }

        try {
            // A manual or racing preheat must never reset live stock or per-user quota.
            if (runtimeSnapshotReady(infoKey, stockKey, readyKey)) {
                return;
            }

            // Clear a partial snapshot before rebuilding it, so Lua never reads stale mixed data.
            stringRedisTemplate.delete(Arrays.asList(infoKey, stockKey, userCountKey, readyKey));

            // Refresh the database snapshot after acquiring the distributed lock.
            flashSaleActivity = getActivityOrThrow(id);
            if (!FlashSaleActivity.ENABLED.equals(flashSaleActivity.getStatus())) {
                throw new FlashSaleBusinessException("请先启用活动再预热");
            }

            // Activity configuration snapshot: Lua only reads the fields needed for admission.
            Map<String, String> info = new HashMap<>();
            info.put("status", String.valueOf(flashSaleActivity.getStatus()));
            info.put("startTime", String.valueOf(toEpochSecond(flashSaleActivity.getStartTime())));
            info.put("endTime", String.valueOf(toEpochSecond(flashSaleActivity.getEndTime())));
            info.put("limitPerUser", String.valueOf(flashSaleActivity.getLimitPerUser()));
            info.put("dishId", String.valueOf(flashSaleActivity.getDishId()));
            info.put("salePrice", flashSaleActivity.getSalePrice().toPlainString());
            stringRedisTemplate.opsForHash().putAll(infoKey, info);

            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(flashSaleActivity.getAvailableStock()));
            List<Map<String, Object>> userCounts = flashSaleOrderMapper.countActiveUserPurchases(id);
            if (userCounts != null) {
                for (Map<String, Object> userCount : userCounts) {
                    Object userId = userCount.get("userId");
                    Object purchaseCount = userCount.get("purchaseCount");
                    if (userId != null && purchaseCount != null) {
                        stringRedisTemplate.opsForHash().put(userCountKey, String.valueOf(userId), String.valueOf(purchaseCount));
                    }
                }
            }
            FlashSaleActivity latestActivity = getActivityOrThrow(id);
            Dish latestDish = getAndValidateDish(latestActivity.getDishId());
            if (!FlashSaleActivity.ENABLED.equals(latestActivity.getStatus())
                    || !Integer.valueOf(1).equals(latestDish.getStatus())) {
                stringRedisTemplate.delete(Arrays.asList(infoKey, stockKey, userCountKey, readyKey));
                throw new FlashSaleBusinessException("活动或菜品状态已变更，请刷新后重试");
            }
            stringRedisTemplate.opsForValue().set(readyKey, "1");
        } finally {
            DefaultRedisScript<Long> releaseLockScript = new DefaultRedisScript<>(
                    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                    Long.class
            );
            stringRedisTemplate.execute(releaseLockScript, Collections.singletonList(lockKey), lockToken);
        }
    }

    /**
     * 查询当前可抢购活动列表，并补充 Redis 中的实时库存。
     */
    @Override
    public List<FlashSaleActivityVO> listCurrentActivities() {
        List<FlashSaleActivityVO> activities = flashSaleActivityMapper.listCurrentActivities(LocalDateTime.now());
        activities.forEach(this::fillRuntimeStock);
        return activities;
    }

    /**
     * 抢购入口。
     * <p>
     * 核心流程：
     * 1. 校验参数完整性
     * 2. 通过 requestId 做请求级幂等
     * 3. 校验地址是否属于当前用户
     * 4. 确保活动已预热
     * 5. 执行 Lua，原子完成资格判断 + 库存扣减 + 用户次数累加
     * 6. Lua 成功后写 PENDING 结果，并组装建单消息
     * 7. 根据配置决定同步建单还是异步发 MQ
     */
    @Override
    public FlashSalePurchaseVO purchase(FlashSalePurchaseDTO purchaseDTO) {
        if (purchaseDTO.getActivityId() == null || purchaseDTO.getAddressBookId() == null || !StringUtils.hasText(purchaseDTO.getRequestId())) {
            throw new FlashSaleBusinessException("抢购参数不完整");
        }
        ensureShopOpen();

        // 第一道幂等：同一个 requestId 已经处理过时，直接返回上次结果。
        FlashSaleResultVO existingResult = getPurchaseResultInternal(purchaseDTO.getRequestId());
        if (existingResult != null) {
            return FlashSalePurchaseVO.builder()
                    .requestId(purchaseDTO.getRequestId())
                    .status(existingResult.getStatus())
                    .message(existingResult.getMessage())
                    .build();
        }

        Long userId = BaseContext.getCurrentId();
        AddressBook addressBook = addressBookMapper.getById(purchaseDTO.getAddressBookId());
        if (addressBook == null || !userId.equals(addressBook.getUserId())) {
            throw new FlashSaleBusinessException("收货地址不存在");
        }

        // 如果 Redis 中还没有活动快照，则自动补一次预热。
        ensureActivityPreheated(purchaseDTO.getActivityId());

        // 高并发入口的核心：Lua 在 Redis 内部原子判断活动状态、库存和限购资格。
        Long executeResult = stringRedisTemplate.execute(
                flashSalePurchaseScript,
                Arrays.asList(
                        buildActivityStockKey(purchaseDTO.getActivityId()),
                        buildActivityUserCountKey(purchaseDTO.getActivityId()),
                        buildActivityInfoKey(purchaseDTO.getActivityId()),
                        buildRequestKey(purchaseDTO.getRequestId())
                ),
                String.valueOf(userId),
                String.valueOf(toEpochSecond(LocalDateTime.now())),
                FlashSaleRedisConstant.REQUEST_FAIL_PREFIX,
                String.valueOf(REQUEST_RESULT_TTL_SECONDS),
                "活动不存在或未预热",
                "库存不足",
                "已达到本活动限购次数",
                FlashSaleRedisConstant.REQUEST_PENDING,
                "活动尚未开始",
                "活动已结束"
        );

        if (executeResult == null) {
            throw new FlashSaleBusinessException("抢购系统繁忙，请稍后重试");
        }

        // 同一 requestId 并发重复到达时，Lua 返回 2，读取原请求结果且不再占用配额。
        if (executeResult == 2L) {
            FlashSaleResultVO duplicateResult = getPurchaseResultInternal(purchaseDTO.getRequestId());
            return FlashSalePurchaseVO.builder()
                    .requestId(purchaseDTO.getRequestId())
                    .status(duplicateResult == null ? FlashSalePurchaseVO.ACCEPTED : duplicateResult.getStatus())
                    .message(duplicateResult == null ? "抢购请求已受理，请稍后查询结果" : duplicateResult.getMessage())
                    .build();
        }

        // Lua 已原子保存资格校验的失败结果，直接返回对应提示。
        if (executeResult != 1L) {
            String failMessage = convertLuaResult(executeResult);
            return FlashSalePurchaseVO.builder()
                    .requestId(purchaseDTO.getRequestId())
                    .status(FlashSalePurchaseVO.FAILED)
                    .message(failMessage)
                    .build();
        }

        // Lua 已经在同一个原子操作中预占库存、限购次数并写入 PENDING。
        FlashSaleOrderMessage orderMessage = FlashSaleOrderMessage.builder()
                .activityId(purchaseDTO.getActivityId())
                .userId(userId)
                .addressBookId(purchaseDTO.getAddressBookId())
                .requestId(purchaseDTO.getRequestId())
                .remark(purchaseDTO.getRemark())
                .build();

        // 默认关闭异步链路时，直接同步建单，便于本地开发和 Swagger 调试。
        if (!flashSaleAsyncEnabled) {
            try {
                flashSaleOrderService.createFlashSaleOrder(orderMessage);
            } catch (RuntimeException e) {
                // Synchronous development mode has no broker retry queue, so compensate once here.
                flashSaleOrderService.failFlashSaleOrder(orderMessage, "订单创建暂时失败，请稍后重试");
            }
            FlashSaleResultVO syncResult = getPurchaseResultInternal(purchaseDTO.getRequestId());
            return FlashSalePurchaseVO.builder()
                    .requestId(purchaseDTO.getRequestId())
                    .status(syncResult == null ? FlashSalePurchaseVO.ACCEPTED : syncResult.getStatus())
                    .message(syncResult == null ? "抢购请求已受理，请稍后查询结果" : syncResult.getMessage())
                    .build();
        }

        try {
            // 开启异步模式后，主线程只发消息，不直接操作 orders 表。
            CorrelationData correlationData = new CorrelationData(purchaseDTO.getRequestId());
            rabbitTemplate.convertAndSend(
                    FlashSaleRabbitConstant.ORDER_EXCHANGE,
                    FlashSaleRabbitConstant.ORDER_ROUTING_KEY,
                    orderMessage,
                    message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return message;
                    },
                    correlationData
            );
            correlationData.getFuture().addCallback(confirm -> {
                if (confirm.isAck() && correlationData.getReturned() == null) {
                    return;
                }
                String reason = correlationData.getReturned() == null
                        ? "消息队列未确认接收订单"
                        : "订单消息没有投递到消费队列";
                log.error("特卖建单消息投递失败，requestId={}，reason={}", purchaseDTO.getRequestId(), reason);
                try {
                    flashSaleOrderService.failFlashSaleOrder(orderMessage, reason);
                } catch (RuntimeException compensationError) {
                    log.error("特卖消息投递失败后的配额回补失败，requestId={}", purchaseDTO.getRequestId(), compensationError);
                }
            }, publishError -> {
                log.error("特卖建单消息确认异常，requestId={}", purchaseDTO.getRequestId(), publishError);
                try {
                    flashSaleOrderService.failFlashSaleOrder(orderMessage, "消息队列不可用，请稍后重试");
                } catch (RuntimeException compensationError) {
                    log.error("特卖消息确认异常后的配额回补失败，requestId={}", purchaseDTO.getRequestId(), compensationError);
                }
            });
        } catch (Exception e) {
            // MQ 不可用时，要把 Redis 中已经预占的配额回滚。
            flashSaleOrderService.failFlashSaleOrder(orderMessage, "系统繁忙，请稍后重试");
            return FlashSalePurchaseVO.builder()
                    .requestId(purchaseDTO.getRequestId())
                    .status(FlashSalePurchaseVO.FAILED)
                    .message("消息队列不可用，请稍后重试")
                    .build();
        }

        return FlashSalePurchaseVO.builder()
                .requestId(purchaseDTO.getRequestId())
                .status(FlashSalePurchaseVO.ACCEPTED)
                .message("抢购请求已受理，请稍后查询结果")
                .build();
    }

    /**
     * 查询抢购结果，通常供前端轮询使用。
     */
    @Override
    public FlashSaleResultVO getPurchaseResult(String requestId) {
        FlashSaleResultVO resultVO = getPurchaseResultInternal(requestId);
        if (resultVO == null) {
            return FlashSaleResultVO.builder()
                    .requestId(requestId)
                    .status(FlashSalePurchaseVO.ACCEPTED)
                    .message("请求处理中或不存在，请稍后重试")
                    .build();
        }
        return resultVO;
    }

    /**
     * 抢购结果读取逻辑。
     * 优先查 Redis，Redis 没有时再回查数据库做兜底。
     */
    private FlashSaleResultVO getPurchaseResultInternal(String requestId) {
        String requestResult = stringRedisTemplate.opsForValue().get(buildRequestKey(requestId));
        if (!StringUtils.hasText(requestResult)) {
            FlashSaleOrder flashSaleOrder = flashSaleOrderMapper.getByRequestId(requestId);
            return flashSaleOrder == null ? null : buildSuccessResult(requestId, flashSaleOrder);
        }

        // 请求仍在处理中，前端可以继续轮询。
        if (FlashSaleRedisConstant.REQUEST_PENDING.equals(requestResult)) {
            return FlashSaleResultVO.builder()
                    .requestId(requestId)
                    .status(FlashSalePurchaseVO.ACCEPTED)
                    .message("订单创建中")
                    .build();
        }

        // FAIL:xxx，直接截取失败原因返回。
        if (requestResult.startsWith(FlashSaleRedisConstant.REQUEST_FAIL_PREFIX)) {
            return FlashSaleResultVO.builder()
                    .requestId(requestId)
                    .status(FlashSalePurchaseVO.FAILED)
                    .message(requestResult.substring(FlashSaleRedisConstant.REQUEST_FAIL_PREFIX.length()))
                    .build();
        }

        // SUCCESS:flashSaleOrderId，继续查特卖记录表拿到 orderId 等信息。
        if (requestResult.startsWith(FlashSaleRedisConstant.REQUEST_SUCCESS_PREFIX)) {
            Long flashSaleOrderId = Long.valueOf(requestResult.substring(FlashSaleRedisConstant.REQUEST_SUCCESS_PREFIX.length()));
            FlashSaleOrder flashSaleOrder = flashSaleOrderMapper.getById(flashSaleOrderId);
            return flashSaleOrder == null ? null : buildSuccessResult(requestId, flashSaleOrder);
        }
        return null;
    }

    /**
     * 根据特卖记录封装最终返回结果。
     */
    private FlashSaleResultVO buildSuccessResult(String requestId, FlashSaleOrder flashSaleOrder) {
        if (FlashSaleOrder.CANCELLED.equals(flashSaleOrder.getStatus()) || FlashSaleOrder.FAILED.equals(flashSaleOrder.getStatus())) {
            return FlashSaleResultVO.builder()
                    .requestId(requestId)
                    .status(FlashSalePurchaseVO.FAILED)
                    .message(StringUtils.hasText(flashSaleOrder.getFailReason()) ? flashSaleOrder.getFailReason() : "订单已取消")
                    .activityId(flashSaleOrder.getActivityId())
                    .flashSaleOrderId(flashSaleOrder.getId())
                    .orderId(flashSaleOrder.getOrderId())
                    .build();
        }

        String message = FlashSaleOrder.PAID.equals(flashSaleOrder.getStatus()) ? "抢购成功，订单已支付" : "抢购成功，请尽快完成支付";
        return FlashSaleResultVO.builder()
                .requestId(requestId)
                .status(FlashSalePurchaseVO.SUCCESS)
                .message(message)
                .activityId(flashSaleOrder.getActivityId())
                .flashSaleOrderId(flashSaleOrder.getId())
                .orderId(flashSaleOrder.getOrderId())
                .build();
    }

    /**
     * 活动参数校验。
     */
    private void validateActivityDTO(FlashSaleActivityDTO flashSaleActivityDTO) {
        if (!StringUtils.hasText(flashSaleActivityDTO.getActivityName())) {
            throw new FlashSaleBusinessException("活动名称不能为空");
        }
        if (flashSaleActivityDTO.getDishId() == null) {
            throw new FlashSaleBusinessException("请选择活动菜品");
        }
        if (flashSaleActivityDTO.getSalePrice() == null || flashSaleActivityDTO.getSalePrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new FlashSaleBusinessException("特卖价必须大于0");
        }
        if (flashSaleActivityDTO.getStock() == null || flashSaleActivityDTO.getStock() < 1) {
            throw new FlashSaleBusinessException("库存必须大于0");
        }
        if (flashSaleActivityDTO.getLimitPerUser() == null || flashSaleActivityDTO.getLimitPerUser() < 1) {
            throw new FlashSaleBusinessException("限购次数必须大于0");
        }
        if (flashSaleActivityDTO.getStartTime() == null || flashSaleActivityDTO.getEndTime() == null) {
            throw new FlashSaleBusinessException("活动时间不能为空");
        }
        if (!flashSaleActivityDTO.getEndTime().isAfter(flashSaleActivityDTO.getStartTime())) {
            throw new FlashSaleBusinessException("活动结束时间必须晚于开始时间");
        }
    }

    /**
     * 查询活动，不存在则抛异常。
     */
    private FlashSaleActivity getActivityOrThrow(Long id) {
        FlashSaleActivity flashSaleActivity = flashSaleActivityMapper.getById(id);
        if (flashSaleActivity == null) {
            throw new FlashSaleBusinessException("活动不存在");
        }
        return flashSaleActivity;
    }

    /**
     * 查询菜品，不存在则抛异常。
     */
    private Dish getAndValidateDish(Long dishId) {
        Dish dish = dishMapper.getById(dishId);
        if (dish == null) {
            throw new FlashSaleBusinessException("菜品不存在");
        }
        return dish;
    }

    /**
     * 确保活动在 Redis 中可用。
     * 如果活动信息或库存快照不存在，则自动触发一次预热。
     */
    private void ensureActivityPreheated(Long activityId) {
        String infoKey = buildActivityInfoKey(activityId);
        String stockKey = buildActivityStockKey(activityId);
        String readyKey = buildActivityReadyKey(activityId);
        if (!runtimeSnapshotReady(infoKey, stockKey, readyKey)) {
            preheat(activityId);
        }
    }

    private boolean runtimeSnapshotReady(String infoKey, String stockKey, String readyKey) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(infoKey))
                && Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))
                && Boolean.TRUE.equals(stringRedisTemplate.hasKey(readyKey));
    }

    private void ensureShopOpen() {
        Object status = redisTemplate.opsForValue().get("SHOP_STATUS");
        if (!(status instanceof Number) || ((Number) status).intValue() != 1) {
            throw new FlashSaleBusinessException("门店已打烊，暂时不能参与特卖");
        }
    }

    /**
     * 使用 Redis 中的运行时库存覆盖 VO 里的数据库库存。
     */
    private void fillRuntimeStock(FlashSaleActivityVO activityVO) {
        String stock = stringRedisTemplate.opsForValue().get(buildActivityStockKey(activityVO.getId()));
        if (StringUtils.hasText(stock)) {
            activityVO.setAvailableStock(Integer.valueOf(stock));
        }
    }

    /**
     * 把 Lua 状态码翻译成可直接返回给前端的业务提示。
     */
    private String convertLuaResult(Long resultCode) {
        if (resultCode == -1L) {
            return "活动不存在或未预热";
        }
        if (resultCode == -2L) {
            return "库存不足";
        }
        if (resultCode == -3L) {
            return "已达到本活动限购次数";
        }
        if (resultCode == -4L) {
            return "活动尚未开始";
        }
        if (resultCode == -5L) {
            return "活动已结束";
        }
        return "抢购失败，请稍后重试";
    }

    /**
     * 活动配置变化后，清理 Redis 运行时缓存。
     * 后续可由管理端手动预热，或由首次抢购自动触发预热。
     */
    private void clearRuntimeKeys(Long activityId) {
        stringRedisTemplate.delete(Arrays.asList(
                buildActivityInfoKey(activityId),
                buildActivityStockKey(activityId),
                buildActivityUserCountKey(activityId),
                buildActivityReadyKey(activityId)
        ));
    }

    /**
     * MQ 发送失败等异常场景下，回滚 Redis 中已经预占的库存和用户次数。
     */
    /**
     * 写入请求结果缓存。
     */
    private void setRequestResult(String requestKey, String value) {
        stringRedisTemplate.opsForValue().set(requestKey, value, Duration.ofDays(REQUEST_RESULT_TTL_DAYS));
    }

    /**
     * 时间统一转成秒级时间戳，便于 Lua 中直接做数值比较。
     */
    private long toEpochSecond(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    private String buildActivityInfoKey(Long activityId) {
        return String.format(FlashSaleRedisConstant.ACTIVITY_INFO_KEY, activityId);
    }

    private String buildActivityStockKey(Long activityId) {
        return String.format(FlashSaleRedisConstant.ACTIVITY_STOCK_KEY, activityId);
    }

    private String buildActivityUserCountKey(Long activityId) {
        return String.format(FlashSaleRedisConstant.ACTIVITY_USER_COUNT_KEY, activityId);
    }

    private String buildActivityReadyKey(Long activityId) {
        return String.format(FlashSaleRedisConstant.ACTIVITY_READY_KEY, activityId);
    }

    private String buildRequestKey(String requestId) {
        return String.format(FlashSaleRedisConstant.REQUEST_RESULT_KEY, requestId);
    }
}
