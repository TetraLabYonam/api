import { createContext, useContext, useEffect, useState, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const SocketContext = createContext(null);

export const SocketProvider = ({ children }) => {
  const [client, setClient] = useState(null);
  const [connected, setConnected] = useState(false);
  const clientRef = useRef(null);

  useEffect(() => {
    const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';
    const WS_URL = `${API_URL}/ws`;

    console.log('[WebSocket] Connecting to', WS_URL);

    // STOMP 클라이언트 생성
    const stompClient = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      debug: (str) => {
        console.log('[STOMP Debug]', str);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    stompClient.onConnect = (frame) => {
      console.log('[WebSocket] Connected:', frame);
      setConnected(true);
    };

    stompClient.onDisconnect = () => {
      console.log('[WebSocket] Disconnected');
      setConnected(false);
    };

    stompClient.onStompError = (frame) => {
      console.error('[WebSocket] STOMP error:', frame);
    };

    stompClient.onWebSocketError = (error) => {
      console.error('[WebSocket] WebSocket error:', error);
    };

    stompClient.activate();
    clientRef.current = stompClient;
    setClient(stompClient);

    return () => {
      console.log('[WebSocket] Cleaning up');
      if (clientRef.current) {
        clientRef.current.deactivate();
      }
    };
  }, []);

  return (
    <SocketContext.Provider value={{ client, connected }}>
      {children}
    </SocketContext.Provider>
  );
};

export const useSocketContext = () => {
  const context = useContext(SocketContext);
  if (!context) {
    throw new Error('useSocketContext must be used within SocketProvider');
  }
  return context;
};