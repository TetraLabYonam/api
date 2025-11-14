import './HomePage.css';

const HomePage = () => {
  return (
    <div className="dashboard">
      <div className="welcome-section">
        <h2>환영합니다! 👋</h2>
        <p>연암공과대학교 관리 시스템에 오신 것을 환영합니다.</p>
      </div>

      <div className="dashboard-grid">
        <div className="dashboard-card">
          <div className="card-icon">📊</div>
          <h3>오늘의 통계</h3>
          <p>등록된 회원 수</p>
          <span className="stat-number">124</span>
        </div>

        <div className="dashboard-card">
          <div className="card-icon">🎫</div>
          <h3>활성 방</h3>
          <p>현재 운영 중인 방</p>
          <span className="stat-number">8</span>
        </div>

        <div className="dashboard-card">
          <div className="card-icon">📅</div>
          <h3>예정된 일정</h3>
          <p>이번 주 일정</p>
          <span className="stat-number">5</span>
        </div>

        <div className="dashboard-card">
          <div className="card-icon">⚡</div>
          <h3>시스템 상태</h3>
          <p>모든 서비스 정상</p>
          <span className="status-indicator">🟢 정상</span>
        </div>
      </div>

      <div className="recent-activity">
        <h3>최근 활동</h3>
        <div className="activity-list">
          <div className="activity-item">
            <span className="activity-time">10분 전</span>
            <span className="activity-text">새로운 회원이 등록되었습니다.</span>
          </div>
          <div className="activity-item">
            <span className="activity-time">30분 전</span>
            <span className="activity-text">방 #3에서 번호표가 발급되었습니다.</span>
          </div>
          <div className="activity-item">
            <span className="activity-time">1시간 전</span>
            <span className="activity-text">Excel 파일이 업로드되었습니다.</span>
          </div>
        </div>
      </div>
    </div>
  );
};

export default HomePage;