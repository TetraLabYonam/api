import { create } from 'zustand';
import { socketService } from '../services/socketService';

/**
 * WebSocket 상태 관리 (Zustand)
 */
export const useSocketStore = create((set, get) => ({
  client: null,
  connected: false,

  /**
   * WebSocket 연결
   */
  connect: () => {
    const { client: existingClient } = get();
    
    // 이미 연결되어 있으면 재연결하지 않음
    if (existingClient && existingClient.connected) {
      return;
    }

    const client = socketService.createClient(
      () => {
        console.log('[SocketStore] 연결 성공');
        set({ connected: true });
      },
      () => {
        console.log('[SocketStore] 연결 실패');
        set({ connected: false });
      }
    );

    client.activate();
    set({ client });
  },

  /**
   * WebSocket 연결 해제
   */
  disconnect: () => {
    const { client } = get();
    if (client) {
      client.deactivate();
    }
    set({ client: null, connected: false });
  },
}));

