import { useEffect, useState } from 'react';
import { useSocketContext } from '../contexts/SocketContext';
import { roomService } from '../services/roomService';
import { socketService } from '../services/socketService';
import Button from '../components/common/Button';
import './AdminPage.css';

const AdminPage = () => {
  const { client, connected } = useSocketContext();
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadRooms();
  }, []);

  const loadRooms = async () => {
    try {
      setLoading(true);
      const data = await roomService.getRoomDetails();
      setRooms(data);
    } catch (error) {
      console.error('Failed to load rooms:', error);
      alert('방 목록을 불러올 수 없습니다.');
    } finally {
      setLoading(false);
    }
  };

  const handleCreateRoom = async () => {
    const title = prompt('방 제목을 입력하세요:');
    if (title === null) return;

    try {
      const result = await roomService.createRoom(title);
      alert(`방이 생성되었습니다!\n방 코드: ${result.roomUid}`);
      loadRooms();
    } catch (error) {
      console.error('Failed to create room:', error);
      alert('방 생성에 실패했습니다.');
    }
  };

  const handleViewRoom = (roomUid) => {
    window.open(`/rooms/${roomUid}`, '_blank');
  };

  const handleResetRoom = async (roomUid) => {
    if (!confirm(`방 ${roomUid}을(를) 초기화하시겠습니까?\n모든 발급 기록이 삭제됩니다.`)) {
      return;
    }

    try {
      const result = await roomService.resetRoom(roomUid);
      alert(result.message || '방이 초기화되었습니다.');
      loadRooms();
    } catch (error) {
      console.error('Failed to reset room:', error);
      alert('방 초기화에 실패했습니다.');
    }
  };

  const handleSendNotification = (roomUid) => {
    if (!client || !connected) {
      alert('WebSocket이 연결되지 않았습니다. 페이지를 새로고침해주세요.');
      return;
    }

    const numberStr = prompt('알림을 보낼 번호표 번호를 입력하세요:');
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
      alert(`알림 전송 실패: ${error.error || error.message}`);
    }
  };

  const totalRooms = rooms.length;
  const totalIssuedTickets = rooms.reduce((sum, room) => sum + room.issuedCount, 0);

  const formatDateTime = (dateTimeStr) => {
    const date = new Date(dateTimeStr);
    return date.toLocaleString('ko-KR');
  };

  return (
    <div className="admin-page">
      <a href="/" className="back-link">← 메인으로 돌아가기</a>

      <div className="admin-header">
        <h1>🔐 방 관리 시스템 (관리자)</h1>
        <p>모든 방의 상태를 확인하고 관리할 수 있습니다</p>
      </div>

      <div className="stats">
        <div className="stat-card">
          <div className="stat-number">{totalRooms}</div>
          <div className="stat-label">전체 방 수</div>
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
        <Button onClick={loadRooms} variant="secondary">
          새로고침
        </Button>
      </div>

      {loading && <p>로딩 중...</p>}

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
                <td><strong>{room.roomUid}</strong></td>
                <td>{room.title || '제목 없음'}</td>
                <td style={{ textAlign: 'center' }}>{room.currentNumber}</td>
                <td style={{ textAlign: 'center' }}>{room.issuedCount}명</td>
                <td>{formatDateTime(room.createdAt)}</td>
                <td>
                  <div className="room-actions">
                    <Button onClick={() => handleViewRoom(room.roomUid)} variant="secondary">
                      입장
                    </Button>
                    <Button onClick={() => handleSendNotification(room.roomUid)} variant="primary">
                      알림
                    </Button>
                    <Button onClick={() => handleResetRoom(room.roomUid)} variant="danger">
                      초기화
                    </Button>
                  </div>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
};

export default AdminPage;