/**
 * Web 端 WebSocket 连接示例
 * 使用 SockJS + STOMP.js
 */

// 需要引入 SockJS 和 STOMP.js
// <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
// <script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js"></script>

class WebSocketClient {
  constructor(options = {}) {
    this.url = options.url || 'http://localhost:8888/ws';
    this.token = options.token || '';
    this.userId = options.userId || '';
    this.socket = null;
    this.stompClient = null;
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
      // 创建 SockJS 连接
      this.socket = new SockJS(`${this.url}?token=${this.token}&userId=${this.userId}`);
      
      // 创建 STOMP 客户端
      this.stompClient = new StompJs.Client({
        webSocketFactory: () => this.socket,
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        debug: (str) => {
          console.log('STOMP:', str);
        }
      });

      // 连接成功回调
      this.stompClient.onConnect = (frame) => {
        console.log('WebSocket 连接成功:', frame);
        this.connected = true;
        this.reconnectAttempts = 0;
        
        // 订阅用户专属消息队列
        this.subscribeToUserQueue();
        
        // 订阅游戏相关 topic（如果需要）
        // this.subscribeToGameTopics();
        
        resolve();
      };

      // 连接错误回调
      this.stompClient.onStompError = (frame) => {
        console.error('STOMP 错误:', frame);
        this.connected = false;
        reject(new Error(frame.headers['message'] || '连接错误'));
      };

      // WebSocket 错误回调
      this.socket.onerror = (error) => {
        console.error('WebSocket 错误:', error);
        this.connected = false;
        reject(error);
      };

      // WebSocket 关闭回调
      this.socket.onclose = () => {
        console.log('WebSocket 连接已关闭');
        this.connected = false;
        this.handleReconnect();
      };

      // 激活 STOMP 客户端
      this.stompClient.activate();
    });
  }

  /**
   * 订阅用户专属消息队列
   */
  subscribeToUserQueue() {
    const destination = `/user/${this.userId}/queue/messages`;
    
    this.stompClient.subscribe(destination, (message) => {
      try {
        const data = JSON.parse(message.body);
        this.handleMessage(data);
      } catch (error) {
        console.error('解析消息失败:', error, message.body);
      }
    });
    
    console.log('已订阅用户消息队列:', destination);
  }

  /**
   * 订阅游戏 topic（可选）
   */
  subscribeToGameTopic(gameId) {
    const destination = `/topic/game/${gameId}`;
    
    return this.stompClient.subscribe(destination, (message) => {
      try {
        const data = JSON.parse(message.body);
        this.handleMessage(data);
      } catch (error) {
        console.error('解析消息失败:', error, message.body);
      }
    });
  }

  /**
   * 订阅房间 topic（可选）
   */
  subscribeToRoomTopic(roomId) {
    const destination = `/topic/room/${roomId}`;
    
    return this.stompClient.subscribe(destination, (message) => {
      try {
        const data = JSON.parse(message.body);
        this.handleMessage(data);
      } catch (error) {
        console.error('解析消息失败:', error, message.body);
      }
    });
  }

  /**
   * 处理接收到的消息
   */
  handleMessage(message) {
    const { type, data, error, timestamp } = message;
    
    console.log('收到消息:', type, data);

    // 处理心跳
    if (type === 'PING' || type === 'PONG') {
      return;
    }

    // 处理错误
    if (type === 'ERROR') {
      console.error('服务器错误:', error);
      alert(error || '服务器错误');
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
   * 发送消息
   */
  send(destination, body) {
    if (!this.connected || !this.stompClient) {
      console.warn('WebSocket 未连接，无法发送消息');
      return;
    }

    this.stompClient.publish({
      destination: destination,
      body: JSON.stringify(body)
    });
  }

  /**
   * 处理重连
   */
  handleReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('达到最大重连次数，停止重连');
      alert('连接失败，请刷新页面重试');
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
    
    if (this.stompClient) {
      this.stompClient.deactivate();
      this.stompClient = null;
    }
    
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
    
    this.connected = false;
  }
}

// 使用示例
export function initWebSocket(token, userId) {
  const wsClient = new WebSocketClient({
    url: 'http://localhost:8888/ws',
    token: token,
    userId: userId
  });

  // 注册消息处理器
  wsClient.onMessageType('MATCH_SUCCESS', (data) => {
    console.log('匹配成功:', data);
    const { opponent, countdown } = data;
    // 显示匹配成功界面
    alert(`匹配成功！对手：${opponent.nickname}`);
    // 跳转到游戏准备页面
    // window.location.href = `/game/prepare?opponentId=${opponent.id}`;
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
    // window.location.href = `/game/playing?gameId=${gameId}&roomId=${roomId}`;
  });

  wsClient.onMessageType('ROOM_JOINED', (data) => {
    console.log('玩家加入房间:', data);
    // 更新房间玩家列表
  });

  wsClient.onMessageType('ROOM_PLAYER_READY', (data) => {
    console.log('玩家准备状态变化:', data);
    // 更新玩家准备状态
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
// const wsClient = initWebSocket(localStorage.getItem('token'), localStorage.getItem('userId'));
// 
// // 订阅特定游戏
// const gameId = 123;
// wsClient.subscribeToGameTopic(gameId);
// 
// // 订阅特定房间
// const roomId = 456;
// wsClient.subscribeToRoomTopic(roomId);
// 
// // 页面卸载时断开连接
// window.addEventListener('beforeunload', () => {
//   if (wsClient) {
//     wsClient.disconnect();
//   }
// });
