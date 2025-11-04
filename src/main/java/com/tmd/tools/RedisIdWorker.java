package com.tmd.tools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final long COUNT_BITS = 32;
    public long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        //生成序列号
        //获取当前日期
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //icr后面的:可以将不同的业务区分开，每个业务有自己独特的一套序列号,每一个业务keyprefix后面也有一个:,为了分层和美观。。。上面对于日期的格式进行了修改，不同时间单位后面添加了:
        //这么做非常细节，根据年月日分层，在之后想要统计某一天或者某一月，某一年的订单量的时候会非常的容易,如下图演示
        /*icr:
        *    keyprefix:
        *              年:
        *                 月:
        *                    日:
        *                      1.当天的订单数据和信息
        *                      2.。。。。。。。。。 */
        Long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        return timestamp << COUNT_BITS | increment;
    }

}
/*
位运算原理分析
1. 基本结构
return timestamp << COUNT_BITS | increment;
这个表达式由两部分组成：
timestamp << COUNT_BITS：时间戳左移32位
increment：Redis自增序列号
|：按位或运算符，将两部分合并
2. 详细分解
        时间戳部分
LocalDateTime now = LocalDateTime.now();
long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
long timestamp = nowSecond - BEGIN_TIMESTAMP;
获取当前时间戳（秒级）
减去基准时间戳 BEGIN_TIMESTAMP = 1640995200L（2022-01-01 00:00:00 UTC）
这样可以节省位数，避免时间戳过大

        序列号部分

String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
Long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
使用Redis的INCR命令生成每日自增序列号
        每天序列号从1开始重新计数
return timestamp << COUNT_BITS | increment;
位运算示例演示
假设：
timestamp = 1000000（32位二进制：00000000000011110100001001000000）
increment = 123（32位二进制：00000000000000000000000001111011）
执行过程：
timestamp << 32：
原始timestamp:     00000000000011110100001001000000
左移32位后:        00000000000011110100001001000000 00000000000000000000000000000000
        (高32位为原始timestamp，低32位为0)
        | increment：
左移后的timestamp: 00000000000011110100001001000000 00000000000000000000000000000000
increment:         00000000000000000000000000000000 00000000000000000000000001111011
按位或结果:        00000000000011110100001001000000 00000000000000000000000001111011

这种设计充分利用了64位long类型的存储空间，将时间和序列号巧妙地结合在一起，既保证了ID的唯一性，又使得ID具有时间有序性，是一种经典的分布式ID生成方案。

3. 位运算原理
位运算符在计算机中用于对二进制数进行运算。位运算符的基本原理是将二进制数转换成二进制位，然后进行运算，最后将运算结果转换回二进制数。

位运算符在计算机中的作用如下：

位运算符用于对二进制数进行运算。
位运算符可以进行位运算，例如位与、位或、位异或、位取反、位左移、位右移等。
位运算符可以进行位运算，例如位与、位或、位异或、位取反、位左移、位右移等。
*/




/*具体数字示例
示例1：简单数字运算
int a = 5;   // 二进制: 0101
int b = 3;   // 二进制: 0011
int result = a | b;  // 按位或运算

// 计算过程：
//   0101  (a = 5)
// | 0011  (b = 3)
// ------
//   0111  (result = 7)

System.out.println(result); // 输出: 7



// 假设：
long timestamp = 6;     // 二进制: 0000000000000000000000000000000000000000000000000000000000000110
long increment = 5;     // 二进制: 0000000000000000000000000000000000000000000000000000000000000101

// 执行左移操作
long shiftedTimestamp = timestamp << 32;
// 结果: 00000000000000000000000000000110 00000000000000000000000000000000

// 执行按位或运算
long id = shiftedTimestamp | increment;

shiftedTimestamp: 00000000000000000000000000000110 00000000000000000000000000000000
increment:        00000000000000000000000000000000 00000000000000000000000000000101
        ----------------------------------------------------------------------------------
id结果:           00000000000000000000000000000110 00000000000000000000000000000101


转换为十进制：257698037765*/

//哟西，懂了