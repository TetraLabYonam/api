import { useEffect } from 'react';

/**
 * Socket.IO 이벤트를 구독하는 커스텀 훅
 * @param {Socket} socket - Socket.IO 인스턴스
 * @param {string} eventName - 이벤트 이름
 * @param {Function} handler - 이벤트 핸들러
 */
export const useSocketEvent = (socket, eventName, handler) => {
  useEffect(() => {
    if (!socket) return;

    console.log(`[Socket] Subscribing to event: ${eventName}`);
    socket.on(eventName, handler);

    return () => {
      console.log(`[Socket] Unsubscribing from event: ${eventName}`);
      socket.off(eventName, handler);
    };
  }, [socket, eventName, handler]);
};