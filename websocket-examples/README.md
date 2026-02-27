# WebSocket 连接示例代码

本目录包含前端 WebSocket 连接示例代码，支持微信小程序和 Web 端。

## 文件说明

- `wechat-miniprogram-websocket.js` - 微信小程序 WebSocket 连接示例
- `web-websocket.js` - Web 端 WebSocket 连接示例（使用 SockJS + STOMP.js）

## 消息类型

### 匹配相关
- `MATCH_SUCCESS` - 匹配成功
- `MATCH_STATUS_UPDATED` - 匹配状态更新
- `MATCH_CANCELLED` - 匹配取消

### 游戏相关
- `GAME_TURN_CHANGED` - 回合切换
- `GAME_GUESS_RESULT` - 猜测结果
- `GAME_ENDED` - 游戏结束
- `GAME_UPDATED` - 游戏状态更新

### 房间相关
- `ROOM_JOINED` - 玩家加入房间
- `ROOM_LEFT` - 玩家离开房间
- `ROOM_PLAYER_READY` - 玩家准备状态变化
- `ROOM_SECRET_SET` - 玩家设置数字
- `ROOM_TURN_ORDER_SET` - 玩家选择先手/后手
- `ROOM_START_COUNTDOWN` - 开始倒计时
- `ROOM_GAME_STARTED` - 游戏开始
- `ROOM_UPDATED` - 房间信息更新

### 系统消息
- `PING` - 心跳
- `PONG` - 心跳响应
- `ERROR` - 错误消息
- `NOTIFICATION` - 通知消息

## 消息格式

```javascript
{
  type: "GAME_GUESS_RESULT",
  data: {
    gameId: 123,
    playerId: 456,
    guess: "1234",
    a: 2,
    b: 1,
    isGameOver: false
  },
  timestamp: 1234567890,
  error: null
}
```

## 使用说明

### 微信小程序

1. 将 `wechat-miniprogram-websocket.js` 复制到小程序项目中
2. 在页面中引入并使用：

```javascript
import { initWebSocket } from '../../utils/wechat-miniprogram-websocket';

Page({
  onLoad() {
    const app = getApp();
    this.wsClient = initWebSocket(
      app.globalData.token,
      app.globalData.userId
    );
  },
  
  onUnload() {
    if (this.wsClient) {
      this.wsClient.disconnect();
    }
  }
});
```

### Web 端

1. 引入 SockJS 和 STOMP.js：

```html
<script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js"></script>
```

2. 引入并使用：

```javascript
import { initWebSocket } from './web-websocket';

const wsClient = initWebSocket(
  localStorage.getItem('token'),
  localStorage.getItem('userId')
);

// 订阅特定游戏
const gameId = 123;
wsClient.subscribeToGameTopic(gameId);
```

## 注意事项

1. **连接地址**：需要根据实际部署环境修改 WebSocket 连接地址
2. **认证**：连接时需要传递 token 和 userId
3. **重连机制**：已实现自动重连，最多重试 5 次
4. **心跳**：微信小程序版本实现了心跳机制，Web 端由 STOMP.js 自动处理
5. **消息处理**：所有消息都会通过注册的处理器进行处理

## 消息处理示例

```javascript
// 处理匹配成功
wsClient.onMessageType('MATCH_SUCCESS', (data) => {
  const { opponent, countdown } = data;
  console.log('匹配成功，对手：', opponent.nickname);
  // 更新 UI
});

// 处理猜测结果
wsClient.onMessageType('GAME_GUESS_RESULT', (data) => {
  const { playerId, guess, a, b, isGameOver } = data;
  console.log(`玩家 ${playerId} 猜测 ${guess}，结果：${a}A${b}B`);
  // 更新游戏界面
});

// 处理游戏结束
wsClient.onMessageType('GAME_ENDED', (data) => {
  const { winnerId, loserId, punishment } = data;
  console.log('游戏结束，获胜者：', winnerId);
  // 显示游戏结束界面
});
```
