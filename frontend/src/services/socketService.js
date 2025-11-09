/**
 * STOMP WebSocket 메시지 핸들링을 위한 서비스
 */

export const socketService = {
  /**
   * 방 입장
   * @param {Client} client - STOMP 클라이언트
   * @param {string} roomUid - 방 UID
   * @param {string} userKey - 사용자 키
   * @param {Function} onState - 상태 업데이트 콜백
   * @returns {Object} subscription - 구독 객체
   */
  joinRoom: (client, roomUid, userKey, onState) => {
    if (!client || !client.connected) {
      throw new Error('Client not connected');
    }

    // 방 상태 구독
    const subscription = client.subscribe(`/topic/room/${roomUid}/state`, (message) => {
      const data = JSON.parse(message.body);
      console.log('[WebSocket] State update:', data);
      if (onState) onState(data);
    });

    // 방 입장 메시지 전송
    client.publish({
      destination: '/app/room/join',
      body: JSON.stringify({ roomUid, userKey }),
    });

    console.log('[WebSocket] Joined room:', roomUid);
    return subscription;
  },

  /**
   * 번호표 발급/확인
   * @param {Client} client - STOMP 클라이언트
   * @param {string} roomUid - 방 UID
   * @param {string} userKey - 사용자 키
   * @param {Function} onTicket - 티켓 수신 콜백
   * @param {Function} onState - 상태 업데이트 콜백
   * @returns {Object} subscriptions - 구독 객체들
   */
  issueTicket: (client, roomUid, userKey, onTicket, onState) => {
    if (!client || !client.connected) {
      throw new Error('Client not connected');
    }

    const subscriptions = [];

    // 개인 티켓 응답 구독
    const ticketSub = client.subscribe('/user/queue/ticket', (message) => {
      const data = JSON.parse(message.body);
      console.log('[WebSocket] Ticket received:', data);
      if (onTicket) onTicket(data);
    });
    subscriptions.push(ticketSub);

    // 방 상태 업데이트 구독
    const stateSub = client.subscribe(`/topic/room/${roomUid}/state`, (message) => {
      const data = JSON.parse(message.body);
      console.log('[WebSocket] State update:', data);
      if (onState) onState(data);
    });
    subscriptions.push(stateSub);

    // 티켓 발급 요청
    client.publish({
      destination: '/app/room/issue',
      body: JSON.stringify({ roomUid, userKey }),
    });

    return subscriptions;
  },

  /**
   * 특정 번호에 알림 전송 (관리자)
   * @param {Client} client - STOMP 클라이언트
   * @param {string} roomUid - 방 UID
   * @param {number} number - 번호표 번호
   * @param {string} message - 알림 메시지
   */
  sendNotification: (client, roomUid, number, message) => {
    if (!client || !client.connected) {
      throw new Error('Client not connected');
    }

    client.publish({
      destination: '/app/room/notify',
      body: JSON.stringify({ roomUid, number, message }),
    });

    console.log('[WebSocket] Notification sent to number:', number);
  },

  /**
   * 알림 구독 (사용자)
   * @param {Client} client - STOMP 클라이언트
   * @param {string} roomUid - 방 UID
   * @param {string} userKey - 사용자 키
   * @param {Function} onNotification - 알림 수신 콜백
   * @returns {Object} subscription - 구독 객체
   */
  subscribeToNotifications: (client, roomUid, userKey, onNotification) => {
    if (!client || !client.connected) {
      throw new Error('Client not connected');
    }

    const subscription = client.subscribe(
      `/topic/room/${roomUid}/notification/${userKey}`,
      (message) => {
        const data = JSON.parse(message.body);
        console.log('[WebSocket] Notification received:', data);
        if (onNotification) onNotification(data);
      }
    );

    return subscription;
  },
};
