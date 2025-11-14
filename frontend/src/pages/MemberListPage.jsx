import { useState, useEffect } from 'react';
import { getMembers } from '../services/api';
import './MemberListPage.css';

const MemberListPage = () => {
    const [members, setMembers] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [searchTerm, setSearchTerm] = useState('');

    // 실제 API 호출로 회원 정보를 가져오는 함수
    const fetchMembers = async () => {
        setLoading(true);
        setError(null);

        try {
            const data = await getMembers();
            setMembers(data);
            setLoading(false);
        } catch (err) {
            console.error('회원 정보 조회 실패:', err);
            setError(`회원 정보를 불러오는 중 오류가 발생했습니다: ${err.message}`);
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchMembers();
    }, []);

    // 검색 필터링
    const filteredMembers = members.filter(member =>
        member.memberName.toLowerCase().includes(searchTerm.toLowerCase()) ||
        member.unitName.toLowerCase().includes(searchTerm.toLowerCase()) ||
        member.phoneNumber.includes(searchTerm)
    );

    return (
        <div className="member-list-page">
            <div className="page-header">
                <h1>회원 정보 조회</h1>
                <p>등록된 회원 정보를 조회하고 검색할 수 있습니다.</p>
            </div>

            <div className="search-section">
                <div className="search-box">
                    <input
                        type="text"
                        placeholder="이름, 부서, 전화번호로 검색..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        className="search-input"
                    />
                    <button onClick={fetchMembers} className="refresh-btn" disabled={loading}>
                        {loading ? '로딩 중...' : '새로고침'}
                    </button>
                </div>
            </div>

            {error && <div className="error-message">{error}</div>}

            <div className="members-section">
                <div className="section-header">
                    <h3>전체 회원 ({filteredMembers.length}명)</h3>
                </div>

                {loading ? (
                    <div className="loading-message">회원 정보를 불러오는 중...</div>
                ) : filteredMembers.length === 0 ? (
                    <div className="empty-message">
                        {searchTerm ? '검색 결과가 없습니다.' : '등록된 회원이 없습니다.'}
                    </div>
                ) : (
                    <div className="members-table-container">
                        <table className="members-table">
                            <thead>
                                <tr>
                                    <th>번호</th>
                                    <th>이름</th>
                                    <th>부서/팀</th>
                                    <th>전화번호</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredMembers.map((member, index) => (
                                    <tr key={member.id}>
                                        <td>{index + 1}</td>
                                        <td className="member-name">{member.memberName}</td>
                                        <td>{member.unitName}</td>
                                        <td>{member.phoneNumber}</td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>
        </div>
    );
};

export default MemberListPage;