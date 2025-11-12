package com.tmd.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.tmd.config.ThreadPoolConfig;
import com.tmd.entity.dto.*;
import com.tmd.mapper.MailCommentMapper;
import com.tmd.mapper.MailMapper;
import com.tmd.mapper.ReceivedMailMapper;
import com.tmd.publisher.MessageProducer;
import org.springframework.data.redis.core.ZSetOperations;
import java.util.HashSet;
import com.tmd.service.MailService;
import com.tmd.tools.BaseContext;
import com.tmd.tools.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.search.Scroll;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MailServiceImpl implements MailService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private MailMapper mailMapper;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ThreadPoolConfig threadPoolConfig;
    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private MailCommentMapper mailCommentMapper;
    @Autowired
    private ReceivedMailMapper receivedMailMapper;
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    public MailVO getMailById(Integer mailId) {
        // 先查redis，缓存中有则直接返回
        String s = stringRedisTemplate.opsForValue().get("mail:" + mailId);
        // 缓存中没有则查数据库
        if (StrUtil.isNotBlank(s)) {
            mail bean = JSONUtil.toBean(s, mail.class);
            MailVO mailVO = MailVO.builder()
                    .mailId(bean.getId())
                    .stampType(bean.getStampType())
                    .stampContent(bean.getStampContent())
                    .senderNickname(bean.getSenderNickname())
                    .recipientEmail(bean.getRecipientEmail())
                    .content(bean.getContent())
                    .status(bean.getStatus())
                    .readAt(bean.getReadAt())
                    .build();
            return mailVO;
        }
        // 数据库中查到则返回
        if (s != null) {
            // 说明是缓存穿透，直接返回
            return null;
        }
        // 查数据库
        mail mail = mailMapper.selectById(mailId);
        // 数据库中没查到则返回null，而且使用缓存穿透
        if (mail == null) {
            stringRedisTemplate.opsForValue().set("mail:" + mailId, "", 5, TimeUnit.MINUTES);
            return null;
        }
        // 存入redis
        stringRedisTemplate.opsForValue().set("mail:" + mailId, JSONUtil.toJsonStr(mail), 5, TimeUnit.MINUTES);
        MailVO mailVO = MailVO.builder()
                .mailId(mail.getId())
                .stampType(mail.getStampType())
                .stampContent(mail.getStampContent())
                .senderNickname(mail.getSenderNickname())
                .recipientEmail(mail.getRecipientEmail())
                .content(mail.getContent())
                .status(mail.getStatus())
                .readAt(mail.getReadAt())
                .build();
        return mailVO;
    }

    // 到时候新增的时候记得同步缓存
    @Override
    public ScrollResult getAllMails(MailPackage mailPackage) {
        // 进行滚动分页查询
        String key = "mails:all";
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, mailPackage.getMax(),
                        mailPackage.getScroll(), mailPackage.getSize());
        if (Objects.isNull(tuples) || tuples.isEmpty()) {
            // 决定进行数据库查询
            // 查询数据库的时候肯定不能阻塞查询，让一个线程去查询数据库，其他的先返回空，等数据库查询完成后再返回
            RLock lock = redissonClient.getLock("lock:mails:all");
            try {
                boolean b = lock.tryLock(5, 30, TimeUnit.SECONDS);
                if (b) {
                    threadPoolConfig.threadPoolExecutor().execute(() -> {
                        // 查询数据库，查询所有邮件的id
                        try {
                            List<Long> ids = mailMapper.selectAllIds();
                            // 查询数据库的时候肯定不能阻塞查询，让一个线程去查询数据库，其他的先返回空，等数据库查询完成后再返回
                            Set<ZSetOperations.TypedTuple<String>> addTuples = new HashSet<>(ids.size());
                            for (Long id : ids) {
                                addTuples.add(
                                        ZSetOperations.TypedTuple.of(id.toString(),
                                                (double) System.currentTimeMillis()));
                            }
                            if (!addTuples.isEmpty()) {
                                stringRedisTemplate.opsForZSet().add(key, addTuples);
                            }
                        } finally {
                            if (lock.isHeldByCurrentThread()) {
                                lock.unlock();
                            }
                        }
                    });
                }
                return ScrollResult.builder().max(mailPackage.getMax()).scroll(mailPackage.getScroll())
                        .data(new ArrayList<>()).build();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } /*
               * finally {
               * if (lock.isHeldByCurrentThread()) {
               * lock.unlock();
               * }
               * }
               */
        }

        List<Long> ids = new ArrayList<>(tuples.size());
        long minTime = mailPackage.getMax();
        int os = mailPackage.getScroll();
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            String s = t.getValue();
            ids.add(Long.parseLong(s));
            Double score = t.getScore();
            long time = score == null ? minTime : score.longValue();
            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        List<mail> list = mailMapper.selectByIds(ids);
        List<MailVO> mailVOS = new ArrayList<>(list.size());
        for (mail mail : list) {
            MailVO mailVO = MailVO.builder()
                    .mailId(mail.getId())
                    .stampType(mail.getStampType())
                    .stampContent(mail.getStampContent())
                    .senderNickname(mail.getSenderNickname())
                    .recipientEmail(mail.getRecipientEmail())
                    .content(mail.getContent())
                    .status(mail.getStatus())
                    .readAt(mail.getReadAt())
                    .build();
            mailVOS.add(mailVO);
        }
        return ScrollResult.builder()
                .max(minTime)
                .scroll(os)
                .data(mailVOS)
                .build();
    }

    @Override
    public PageResult getSelfMails(Integer page, Integer size, String status) {
        // 获取当前用户，分页查询呗
        PageHelper.startPage(page, size);
        Long l = BaseContext.get();
        Page<mail> page1 = mailMapper.selectByUserId(l);
        return PageResult.builder()
                .total(page1.getTotal())
                .rows(page1.getResult())
                .build();
    }

    @Override
    public void sendMail(MailDTO mailDTO) {
        // 插入数据库呗，然后更新缓存，小问题罢了
        if (StrUtil.isNotBlank(mailDTO.getRecipientEmail())) {
            // 调用邮件服务，使用生产者异步发送邮件
            messageProducer.sendMailMessage(mailDTO);
            return;
        }
        mail mail = com.tmd.entity.dto.mail.builder()
                .senderId(BaseContext.get())
                .senderNickname(mailDTO.getSenderNickname())
                .recipientEmail(mailDTO.getRecipientEmail())
                .content(mailDTO.getContent())
                .status(mailStatus.sent)
                .createdAt(LocalDateTime.now())
                .stampType(mailDTO.getStampType())
                .stampContent(mailDTO.getStampContent())
                .senderNickname(mailDTO.getSenderNickname())
                .build();
        mailMapper.insert(mail);
        stringRedisTemplate.opsForZSet().add("mails:all", mail.getId().toString(),
                mail.getCreatedAt().toInstant(ZoneOffset.of("+8")).toEpochMilli());
        stringRedisTemplate.opsForValue().set("mail:" + mail.getId(), JSONUtil.toJsonStr(mail), 5, TimeUnit.MINUTES);
    }

    @Override
    public Result comment(Long mailId, MailDTO mailDTO, Boolean isFirst) {
        // 首先肯定要操作数据库的，最好使用redis缓存
        // 所以我的思路如下
        // 先把maildto整理为mail，然后将mail推送到别人的邮箱当中，mail:push:userId，这个操作通过redis可以轻松实现
        // 通过redis获取mailId对应的邮件
        if (isFirst) {
            String s = stringRedisTemplate.opsForValue().get("mail:" + mailId);
            mail mail;
            if (StrUtil.isBlank(s)) {
                // 查询数据库
                mail = mailMapper.selectById(mailId.intValue());
                if (mail == null) {
                    return Result.error("原始邮件不存在或已删除");
                }
                // 将数据库中的邮件缓存到redis中
                stringRedisTemplate.opsForValue().set("mail:" + mail.getId(), JSONUtil.toJsonStr(mail), 5,
                        TimeUnit.MINUTES);
            } else {
                mail = JSONUtil.toBean(s, mail.class);
            }
            Long senderId = mail.getSenderId();
            MailComment mailComment = MailComment.builder()
                    .mailId(Long.valueOf(mailId))
                    .commenterId(BaseContext.get())
                    .content(mailDTO.getContent())
                    .createAt(LocalDateTime.now())
                    .build();
            long l = redisIdWorker.nextId("mailreceive");
            ReceivedMail receivedMail = ReceivedMail.builder()
                    .id(l)
                    .createAt(LocalDateTime.now())
                    .originalMailId(Long.valueOf(mailId))
                    .recipientId(senderId)
                    .senderId(BaseContext.get())
                    .senderNickname(mailDTO.getSenderNickname())
                    .content(mailDTO.getContent())
                    .status(mailStatus.sent)
                    .stampType(mailDTO.getStampType())
                    .readAt(null)
                    .build();
            stringRedisTemplate.opsForZSet().add("mail:push:" + senderId, JSONUtil.toJsonStr(receivedMail),
                    System.currentTimeMillis());
            // 然后把mail也推送到key为mail:comment:userId的list中，这样之后自己就可以查询到自己评论的邮件了
            stringRedisTemplate.opsForList().leftPush("mail:comment:" + BaseContext.get(),
                    JSONUtil.toJsonStr(mailComment));
            // 然后这些数据当然要插入到数据库当中了，插入到三个数据库中，异步写入
            threadPoolConfig.threadPoolExecutor().execute(() -> {

                mailCommentMapper.insert(mailComment);

                receivedMailMapper.insert(receivedMail);

                log.info("[邮件服务] 评论成功");
            });
            return Result.success(MailCommentVO.builder()
                    .isFirst(false)
                    .build());
        } else {
            // 如果不是第一次的话，说明是互相来信，那么这次的邮件id就是自己信箱中收到的邮件的id，也就是说是receivemailId，上一次来信的id
            // 要通过receivemailId找到回信的人的信息
            ReceivedMail receivedMail = receivedMailMapper.selectByMailId(mailId);
            ReceivedMail readyToAdd = ReceivedMail.builder()
                    .id(redisIdWorker.nextId("mailreceive"))
                    .createAt(LocalDateTime.now())
                    .originalMailId(receivedMail.getOriginalMailId())
                    .content(mailDTO.getContent())
                    .stampType(mailDTO.getStampType())
                    .status(mailStatus.sent)
                    .senderNickname(mailDTO.getSenderNickname())
                    .recipientId(receivedMail.getSenderId())
                    .senderId(BaseContext.get())
                    .readAt(null)
                    .build();
            stringRedisTemplate.opsForZSet().add("mail:push:" + receivedMail.getSenderId(),
                    JSONUtil.toJsonStr(readyToAdd),
                    System.currentTimeMillis());
            MailComment mailComment = MailComment.builder()
                    .mailId(receivedMail.getOriginalMailId())
                    .commenterId(BaseContext.get())
                    .content(mailDTO.getContent())
                    .createAt(LocalDateTime.now())
                    .build();
            stringRedisTemplate.opsForList().leftPush("mail:comment:" + BaseContext.get(),
                    JSONUtil.toJsonStr(mailComment));
            threadPoolConfig.threadPoolExecutor().execute(() -> {
                mailCommentMapper.insert(mailComment);
                receivedMailMapper.insert(readyToAdd);
                log.info("[邮件服务] 评论成功");
            });
            return Result.success(MailCommentVO.builder()
                    .isFirst(false)
                    .build());
        }
    }

    @Override
    public PageResult getReceivedMails(Integer page, Integer size, String status) {
        // 先查redis，redis没有再查数据库
        Set<String> set = stringRedisTemplate.opsForZSet().rangeByScore("mail:push:" + BaseContext.get(), 0,
                System.currentTimeMillis(), (long) (page - 1) * size, size);
        List<ReceivedMailVO> receivedMails = new ArrayList<>();
        if (set != null) {
            for (String s : set) {
                ReceivedMail receivedMail = JSONUtil.toBean(s, ReceivedMail.class);
                ReceivedMailVO receivedMailVO = ReceivedMailVO.builder()
                        .receivedMailId(receivedMail.getId())
                        .senderNickname(receivedMail.getSenderNickname())
                        .stampType(receivedMail.getStampType())
                        .reviewContent(receivedMail.getContent())
                        .createdAt(receivedMail.getCreateAt())
                        .build();
                // 获取原来的邮件的内容
                String s1 = stringRedisTemplate.opsForValue().get("mail:" + receivedMail.getOriginalMailId());
                mail mail;
                if (StrUtil.isBlank(s1)) {
                    // 查询数据库
                    mail = mailMapper.selectById(receivedMail.getOriginalMailId().intValue());
                    if (mail == null) {
                        return PageResult
                                .builder()
                                .total(0L)
                                .rows(new ArrayList<>())
                                .build();
                    }
                    // 将数据库中的邮件缓存到redis中
                    stringRedisTemplate.opsForValue().set("mail:" + mail.getId(), JSONUtil.toJsonStr(mail), 5,
                            TimeUnit.MINUTES);
                } else {
                    mail = JSONUtil.toBean(s1, mail.class);
                }
                receivedMailVO.setContent(mail.getContent());
                receivedMails.add(receivedMailVO);
            }
            return PageResult.builder()
                    .total(stringRedisTemplate.opsForZSet().size("mail:push:" + BaseContext.get()))
                    .rows(receivedMails)
                    .build();
        } else {
            PageHelper.startPage(page, size);
            Page<ReceivedMail> page1 = receivedMailMapper.selectByUserId(BaseContext.get());
            List<ReceivedMailVO> collect = page1.getResult().stream()
                    .map(receivedMail -> ReceivedMailVO.builder()
                            .receivedMailId(receivedMail.getId())
                            .senderNickname(receivedMail.getSenderNickname())
                            .stampType(receivedMail.getStampType())
                            .reviewContent(receivedMail.getContent())
                            .createdAt(receivedMail.getCreateAt())
                            .content(getMailById(receivedMail.getOriginalMailId().intValue()).getContent())
                            .build())
                    .collect(Collectors.toList());
            // 恢复缓存，缓存的key为mail:push:userId
            threadPoolConfig.threadPoolExecutor().execute(() -> {
                for (int i = 0; i < page1.getTotal(); i++) {
                    stringRedisTemplate.opsForZSet().add("mail:push:" + BaseContext.get(),
                            JSONUtil.toJsonStr(page1.getResult().get(i)),
                            System.currentTimeMillis());
                }
                log.info("[邮件服务] 恢复缓存成功");
            });
            return PageResult.builder()
                    .total(page1.getTotal())
                    .rows(collect)
                    .build();
        }
    }
}
