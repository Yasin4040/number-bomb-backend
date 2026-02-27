/**
 * 微信小程序 WebSocket 连接示例
 * 使用原生 WebSocket API，支持 STOMP 协议
 */

class WebSocketClient {
  constructor(options = {}) {
    this.url = options.url || 'wss://your-domain.com/ws';
    this.token = options.token || '';
    this.userId = options.userId || '';
    this.socketTask = null;
    this.connected = false;
    this.reconnectTimer = null;
    this.heartbeatTimer = null;
    this.messageHandlers = new Map();
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
  }

  /**
   * 连接 WebSocket
   */
  connect() {
    return new Promise((resolve, reject) => {
      const wsUrl = `${this.url}?token=${this.token}&userId=${this.userId}`;
      
      this.socketTask = wx.connectSocket({
        url: wsUrl,
        header: {
          'Authorization': `Bearer ${this.token}`
        },
        success: () => {
          console.log('WebSocket 连接请求已发送');
        },
        fail: (err) => {
          console.error('WebSocket 连接失败:', err);
          reject(err);
        }
      });

      // 监听连接打开
      this.socketTask.onOpen(() => {
        console.log('WebSocket 连接已打开');
        this.connected = true;
        this.reconnectAttempts = 0;
        this.startHeartbeat();
        resolve();
      });

      // 监听消息
      this.socketTask.onMessage((res) => {
        try {
          const message = JSON.parse(res.data);
          this.handleMessage(message);
        } catch (error) {
          console.error('解析消息失败:', error, res.data);
        }
      });

      // 监听错误
      this.socketTask.onError((err) => {
        console.error('WebSocket 错误:', err);
        this.connected = false;
        reject(err);
      });

      // 监听连接关闭
      this.socketTask.onClose((res) => {
        console.log('WebSocket 连接已关闭:', res);
        this.connected = false;
        this.stopHeartbeat();
        this.handleReconnect();
      });
    });
  }

  /**
   * 处理接收到的消息
   */
  handleMessage(message) {
    const { type, data, error, timestamp } = message;
    
    console.log('收到消息:', type, data);

    // 处理心跳
    if (type === 'PING') {
      this.sendPong();
      return;
    }

    if (type === 'PONG') {
      return;
    }

    // 处理错误
    if (type === 'ERROR') {
      console.error('服务器错误:', error);
      wx.showToast({
        title: error || '服务器错误',
        icon: 'none'
      });
      return;
    }

    // 调用对应的消息处理器
    const handler = this.messageHandlers.get(type);
    if (handler) {
      handler(data);
    } else {
      console.warn('未处理的消息类型:', type);
    }
  }

  /**
   * 注册消息处理器
   */
  onMessageType(type, handler) {
    this.messageHandlers.set(type, handler);
  }

  /**
   * 发送消息（STOMP 格式）
   */
  send(destination, body) {
    if (!this.connected) {
      console.warn('WebSocket 未连接，无法发送消息');
      return;
    }

    const message = JSON.stringify({
      destination: destination,
      body: body
    });

    this.socketTask.send({
      data: message,
      success: () => {
        console.log('消息发送成功:', destination);
      },
      fail: (err) => {
        console.error('消息发送失败:', err);
      }
    });
  }

  /**
   * 发送心跳响应
   */
  sendPong() {
    const message = JSON.stringify({
      type: 'PONG',
      timestamp: Date.now()
    });
    this.socketTask.send({ data: message });
  }

  /**
   * 开始心跳
   */
  startHeartbeat() {
    this.heartbeatTimer = setInterval(() => {
      if (this.connected) {
        const ping = JSON.stringify({
          type: 'PING',
          timestamp: Date.now()
        });
        this.socketTask.send({ data: ping });
      }
    }, 30000); // 每30秒发送一次心跳
  }

  /**
   * 停止心跳
   */
  stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  /**
   * 处理重连
   */
  handleReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('达到最大重连次数，停止重连');
      wx.showToast({
        title: '连接失败，请检查网络',
        icon: 'none'
      });
      return;
    }

    this.reconnectAttempts++;
    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
    
    console.log(`${delay}ms 后尝试重连 (${this.reconnectAttempts}/${this.maxReconnectAttempts})`);
    
    this.reconnectTimer = setTimeout(() => {
      this.connect().catch(err => {
        console.error('重连失败:', err);
      });
    }, delay);
  }

  /**
   * 断开连接
   */
  disconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.stopHeartbeat();
    
    if (this.socketTask) {
      this.socketTask.close({
        success: () => {
          console.log('WebSocket 已断开');
        }
      });
      this.socketTask = null;
    }
    this.connected = false;
  }
}

// 使用示例
export function initWebSocket(token, userId) {
  const wsClient = new WebSocketClient({
    url: 'wss://your-domain.com/ws',
    token: token,
    userId: userId
  });

  // 注册消息处理器
  wsClient.onMessageType('MATCH_SUCCESS', (data) => {
    console.log('匹配成功:', data);
    const { opponent, countdown } = data;
    // 显示匹配成功界面
    wx.showToast({
      title: `匹配成功！对手：${opponent.nickname}`,
      icon: 'success'
    });
    // 跳转到游戏准备页面
    // wx.navigateTo({ url: `/pages/game/prepare?opponentId=${opponent.id}` });
  });

  wsClient.onMessageType('GAME_GUESS_RESULT', (data) => {
    console.log('猜测结果:', data);
    const { playerId, guess, a, b, isGameOver } = data;
    // 更新游戏界面，显示猜测结果
    // updateGameUI({ playerId, guess, a, b, isGameOver });
  });

  wsClient.onMessageType('GAME_TURN_CHANGED', (data) => {
    console.log('回合切换:', data);
    const { currentPlayerId, countdown } = data;
    // 更新当前回合玩家和倒计时
    // updateTurnUI({ currentPlayerId, countdown });
  });

  wsClient.onMessageType('GAME_ENDED', (data) => {
    console.log('游戏结束:', data);
    const { winnerId, loserId, punishment, winnerSecret, loserSecret } = data;
    // 显示游戏结束界面
    // showGameEndUI({ winnerId, loserId, punishment, winnerSecret, loserSecret });
  });

  wsClient.onMessageType('ROOM_GAME_STARTED', (data) => {
    console.log('游戏开始:', data);
    const { gameId, roomId } = data;
    // 跳转到游戏页面
    // wx.navigateTo({ url: `/pages/game/playing?gameId=${gameId}&roomId=${roomId}` });
  });

  // 连接 WebSocket
  wsClient.connect().then(() => {
    console.log('WebSocket 连接成功');
  }).catch(err => {
    console.error('WebSocket 连接失败:', err);
  });

  return wsClient;
}

// 在页面中使用
// const app = getApp();
// const wsClient = initWebSocket(app.globalData.token, app.globalData.userId);
// 
// // 页面卸载时断开连接
// onUnload() {
//   if (wsClient) {
//     wsClient.disconnect();
//   }
// }
