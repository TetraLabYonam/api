import apiClient from './api';

export const roomService = {
  /**
   * 방 목록 조회
   */
  getRooms: async () => {
    const response = await apiClient.get('/api/rooms');
    return response.data;
  },

  /**
   * 방 상세 정보 조회 (관리자용)
   */
  getRoomDetails: async () => {
    const response = await apiClient.get('/api/rooms/details');
    return response.data;
  },

  /**
   * 방 생성
   * @param {string} title - 방 제목
   */
  createRoom: async (title) => {
    const response = await apiClient.post('/api/rooms', `title=${encodeURIComponent(title)}`, {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
    });
    return response.data;
  },

  /**
   * 방 초기화
   * @param {string} roomUid - 방 UID
   */
  resetRoom: async (roomUid) => {
    const response = await apiClient.post(`/api/rooms/${roomUid}/reset`);
    return response.data;
  },

  /**
   * 발급 목록 조회
   * @param {string} roomUid - 방 UID
   */
  getIssuances: async (roomUid) => {
    const response = await apiClient.get(`/api/rooms/${roomUid}/issuances`);
    return response.data;
  },
};