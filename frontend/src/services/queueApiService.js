import apiClient from './api';

/**
 * REST API 전용 서비스 (Flutter QueueApiService와 동일)
 * HTTP 요청만 담당
 */
export const queueApiService = {
  /**
   * 활성화된 방 목록 조회
   * @returns {Promise<Array>} 방 목록
   */
  async getActiveRooms() {
    const response = await apiClient.get('/api/queue/rooms');
    return response.data;
  },

  /**
   * 방 정보 조회
   * @param {string} roomUid - 방 UID
   * @returns {Promise<Object>} 방 정보
   */
  async getRoom(roomUid) {
    const response = await apiClient.get(`/api/queue/room/${roomUid}`);
    return response.data;
  },

  /**
   * 번호표 발급 (REST)
   * @param {string} roomUid - 방 UID
   * @param {string} deviceId - 사용자 디바이스 ID
   * @returns {Promise<Object>} 발급된 티켓 정보
   */
  async issueTicket(roomUid, deviceId) {
    const response = await apiClient.post(
      `/api/queue/room/${roomUid}/ticket`,
      { userDeviceId: deviceId }
    );
    return response.data;
  },

  /**
   * 방 현황 조회
   * @param {string} roomUid - 방 UID
   * @returns {Promise<Object>} 방 현황 정보
   */
  async getRoomStatus(roomUid) {
    const response = await apiClient.get(`/api/queue/room/${roomUid}/status`);
    return response.data;
  },

  /**
   * 번호표 목록 조회
   * @param {string} roomUid - 방 UID
   * @returns {Promise<Array>} 번호표 목록
   */
  async getTickets(roomUid) {
    const response = await apiClient.get(`/api/queue/room/${roomUid}/tickets`);
    return response.data;
  },
};
