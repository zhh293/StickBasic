# 前端通话信令流程与时序

## 概览
- 信令 WebSocket 负责通话的创建、接听、拒绝、结束以及 WebRTC 握手（SDP/ICE）。
- 媒体流推荐使用 WebRTC 端到端传输；服务端仅做信令转发与会话管理。
- 可选旁路：语音/视频房间端点支持二进制分片转发，用于特殊业务（非必须）。

## 端点
- 信令连接：`ws://{host}/ws/call/{userId}`
  - 代码：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:25-27`
- 会话查询：`GET /call/active/{userId}`（登录后用于展示/恢复）
  - 代码：`src/main/java/com/tmd/controller/CallController.java:21-25`
- 旁路房间（可选）：
  - 语音：`ws://{host}/ws/call/voice/{roomId}/{userId}`，`src/main/java/com/tmd/WebSocket/VoiceCallSocket.java:13-41`
  - 视频：`ws://{host}/ws/call/video/{roomId}/{userId}`，`src/main/java/com/tmd/WebSocket/VideoCallSocket.java:13-41`

## 消息结构与动作
- 统一 JSON 字段：`action`、`callId`、`fromUserId`、`toUserId`、`callType`、`payload`、`timestamp`
  - DTO：`src/main/java/com/tmd/entity/dto/call/CallSignalMessage.java:10-19`
- 动作枚举：`INITIATE`、`INITIATE_ACK`、`RINGING`、`ANSWER`、`REJECT`、`CANCEL`、`END`、`ICE`、`SDP_OFFER`、`SDP_ANSWER`、`HEARTBEAT`
  - 枚举：`src/main/java/com/tmd/entity/dto/call/CallAction.java:6-19`
- 服务端路由：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:72-85`

## 主叫（Caller）时序
1. 连接并保活
   - 打开 `ws://{host}/ws/call/{callerUserId}`
   - 接收心跳回执并定时发送 `HEARTBEAT`
   - 代码：握手/心跳 `src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:41-46,171-180`
2. 发起通话
   - 点击“语音通话”发送：
     - `{"action":"INITIATE","toUserId":<calleeId>,"callType":"VOICE"}`
   - 服务端：创建会话并置 `RINGING`
   - 代码：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:88-101`
3. 收到发起确认
   - 接收：`{"action":"INITIATE_ACK","callId":"...","toUserId":<calleeId>,"callType":"VOICE"}`
   - 前端：保存 `callId`，显示“已呼叫，等待对方”
   - 代码：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:112-119`
4. WebRTC 握手（Caller 侧）
   - 创建本地音频轨与 `RTCPeerConnection`
   - 生成 `offer` 并发送：
     - `{"action":"SDP_OFFER","callId":"...","payload":{"sdp":"..."}}`
   - 收集 ICE 候选并逐条发送：
     - `{"action":"ICE","callId":"...","payload":{"candidate":"..."}}`
   - 代码：信令转发 `src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:159-169`
5. 接收对端接听与应答 SDP
   - 接收：`{"action":"ANSWER","callId":"..."}`（服务端置 `ACTIVE`）
   - 接收：`{"action":"SDP_ANSWER","payload":{"sdp":"..."}}`，设置远端描述
   - 代码：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:121-132,129`
6. 结束或取消
   - 通话中结束：`{"action":"END","callId":"..."}`
   - 响铃阶段取消：`{"action":"CANCEL","callId":"..."}`
   - 代码：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:147-157,134-145`

## 被叫（Callee）时序
1. 连接并保活
   - 打开 `ws://{host}/ws/call/{calleeUserId}`
   - 定时 `HEARTBEAT`
   - 代码：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:41-46,171-180`
2. 来电提醒
   - 接收：`{"action":"RINGING","callId":"...","fromUserId":<callerId>,"callType":"VOICE"}`
   - 前端：展示来电 UI
   - 代码：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:102-111`
3. 接听或拒绝
   - 接听发送：`{"action":"ANSWER","callId":"..."}`（服务端置 `ACTIVE`，并转发给主叫）
   - 拒绝发送：`{"action":"REJECT","callId":"..."}`（主叫收到拒绝）
   - 代码：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:121-132,134-145`
4. WebRTC 握手（Callee 侧）
   - 创建本地音频轨与 `RTCPeerConnection`
   - 收到主叫 `SDP_OFFER` 后，设置远端描述并生成 `answer` 发送：
     - `{"action":"SDP_ANSWER","callId":"...","payload":{"sdp":"..."}}`
   - 收集并发送 `ICE`
   - 代码：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:159-169`
5. 结束
   - 发送：`{"action":"END","callId":"..."}`
   - 代码：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:147-157`

## 请求/响应去向与转发
- 前端所有上行消息发往：`ws://{host}/ws/call/{当前用户ID}`
- 服务端路由：`route(...)` 根据 `action` 分发，位置：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:72-85`
- 会话管理：创建/更新/查找，位置：`src/main/java/com/tmd/service/impl/CallSessionServiceImpl.java:31-79`
- 对端转发：按会话确定目标用户，查 `CONNECTIONS` 并 `send(target, message)`，位置：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:159-169,198-214`

## 心跳与清理
- 前端：定时发送 `{"action":"HEARTBEAT"}`
- 服务端：更新会话与连接的最近活动时间并回执，位置：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:171-180`
- 连接清理：心跳超时关闭，位置：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:216-232`

## 错误示例
- 被叫不在线：主叫收到 `{"action":"ERROR","reason":"USER_OFFLINE"}`，位置：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:94-97,182-189`
- 无效载荷：`{"action":"ERROR","reason":"INVALID_PAYLOAD"}`，位置：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:48-57,182-189`
- 会话不存在：`{"action":"ERROR","reason":"CALL_NOT_FOUND"}`，位置：`src/main/java/com/tmd/WebSocket/CallWebSocketServer.java:121-125,135-138,148-151,160-163`

## 旁路二进制（可选）
- 仅当不能用 WebRTC 或需旁路传输/录制时使用。
- 前端连接：语音 `/ws/call/voice/{roomId}/{userId}`、视频 `/ws/call/video/{roomId}/{userId}`
- 文本信令：`onMessage(String)` 房间广播并附 `channel` 标签，位置：`src/main/java/com/tmd/WebSocket/VoiceCallSocket.java:25-30`, `VideoCallSocket.java:25-30`
- 二进制：`onBinary(ByteBuffer)` 分片 64KB 转发，位置：`src/main/java/com/tmd/WebSocket/VoiceCallSocket.java:31-40`, `VideoCallSocket.java:31-40`
- 广播与限流：`CallRoomManager.relay/relayBinary`，令牌桶+有界队列+批量下发，位置：`src/main/java/com/tmd/WebSocket/CallRoomManager.java:33-41,44-58,60-70,73-92`

## 最小前端动作清单
- 打开信令 WebSocket，心跳保活
- 主叫：`INITIATE`→收 `INITIATE_ACK`→`SDP_OFFER`+`ICE`→收 `ANSWER`→收 `SDP_ANSWER`→媒体建立→`END`
- 被叫：收 `RINGING`→`ANSWER`→`SDP_ANSWER`+`ICE`→媒体建立→`END`
- 错误处理：根据 `ERROR.reason` 提示用户；断线重连；清理资源