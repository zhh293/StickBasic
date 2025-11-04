package com.tmd.service.impl;

import cn.hutool.json.JSONUtil;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.tmd.config.ThreadPoolConfig;
import com.tmd.entity.dto.*;
import com.tmd.mapper.TopicFollowMapper;
import com.tmd.mapper.TopicMapper;
import com.tmd.service.TopicService;
import com.tmd.tools.BaseContext;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class TopicServiceImpl implements TopicService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private TopicMapper topicMapper;

    @Autowired
    private TopicFollowMapper topicFollowMapper;

    @Autowired
    private ThreadPoolConfig threadPoolConfig;
    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    @Qualifier("moderationClient")
    private ChatClient moderationClient;

    @Override
    public Result getAllTopics(Integer page, Integer size, String status) throws InterruptedException {
        // 先查redis中是否有数据
        // 把这一堆放到redis的集合当中
        String key = "topics:all";
        // 根据时间大小从高到低取元素，根据page和size查询
        Set<String> set = stringRedisTemplate.opsForZSet().reverseRangeByScore(key, 0, System.currentTimeMillis(),
                (long) (page - 1) * size, size);
        // 如果为空的话，就从数据库中查
        if (set == null || set.isEmpty()) {
            // redisson获取锁
            RLock lock = redissonClient.getLock("lock:topics:all");
            try {
                boolean b = lock.tryLock(10, -1, TimeUnit.SECONDS);
                // 从数据库中查出来所有的帖子，然后插入到redis中
                if (b) {
                    threadPoolConfig.threadPoolExecutor().execute(() -> {
                        // 把这一堆放到redis的集合当中
                        List<Topic> topics = topicMapper.getAllTopics();
                        for (Topic topic : topics) {
                            LocalDateTime createdAt = topic.getCreatedAt();
                            double score = (double) createdAt.toInstant(java.time.ZoneOffset.of("+8")).toEpochMilli();
                            stringRedisTemplate.opsForZSet().add(key, JSONUtil.toJsonStr(topic), score);
                        }
                    });
                }
                return Result.success("哎呀，一不小心走心了，再试试吧");
            } catch (Exception e) {
                return Result.error("服务器错误");
            } finally {
                lock.unlock();
            }
        } else {
            List<Topic> list = set.stream().map((json) -> {
                return JSONUtil.toBean(json, Topic.class);
            }).toList();
            Long total = stringRedisTemplate.opsForZSet().zCard(key);
            PageResult pageResult = new PageResult(total, list);
            return Result.success(pageResult);
        }
    }

    @Override
    public Result getTopicById(Integer topicId) {
        // 查数据库了，不管了，根据id查感觉也不慢，况且话题本来也不多
        TopicVO topic = topicMapper.getTopicById(topicId);
        return Result.success(topic);
    }

    @Override
    public Result createTopic(TopicDTO topic) {
        // 让ai进行审核。如果内容和图像什么的都可以的话，就返回1，否则的话返回零
        try {
            // 构建审核文本内容
            StringBuilder contentBuilder = new StringBuilder();
            if (topic.getName() != null && !topic.getName().trim().isEmpty()) {
                contentBuilder.append("标题：").append(topic.getName()).append("\n");
            }
            if (topic.getDescription() != null && !topic.getDescription().trim().isEmpty()) {
                contentBuilder.append("描述：").append(topic.getDescription());
            }
            String textContent = contentBuilder.toString().trim();

            // 构建完整的审核提示，包含文本和图片URL
            StringBuilder promptBuilder = new StringBuilder();
            if (!textContent.isEmpty()) {
                promptBuilder.append(textContent);
            }
            if (topic.getCoverImage() != null && !topic.getCoverImage().trim().isEmpty()) {
                if (promptBuilder.length() > 0) {
                    promptBuilder.append("\n\n");
                }
                promptBuilder.append("图片URL：").append(topic.getCoverImage());
                promptBuilder.append("\n请访问以上图片URL并审核图片内容。");
            }

            String fullPrompt = promptBuilder.toString().trim();
            if (fullPrompt.isEmpty()) {
                return Result.error("话题内容不能为空");
            }

            // 调用AI进行审核
            String response = moderationClient.prompt()
                    .user(fullPrompt)
                    .call()
                    .content();

            // 解析返回结果，移除空白字符后提取数字
            String resultStr = response.trim().replaceAll("\\s+", "");
            int result = -1;

            // 尝试从响应中提取0或1
            // 优先查找独立的0或1（前面或后面没有其他数字）
            if (resultStr.matches(".*\\b0\\b.*") && !resultStr.matches(".*\\b10\\b.*")
                    && !resultStr.matches(".*\\b01\\b.*")) {
                result = 0;
            } else if (resultStr.matches(".*\\b1\\b.*")) {
                result = 1;
            } else {
                // 如果无法匹配，尝试直接解析第一个字符
                try {
                    char firstChar = resultStr.charAt(0);
                    if (firstChar == '0' || firstChar == '1') {
                        result = Character.getNumericValue(firstChar);
                    } else {
                        // 查找字符串中的第一个0或1
                        int idx0 = resultStr.indexOf('0');
                        int idx1 = resultStr.indexOf('1');
                        if (idx0 != -1 && (idx1 == -1 || idx0 < idx1)) {
                            result = 0;
                        } else if (idx1 != -1) {
                            result = 1;
                        } else {
                            // 如果还是无法解析，默认返回0（拒绝）
                            result = 0;
                        }
                    }
                } catch (Exception e) {
                    // 如果还是无法解析，默认返回0（拒绝）
                    result = 0;
                }
            }

            // 根据审核结果返回
            if (result == 1) {
                // 审核通过，可以继续处理话题创建逻辑
                // TODO: 在这里添加保存话题到数据库的逻辑
                Topic topicEntity = Topic.builder()
                        .createdAt(LocalDateTime.now())
                        .coverImage(topic.getCoverImage())
                        .description(topic.getDescription())
                        .updatedAt(LocalDateTime.now())
                        .name(topic.getName())
                        .userId(BaseContext.get())
                        .build();
                        topicMapper.insert(topicEntity);
                        //加入redis
                String key = "topics:all";
                stringRedisTemplate.opsForZSet().add(key, JSONUtil.toJsonStr(topicEntity), System.currentTimeMillis());
                return Result.success("审核通过，话题创建成功");
            } else {
                // 审核不通过
                return Result.error("内容审核未通过，包含违规内容");
            }
        } catch (Exception e) {
            // 审核过程中出现异常，为了安全起见，拒绝创建
            return Result.error("审核服务异常，无法创建话题");
        }
    }

    @Override
    public Result followTopic(Integer topicId) {
         //先看看redis里面有没有数据，然后从redis先取出来
        String key = "topic:follow:" + topicId;
        Set<String> range = stringRedisTemplate.opsForZSet().range(key, 0, System.currentTimeMillis());
        //range里面都是用户的id
            //看看有没有呗
            if(range.contains(BaseContext.get().toString())){
                TopicFollowVO topicFollowVO =  TopicFollowVO.builder()
                        .isFollowed(false)
                        .followerCount(range.size()-1)
                        .build();
                stringRedisTemplate.opsForZSet().remove(key,BaseContext.get().toString());
                //把数据库中的改成false，数量减去一
                threadPoolConfig.threadPoolExecutor().execute(() -> {
                    topicMapper.updateFollowCount(topicFollowVO);
                    topicFollowMapper.deleteByTopicId(topicId);
                });
                return Result.success(topicFollowVO);
            }else{
                TopicFollowVO topicFollowVO =  TopicFollowVO.builder()
                        .isFollowed(true)
                        .followerCount(range.size()+1)
                        .build();
                stringRedisTemplate.opsForZSet().add(key,BaseContext.get().toString(),System.currentTimeMillis());
                threadPoolConfig.threadPoolExecutor().execute(() -> {
                    topicMapper.updateFollowCount(topicFollowVO);
                    topicFollowMapper.insert(TopicFollow.builder()
                            .topicId(Long.valueOf(topicId))
                            .userId(BaseContext.get())
                            .createdAt(LocalDateTime.now())
                            .build());
                });
                return Result.success(topicFollowVO);
            }
    }

    @Override
    public Result getTopicFollowers(Integer topicId,Integer page, Integer size) {
        //直接查数据库算了，不想用缓存了，太累了
        PageHelper.startPage(page,size);
        Page<TopicFollowVO> page1=topicFollowMapper.getTopicFollowers(topicId);
        PageResult pageResult = new PageResult(page1.getTotal(),page1.getResult());
        return Result.success(pageResult);
    }
}
