/**
 * 사용자 키 관리 서비스
 * localStorage를 사용하여 사용자 키를 영구 저장
 */

const USER_KEY_STORAGE_KEY = 'userKey';

/**
 * 랜덤 사용자 키 생성
 * @returns {string} USER-{랜덤문자열}
 */
export const generateUserKey = () => {
  const randomString = Math.random().toString(36).substring(2, 10);
  return `USER-${randomString}`;
};

/**
 * 저장된 사용자 키 가져오기 (없으면 생성)
 * @returns {string} 사용자 키
 */
export const getUserKey = () => {
  let userKey = localStorage.getItem(USER_KEY_STORAGE_KEY);

  if (!userKey) {
    userKey = generateUserKey();
    localStorage.setItem(USER_KEY_STORAGE_KEY, userKey);
  }

  return userKey;
};

/**
 * 사용자 키 설정
 * @param {string} key - 사용자 키
 */
export const setUserKey = (key) => {
  localStorage.setItem(USER_KEY_STORAGE_KEY, key);
};

/**
 * 사용자 키 삭제
 */
export const clearUserKey = () => {
  localStorage.removeItem(USER_KEY_STORAGE_KEY);
};

