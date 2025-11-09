import { useState, useCallback, useRef } from 'react';
import { uploadMemberExcel, saveMembersToDb } from '../services/api';
import './MemberXlsPage.css';

const MemberXlsPage = () => {
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [message, setMessage] = useState(null);
  const [messageType, setMessageType] = useState(null);
  const fileInputRef = useRef(null);

  // 파일 업로드 처리
  const handleFileChange = useCallback(async (event) => {
    const file = event.target.files?.[0];
    if (!file) return;

    // 파일 형식 검증
    const validExtensions = ['.xlsx', '.xls'];
    const fileExtension = file.name.toLowerCase().slice(file.name.lastIndexOf('.'));
    if (!validExtensions.includes(fileExtension)) {
      setError('엑셀 파일(.xlsx, .xls)만 업로드 가능합니다.');
      setMessage('엑셀 파일(.xlsx, .xls)만 업로드 가능합니다.');
      setMessageType('error');
      return;
    }

    setLoading(true);
    setError(null);
    setMessage(null);
    setMessageType(null);

    try {
      const data = await uploadMemberExcel(file);
      if (data && data.members && Array.isArray(data.members)) {
        setMembers(data.members);
        setMessage(`${data.members.length}개의 회원 정보를 불러왔습니다.`);
        setMessageType('success');
      } else {
        throw new Error('업로드된 데이터 형식이 올바르지 않습니다.');
      }
    } catch (err) {
      console.error('파일 업로드 실패:', err);
      const errorMessage = err.response?.data?.error || err.message || '파일 업로드 중 오류가 발생했습니다.';
      setError(errorMessage);
      setMessage(errorMessage);
      setMessageType('error');
      setMembers([]);
    } finally {
      setLoading(false);
      // 파일 input 초기화
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
    }
  }, []);

  // DB 저장 처리
  const handleSaveToDb = useCallback(async () => {
    if (!members || members.length === 0) {
      setMessage('저장할 회원 정보가 없습니다.');
      setMessageType('error');
      return;
    }

    setSaving(true);
    setMessage(null);
    setMessageType(null);

    try {
      // 백엔드가 기대하는 형식으로 변환
      const membersToSave = members.map(member => ({
        memberName: member.username || member.memberName,
        phoneNumber: member.phoneNumber,
        unitName: member.businessUnit || member.unitName
      }));

      const response = await saveMembersToDb(membersToSave);
      const message = response?.message || '회원 정보가 성공적으로 저장되었습니다.';
      setMessage(message);
      setMessageType('success');
    } catch (err) {
      console.error('DB 저장 실패:', err);
      const errorMessage = err.response?.data?.error || err.response?.data || err.message || '저장 중 오류가 발생했습니다.';
      setMessage(errorMessage);
      setMessageType('error');
    } finally {
      setSaving(false);
    }
  }, [members]);

  return (
    <div className="member-xls-page">
      <div className="member-xls-wrap">
        <div className="member-xls-title">회원 정보 Excel 업로드</div>

        {/* 파일 업로드 섹션 */}
        <div className="upload-section">
          <form onSubmit={(e) => e.preventDefault()}>
            <div className="form-group">
              <label className="form-label" htmlFor="file">
                엑셀 파일을 선택하세요 (회원 정보가 포함된 파일)
              </label>
              <input
                type="file"
                id="file"
                name="file"
                accept=".xlsx,.xls"
                className="file-input"
                onChange={handleFileChange}
                disabled={loading}
                ref={fileInputRef}
              />
            </div>
            <div className="submit-note">
              파일을 선택하면 자동으로 업로드됩니다.
            </div>
          </form>
        </div>

        {/* 메시지 표시 */}
        {message && (
          <div className={`message ${messageType}`}>
            {message}
          </div>
        )}

        {/* 회원 정보 목록 */}
        {members.length > 0 && (
          <div className="members-info">
            <h3>업로드된 회원 정보 ({members.length}개)</h3>
            <div className="members-table-container">
              <table className="members-table">
                <thead>
                  <tr>
                    <th>번호</th>
                    <th>사업단</th>
                    <th>사용자명</th>
                    <th>전화번호</th>
                  </tr>
                </thead>
                <tbody>
                  {members.map((member, index) => (
                    <tr key={index}>
                      <td>{index + 1}</td>
                      <td>{member.unitName || member.businessUnit || '-'}</td>
                      <td>{member.memberName || member.username || '-'}</td>
                      <td>{member.phoneNumber || '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* DB 저장 버튼 */}
        {members.length > 0 && (
          <div className="save-section">
            <button
              className="save-btn"
              onClick={handleSaveToDb}
              disabled={saving || members.length === 0}
            >
              {saving ? '저장 중...' : '데이터베이스에 저장'}
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default MemberXlsPage;
