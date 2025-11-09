import { useEffect, useState, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useSocketContext } from '../contexts/SocketContext';
import { roomService } from '../services/roomService';
import { socketService } from '../services/socketService';
import { getUserKey } from '../utils/userKey';
import Button from '../components/common/Button';
import './RoomPage.css';

const RoomPage = () => {
  const { roomUid } = useParams();
  const navigate = useNavigate();
  const { client, connected } = useSocketContext();

  const [rooms, setRooms] = useState([]);
  const [currentRoom, setCurrentRoom] = useState(null);
  const [myTicket, setMyTicket] = useState(null);
  const [notification, setNotification] = useState(null);
  const [issuances, setIssuances] = useState([]);
  const [showIssuances, setShowIssuances] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const userKey = getUserKey();
  const subscriptionsRef = useRef([]);

  // 방 목록 로드
  useEffect(() => {
    if (!roomUid) {
      loadRooms();
    }
  }, [roomUid]);

  // 방 입장 및 WebSocket 연결
  useEffect(() => {
    if (!roomUid || !client || !connected) return;

    try {
      console.log('[Room] Joining room:', roomUid);

      // 방 상태 구독
      const subscription = socketService.joinRoom(
        client,
        roomUid,
        userKey,
        (data) => {
          console.log('[Room] State update:', data);
        }
      );
      subscriptionsRef.current.push(subscription);

      // 알림 구독
      const notificationSub = socketService.subscribeToNotifications(
        client,
        roomUid,
        userKey,
        (data) => {
          console.log('[Room] Notification received:', data);
          setNotification(data);
        }
      );
      subscriptionsRef.current.push(notificationSub);

      setCurrentRoom({ roomUid });
    } catch (error) {
      console.error('[Room] Failed to join:', error);
      setError('방에 입장할 수 없습니다.');
    }

    // Cleanup
    return () => {
      subscriptionsRef.current.forEach((sub) => {
        if (sub && sub.unsubscribe) {
          sub.unsubscribe();
        }
      });
      subscriptionsRef.current = [];
    };
  }, [roomUid, client, connected, userKey]);

  const loadRooms = async () => {
    try {
      setLoading(true);
      const data = await roomService.getRooms();
      setRooms(data);
    } catch (error) {
      console.error('Failed to load rooms:', error);
      setError('방 목록을 불러올 수 없습니다.');
    } finally {
      setLoading(false);
    }
  };

  const selectRoom = (room) => {
    navigate(`/rooms/${room.roomUid}`);
  };

  const handleCheckTicket = () => {
    if (!client || !connected || !roomUid) {
      alert('WebSocket이 연결되지 않았습니다.');
      return;
    }

    try {
      socketService.issueTicket(
        client,
        roomUid,
        userKey,
        (result) => {
          // 티켓 수신
          console.log('[Room] Ticket result:', result);
          setMyTicket({
            number: result.number,
            duplicated: result.duplicated,
          });
        },
        (state) => {
          // 상태 업데이트
          console.log('[Room] State update:', state);
        }
      );
    } catch (error) {
      console.error('[Room] Failed to issue ticket:', error);
      alert('번호표를 확인할 수 없습니다.');
    }
  };

  const handleShowIssuances = async () => {
    if (!roomUid) return;

    try {
      const data = await roomService.getIssuances(roomUid);
      setIssuances(data);
      setShowIssuances(true);
    } catch (error) {
      console.error('Failed to load issuances:', error);
      alert('발급 목록을 불러올 수 없습니다.');
    }
  };

  const backToRoomList = () => {
    setCurrentRoom(null);
    setMyTicket(null);
    setNotification(null);
    setShowIssuances(false);
    navigate('/rooms');
  };

  const closeNotification = () => {
    setNotification(null);
  };

  // 방 목록 화면
  if (!roomUid) {
    return (
      <div className="room-page">
        <div className="room-list-section">
          <h3>방 목록</h3>
          <Button onClick={loadRooms} variant="secondary">
            새로고침
          </Button>

          {loading && <p>로딩 중...</p>}
          {error && <p className="error">{error}</p>}

          <div className="room-list">
            {rooms.length === 0 && !loading && (
              <p>생성된 방이 없습니다.</p>
            )}
            {rooms.map((room) => (
              <div
                key={room.roomUid}
                className="room-card"
                onClick={() => selectRoom(room)}
              >
                <div className="room-title">{room.title || '제목 없음'}</div>
                <div className="room-uid">방 코드: {room.roomUid}</div>
                <div className="room-number">
                  현재 번호: {room.currentNumber}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    );
  }

  // 방 상세 화면
  return (
    <div className="room-page">
      <div className="ticket-section">
        <Button onClick={backToRoomList} variant="secondary">
          ← 방 목록으로
        </Button>

        <div className="selected-room">
          <h3>번호표 시스템</h3>
          <div>방 코드: <strong>{roomUid}</strong></div>
          {connected ? (
            <div className="connection-status connected">
              ✓ 방에 자동으로 접속되었습니다
            </div>
          ) : (
            <div className="connection-status disconnected">
              ⚠ 연결 중...
            </div>
          )}
        </div>

        <div className="action-buttons">
          <Button onClick={handleCheckTicket} variant="primary">
            번호표 확인
          </Button>
          <Button onClick={handleShowIssuances} variant="secondary">
            발급 목록 보기
          </Button>
        </div>

        {/* 내 번호표 표시 */}
        {myTicket && (
          <div className="my-ticket-area">
            <h3>🎫 내 번호표</h3>
            <div className="ticket-number">{myTicket.number}</div>
            <div className="ticket-status">
              {myTicket.duplicated
                ? '이미 발급받은 번호표입니다'
                : '새로 발급된 번호표입니다'}
            </div>
          </div>
        )}

        {/* 알림 표시 */}
        {notification && (
          <div className="notification-area">
            <h4>📢 알림</h4>
            <div className="notification-content">
              <strong>번호 {notification.number}</strong>님께 알림이 도착했습니다!
              <br />
              {notification.message}
            </div>
            <Button onClick={closeNotification} variant="secondary">
              확인
            </Button>
          </div>
        )}

        {/* 발급 목록 */}
        {showIssuances && (
          <div className="issuance-list">
            <h4>발급된 번호표 목록</h4>
            <table>
              <thead>
                <tr>
                  <th>번호</th>
                  <th>사용자 ID</th>
                  <th>발급 시간</th>
                </tr>
              </thead>
              <tbody>
                {issuances.length === 0 ? (
                  <tr>
                    <td colSpan="3" style={{ textAlign: 'center', padding: '20px' }}>
                      발급된 번호표가 없습니다.
                    </td>
                  </tr>
                ) : (
                  issuances.map((issuance) => (
                    <tr key={`${issuance.number}-${issuance.userKey}`}>
                      <td style={{ textAlign: 'center' }}>{issuance.number}</td>
                      <td>{issuance.userKey}</td>
                      <td style={{ textAlign: 'center' }}>{issuance.issuedAt}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default RoomPage;