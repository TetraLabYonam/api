import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

/**
 * WebSocket STOMP 클라이언트 연결 및 구독 관리
 */
const API_URL = import.meta.env.VITE_API_URL || 'http://52.78.88.221';

export const socketService = {
  /**
   * STOMP 클라이언트 생성
   * @param {Function} onConnect - 연결 성공 콜백
   * @param {Function} onError - 에러 콜백
   * @returns {Client} STOMP 클라이언트
   */
  createClient(onConnect, onError) {
    const client = new Client({
      webSocketFactory: () => new SockJS(`${API_URL}/ws`),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log('[WebSocket] 연결됨');
        onConnect?.();
      },
      onStompError: (frame) => {
        console.error('[WebSocket] STOMP 에러:', frame);
        onError?.(frame);
      },
      onWebSocketError: (error) => {
        console.error('[WebSocket] WebSocket 에러:', error);
        onError?.(error);
      },
    });
    return client;
  },

  /**
   * 방 입장 (상태 구독)
   * @param {Client} client - STOMP 클라이언트
   * @param {string} roomUid - 방 UID
   * @param {string} userKey - 사용자 키
   * @param {Function} callback - 상태 업데이트 콜백
   * @returns {Object} subscription - 구독 객체
   */
  joinRoom(client, roomUid, userKey, callback) {
    if (!client || !client.connected) {
      throw new Error('Client not connected');
    }

    const destination = `/topic/room/${roomUid}/state`;

    const subscription = client.subscribe(destination, (message) => {
      const data = JSON.parse(message.body);
      callback(data);
    });

    // 입장 메시지 전송
    client.publish({
      destination: '/app/room/join',
      body: JSON.stringify({ roomUid, userKey }),
    });

    console.log('[WebSocket] 방 입장:', roomUid);
    return subscription;
  },

  /**
   * 개인 티켓 수신 구독
   * @param {Client} client - STOMP 클라이언트
   * @param {Function} callback - 티켓 수신 콜백
   * @returns {Object} subscription - 구독 객체
   */
  subscribeToTicket(client, callback) {
    if (!client || !client.connected) {
      throw new Error('Client not connected');
    }

    return client.subscribe('/user/queue/ticket', (message) => {
      const ticket = JSON.parse(message.body);
      callback(ticket);
    });
  },

  /**
   * 알림 수신 구독
   * @param {Client} client - STOMP 클라이언트
   * @param {string} roomUid - 방 UID
   * @param {string} userKey - 사용자 키
   * @param {Function} callback - 알림 수신 콜백
   * @returns {Object} subscription - 구독 객체
   */
  subscribeToNotification(client, roomUid, userKey, callback) {
    if (!client || !client.connected) {
      throw new Error('Client not connected');
    }

    const destination = `/topic/room/${roomUid}/notification/${userKey}`;
    return client.subscribe(destination, (message) => {
      const notification = JSON.parse(message.body);
      callback(notification);
    });
  },

  /**
   * 번호표 발급 (WebSocket)
   * @param {Client} client - STOMP 클라이언트
   * @param {string} roomUid - 방 UID
   * @param {string} userKey - 사용자 키
   */
  issueTicket(client, roomUid, userKey) {
    if (!client || !client.connected) {
      throw new Error('Client not connected');
    }

    client.publish({
      destination: '/app/room/issue',
      body: JSON.stringify({ roomUid, userKey }),
    });
  },

  /**
   * 특정 번호에 알림 전송 (관리자)
   * @param {Client} client - STOMP 클라이언트
   * @param {string} roomUid - 방 UID
   * @param {number} number - 번호표 번호
   * @param {string} message - 알림 메시지
   */
  sendNotification(client, roomUid, number, message) {
    if (!client || !client.connected) {
      throw new Error('Client not connected');
    }

    client.publish({
      destination: '/app/room/notify',
      body: JSON.stringify({ roomUid, number, message }),
    });

    console.log('[WebSocket] 알림 전송:', number);
  },
};
