import { queueApiService } from './queueApiService';
import { socketService } from './socketService';
import { getUserKey } from './userKeyService';
import apiClient from './api';

/**
 * 통합 서비스 (REST + WebSocket)
 * Flutter의 QueueServiceProvider와 동일한 패턴
 */
export class QueueService {
  constructor() {
    this.client = null;
    this.deviceId = getUserKey();
    this.subscriptions = [];

    // 콜백 함수들 (Flutter의 notifyListeners 패턴과 동일)
    this.onRoomStatusUpdate = null;
    this.onTicketIssued = null;
    this.onNotification = null;
  }

  /**
   * WebSocket 클라이언트 설정
   * @param {Client} client - STOMP 클라이언트
   */
  setClient(client) {
    this.client = client;
  }

  /**
   * 방 입장 (WebSocket 구독 시작)
   * @param {string} roomUid - 방 UID
   */
  joinRoom(roomUid) {
    if (!this.client || !this.client.connected) {
      console.warn('WebSocket이 연결되지 않았습니다.');
      return;
    }

    // 1. 방 상태 구독
    const stateSub = socketService.joinRoom(
      this.client,
      roomUid,
      this.deviceId,
      (data) => {
        console.log('[QueueService] 방 상태 업데이트:', data);
        this.onRoomStatusUpdate?.(data);
      }
    );
    this.subscriptions.push(stateSub);

    // 2. 개인 티켓 수신 구독
    const ticketSub = socketService.subscribeToTicket(
      this.client,
      (ticket) => {
        console.log('[QueueService] 티켓 발급됨:', ticket);
        this.onTicketIssued?.(ticket);
      }
    );
    this.subscriptions.push(ticketSub);

    // 3. 알림 수신 구독
    const notifSub = socketService.subscribeToNotification(
      this.client,
      roomUid,
      this.deviceId,
      (notification) => {
        console.log('[QueueService] 알림 수신:', notification);
        this.onNotification?.(notification);
      }
    );
    this.subscriptions.push(notifSub);
  }

  /**
   * 방 나가기 (구독 해제)
   */
  leaveRoom() {
    this.subscriptions.forEach((sub) => {
      if (sub && sub.unsubscribe) {
        sub.unsubscribe();
      }
    });
    this.subscriptions = [];
  }

  /**
   * 번호표 발급 (REST API 사용)
   * @param {string} roomUid - 방 UID
   * @returns {Promise<Object>} 발급된 티켓
   */
  async issueTicketViaRest(roomUid) {
    const ticket = await queueApiService.issueTicket(roomUid, this.deviceId);
    this.onTicketIssued?.(ticket);
    return ticket;
  }

  /**
   * 방 현황 새로고침 (REST API)
   * @param {string} roomUid - 방 UID
   * @returns {Promise<Object>} 방 현황
   */
  async refreshRoomStatus(roomUid) {
    const status = await queueApiService.getRoomStatus(roomUid);
    this.onRoomStatusUpdate?.(status);
    return status;
  }

  /**
   * 다음 번호 호출 (관리자)
   * @param {string} roomUid - 방 UID
   * @returns {Promise<Object>} 호출 결과
   */
  async callNextNumber(roomUid) {
    const response = await apiClient.post(`/api/queue/room/${roomUid}/call`);
    return response.data;
  }
}

// 싱글톤 인스턴스
export const queueService = new QueueService();
