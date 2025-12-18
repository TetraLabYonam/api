import { useEffect, useState } from 'react';
import { useSocketStore } from '../stores/socketStore';
import { queueService } from '../services/queueService';
import { queueApiService } from '../services/queueApiService';
import { socketService } from '../services/socketService';
import apiClient from '../services/api';
import Button from '../components/common/Button';
import './AdminPage.css';

/**
 * 관리자 페이지
 * Flutter 가이드의 관리자 기능과 동일한 구조
 */
const AdminPage = () => {
  const { client, connected } = useSocketStore();
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadRooms();
  }, []);

  const loadRooms = async () => {
    try {
      setLoading(true);
      const data = await queueApiService.getActiveRooms();
      setRooms(data);
    } catch (error) {
      console.error('Failed to load rooms:', error);
      alert(`방 목록 로드 실패: ${error.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handleCreateRoom = async () => {
    const roomName = prompt('방 이름을 입력하세요:');
    if (roomName === null || roomName.trim() === '') return;

    try {
      const response = await apiClient.post('/api/v1/rooms', { roomName: roomName.trim() });
      alert(`방이 생성되었습니다!\n방 코드: ${response.data.roomUid || 'N/A'}`);
      loadRooms();
    } catch (error) {
      console.error('Failed to create room:', error);
      alert(`방 생성 실패: ${error.response?.data?.message || error.message}`);
    }
  };

  const handleBatchCreateRooms = async () => {
    const input = prompt(
      '방 이름들을 쉼표(,)로 구분하여 입력하세요:\n예시: 물금청소년문화의집, 동면 행정복지센터, 원동면 행정복지센터'
    );
    if (input === null || input.trim() === '') return;

    const roomNames = input
      .split(',')
      .map((name) => name.trim())
      .filter((name) => name.length > 0);

    if (roomNames.length === 0) {
      alert('방 이름을 입력해주세요.');
      return;
    }

    try {
      await apiClient.post('/api/v1/rooms/batch', { roomNames });
      alert(`${roomNames.length}개의 방이 생성되었습니다!`);
      loadRooms();
    } catch (error) {
      console.error('Failed to batch create rooms:', error);
      alert(`일괄 생성 실패: ${error.response?.data?.message || error.message}`);
    }
  };

  const handleViewRoom = (roomUid) => {
    window.open(`/rooms/${roomUid}`, '_blank');
  };

  const handleResetRoom = async (roomUid, roomName) => {
    if (
      !confirm(
        `방 "${roomName}" (${roomUid})을 초기화하시겠습니까?\n모든 발급 기록이 삭제됩니다.`
      )
    ) {
      return;
    }

    try {
      const response = await apiClient.post(`/api/v1/rooms/${roomUid}/reset`);
      alert(response.data?.message || '방이 초기화되었습니다.');
      loadRooms();
    } catch (error) {
      console.error('Failed to reset room:', error);
      alert(`방 초기화 실패: ${error.response?.data?.message || error.message}`);
    }
  };

  const handleCallNextNumber = async (roomUid, roomName) => {
    if (!confirm(`방 "${roomName}"에서 다음 번호를 호출하시겠습니까?`)) {
      return;
    }

    try {
      const response = await queueService.callNextNumber(roomUid);
      alert(`[${roomName}] 번호 ${response.currentNumber || 'N/A'}이(가) 호출되었습니다!`);
      loadRooms();
    } catch (error) {
      console.error('Failed to call next number:', error);
      alert(`번호 호출 실패: ${error.response?.data?.message || error.message}`);
    }
  };

  const handleSendNotification = (roomUid, roomName) => {
    if (!client || !connected) {
      alert('WebSocket이 연결되지 않았습니다. 페이지를 새로고침해주세요.');
      return;
    }

    const numberStr = prompt(
      `방 "${roomName}"에서\n알림을 보낼 번호표 번호를 입력하세요:`
    );
    if (numberStr === null) return;

    const number = parseInt(numberStr);
    if (isNaN(number) || number < 1) {
      alert('올바른 번호를 입력해주세요.');
      return;
    }

    const message = prompt('전송할 메시지를 입력하세요:');
    if (message === null || message.trim() === '') {
      alert('메시지를 입력해주세요.');
      return;
    }

    try {
      socketService.sendNotification(client, roomUid, number, message.trim());
      alert(`번호 ${number}에게 알림을 전송했습니다.`);
    } catch (error) {
      console.error('Failed to send notification:', error);
      alert(`알림 전송 실패: ${error.message}`);
    }
  };

  const handleDeleteRoom = async (roomUid, roomName, roomId) => {
    if (
      !confirm(
        `방 "${roomName}" (${roomUid})을 삭제하시겠습니까?\n이 작업은 되돌릴 수 없습니다.`
      )
    ) {
      return;
    }

    // 한 번 더 확인
    if (!confirm('정말로 삭제하시겠습니까?')) {
      return;
    }

    try {
      await apiClient.delete(`/api/v1/rooms/${roomId}`);
      alert('방이 삭제되었습니다.');
      loadRooms();
    } catch (error) {
      console.error('Failed to delete room:', error);
      alert(`방 삭제 실패: ${error.response?.data?.message || error.message}`);
    }
  };

  const totalRooms = rooms.length;
  const totalIssuedTickets = rooms.reduce(
    (sum, room) => sum + (room.issuedCount || 0),
    0
  );
  const activeRooms = rooms.filter((room) => room.isActive !== false).length;

  const formatDateTime = (dateTimeStr) => {
    if (!dateTimeStr) return '-';
    const date = new Date(dateTimeStr);
    return date.toLocaleString('ko-KR');
  };

  return (
    <div className="admin-page">
      <a href="/" className="back-link">
        ← 메인으로 돌아가기
      </a>

      <div className="admin-header">
        <h1>🔐 방 관리 시스템 (관리자)</h1>
        <p>모든 방의 상태를 확인하고 관리할 수 있습니다</p>
        {connected ? (
          <div className="ws-status connected">✓ WebSocket 연결됨</div>
        ) : (
          <div className="ws-status disconnected">⚠ WebSocket 연결 중...</div>
        )}
      </div>

      <div className="stats">
        <div className="stat-card">
          <div className="stat-number">{totalRooms}</div>
          <div className="stat-label">전체 방 수</div>
        </div>
        <div className="stat-card">
          <div className="stat-number">{activeRooms}</div>
          <div className="stat-label">활성화된 방</div>
        </div>
        <div className="stat-card">
          <div className="stat-number">{totalIssuedTickets}</div>
          <div className="stat-label">총 발급된 번호표</div>
        </div>
      </div>

      <div className="controls">
        <Button onClick={handleCreateRoom} variant="primary">
          새 방 만들기
        </Button>
        <Button onClick={handleBatchCreateRooms} variant="primary">
          일괄 생성
        </Button>
        <Button onClick={loadRooms} variant="secondary">
          새로고침
        </Button>
      </div>

      {loading && <p className="loading-message">로딩 중...</p>}

      <table className="room-table">
        <thead>
          <tr>
            <th>방 코드</th>
            <th>방 제목</th>
            <th>현재 번호</th>
            <th>발급된 인원</th>
            <th>생성 시간</th>
            <th>관리</th>
          </tr>
        </thead>
        <tbody>
          {rooms.length === 0 && !loading ? (
            <tr>
              <td colSpan="6" style={{ textAlign: 'center', padding: '40px' }}>
                생성된 방이 없습니다.
              </td>
            </tr>
          ) : (
            rooms.map((room) => (
              <tr key={room.roomUid}>
                <td>
                  <strong>{room.roomUid}</strong>
                </td>
                <td>{room.roomName || room.title || '제목 없음'}</td>
                <td style={{ textAlign: 'center' }}>{room.currentNumber}</td>
                <td style={{ textAlign: 'center' }}>
                  {room.issuedCount || 0}명
                </td>
                <td>{formatDateTime(room.createdAt)}</td>
                <td>
                  <div className="room-actions">
                    <Button
                      onClick={() => handleViewRoom(room.roomUid)}
                      variant="secondary"
                      title="방 입장"
                    >
                      입장
                    </Button>
                    <Button
                      onClick={() =>
                        handleCallNextNumber(
                          room.roomUid,
                          room.roomName || room.title
                        )
                      }
                      variant="primary"
                      title="다음 번호 호출"
                    >
                      호출
                    </Button>
                    <Button
                      onClick={() =>
                        handleSendNotification(
                          room.roomUid,
                          room.roomName || room.title
                        )
                      }
                      variant="primary"
                      title="특정 번호에 알림 전송"
                      disabled={!connected}
                    >
                      알림
                    </Button>
                    <Button
                      onClick={() =>
                        handleResetRoom(room.roomUid, room.roomName || room.title)
                      }
                      variant="danger"
                      title="방 초기화 (번호 리셋)"
                    >
                      초기화
                    </Button>
                    <Button
                      onClick={() =>
                        handleDeleteRoom(
                          room.roomUid,
                          room.roomName || room.title,
                          room.id
                        )
                      }
                      variant="danger"
                      title="방 삭제"
                    >
                      삭제
                    </Button>
                  </div>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      <div className="help-section">
        <h3>📖 도움말</h3>
        <ul>
          <li>
            <strong>입장</strong>: 사용자 화면으로 방에 입장합니다.
          </li>
          <li>
            <strong>호출</strong>: 다음 번호를 호출합니다. (현재 번호 +1)
          </li>
          <li>
            <strong>알림</strong>: 특정 번호표를 가진 사용자에게 알림을
            전송합니다.
          </li>
          <li>
            <strong>초기화</strong>: 방의 번호를 0으로 초기화하고 모든 발급
            기록을 삭제합니다.
          </li>
          <li>
            <strong>삭제</strong>: 방을 완전히 삭제합니다. (복구 불가)
          </li>
        </ul>
      </div>
    </div>
  );
};

export default AdminPage;
