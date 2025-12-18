import { useEffect, useState, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useSocketStore } from '../stores/socketStore';
import { queueService } from '../services/queueService';
import { queueApiService } from '../services/queueApiService';
import Button from '../components/common/Button';
import './RoomPage.css';

/**
 * 번호표 시스템 - 방 페이지
 * Flutter 가이드의 RoomDetailScreen과 동일한 구조
 */
const RoomPage = () => {
  const { roomUid } = useParams();
  const navigate = useNavigate();
  const { client, connected } = useSocketStore();

  const [rooms, setRooms] = useState([]);
  const [currentRoomStatus, setCurrentRoomStatus] = useState(null);
  const [myTicket, setMyTicket] = useState(null);
  const [notification, setNotification] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const serviceRef = useRef(queueService);

  // 방 목록 로드
  useEffect(() => {
    if (!roomUid) {
      loadRooms();
    }
  }, [roomUid]);

  // 방 입장 및 WebSocket 연결
  useEffect(() => {
    if (!roomUid || !client || !connected) return;

    const service = serviceRef.current;
    service.setClient(client);

    // 콜백 설정
    service.onRoomStatusUpdate = (status) => {
      console.log('[Room] Status update:', status);
      setCurrentRoomStatus(status);
    };

    service.onTicketIssued = (ticket) => {
      console.log('[Room] Ticket issued:', ticket);
      setMyTicket(ticket);
    };

    service.onNotification = (data) => {
      console.log('[Room] Notification:', data);
      setNotification(data);
    };

    service.onError = (error) => {
      console.error('[Room] Error:', error);
      setError(error);
    };

    try {
      // 방 입장
      service.joinRoom(roomUid);

      // REST API로 현재 상태 가져오기
      service.refreshRoomStatus(roomUid);
    } catch (error) {
      console.error('[Room] Failed to join:', error);
      setError('방에 입장할 수 없습니다.');
    }

    // Cleanup
    return () => {
      service.leaveRoom();
    };
  }, [roomUid, client, connected]);

  const loadRooms = async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await queueApiService.getActiveRooms();
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

  const handleIssueTicket = async () => {
    if (!roomUid) {
      alert('방을 선택해주세요.');
      return;
    }

    if (!connected) {
      alert('WebSocket이 연결되지 않았습니다. 잠시 후 다시 시도해주세요.');
      return;
    }

    try {
      // REST API 방식으로 번호표 발급
      await serviceRef.current.issueTicketViaRest(roomUid);
    } catch (error) {
      console.error('[Room] Failed to issue ticket:', error);
      alert(`번호표 발급 실패: ${error.message}`);
    }
  };

  const handleRefreshStatus = async () => {
    if (!roomUid) return;

    try {
      await serviceRef.current.refreshRoomStatus(roomUid);
    } catch (error) {
      console.error('[Room] Failed to refresh status:', error);
      alert(`상태 새로고침 실패: ${error.message}`);
    }
  };

  const backToRoomList = () => {
    setCurrentRoomStatus(null);
    setMyTicket(null);
    setNotification(null);
    navigate('/rooms');
  };

  const closeNotification = () => {
    setNotification(null);
  };

  const closeError = () => {
    setError(null);
  };

  // 방 목록 화면
  if (!roomUid) {
    return (
      <div className="room-page">
        <div className="room-list-section">
          <h3>📱 번호표 방 목록</h3>
          <p className="subtitle">입장할 방을 선택하세요</p>

          <div className="action-buttons">
            <Button onClick={loadRooms} variant="secondary">
              새로고침
            </Button>
          </div>

          {loading && <p className="loading-message">로딩 중...</p>}
          {error && (
            <div className="error-message">
              <p>{error}</p>
              <Button onClick={closeError} variant="secondary">
                닫기
              </Button>
            </div>
          )}

          <div className="room-list">
            {rooms.length === 0 && !loading && !error && (
              <p className="empty-message">생성된 방이 없습니다.</p>
            )}
            {rooms.map((room) => (
              <div
                key={room.roomUid}
                className="room-card"
                onClick={() => selectRoom(room)}
              >
                <div className="room-header">
                  <div className="room-title">{room.roomName || '제목 없음'}</div>
                  {room.isActive ? (
                    <span className="status-badge active">활성</span>
                  ) : (
                    <span className="status-badge inactive">비활성</span>
                  )}
                </div>
                <div className="room-info">
                  <div className="info-item">
                    <span className="label">방 코드</span>
                    <span className="value">{room.roomUid}</span>
                  </div>
                  <div className="info-item">
                    <span className="label">현재 번호</span>
                    <span className="value current-number">{room.currentNumber}</span>
                  </div>
                  <div className="info-item">
                    <span className="label">대기 중</span>
                    <span className="value waiting-count">{room.waitingCount}명</span>
                  </div>
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
          <h3>🎫 번호표 시스템</h3>
          <div className="room-code">
            방 코드: <strong>{roomUid}</strong>
          </div>
          {connected ? (
            <div className="connection-status connected">
              ✓ 실시간 연결됨
            </div>
          ) : (
            <div className="connection-status disconnected">
              ⚠ 연결 중...
            </div>
          )}
        </div>

        {/* 에러 메시지 */}
        {error && (
          <div className="error-message">
            <p>{error}</p>
            <Button onClick={closeError} variant="secondary">
              닫기
            </Button>
          </div>
        )}

        {/* 방 현황 */}
        {currentRoomStatus && (
          <div className="room-status-card">
            <h4>{currentRoomStatus.roomName || '방 현황'}</h4>
            <div className="status-grid">
              <div className="status-item">
                <div className="status-label">현재 번호</div>
                <div className="status-value current">
                  {currentRoomStatus.currentNumber}
                </div>
              </div>
              <div className="status-item">
                <div className="status-label">마지막 발급</div>
                <div className="status-value">
                  {currentRoomStatus.lastIssuedNumber}
                </div>
              </div>
              <div className="status-item">
                <div className="status-label">대기 인원</div>
                <div className="status-value waiting">
                  {currentRoomStatus.waitingCount}명
                </div>
              </div>
            </div>
          </div>
        )}

        {/* 내 번호표 표시 */}
        {myTicket ? (
          <div className="my-ticket-area">
            <h3>🎫 내 번호</h3>
            <div className="ticket-number">{myTicket.number}</div>
            <div className="ticket-status">
              {myTicket.duplicated ? (
                <span className="duplicated-badge">이미 발급받은 번호</span>
              ) : (
                <span className="new-badge">새로 발급됨</span>
              )}
            </div>
            {currentRoomStatus && (
              <div className="waiting-info">
                앞에{' '}
                <strong>
                  {Math.max(0, myTicket.number - currentRoomStatus.currentNumber)}
                </strong>
                명 대기 중
              </div>
            )}
          </div>
        ) : (
          <div className="action-buttons">
            <Button
              onClick={handleIssueTicket}
              variant="primary"
              disabled={!connected}
            >
              번호표 발급받기
            </Button>
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

        {/* 새로고침 버튼 */}
        <div className="action-buttons">
          <Button onClick={handleRefreshStatus} variant="secondary">
            상태 새로고침
          </Button>
        </div>

        {/* 디바이스 ID 표시 (디버깅용) */}
        <div className="debug-info">
          <small>디바이스 ID: {serviceRef.current.deviceId}</small>
        </div>
      </div>
    </div>
  );
};

export default RoomPage;
