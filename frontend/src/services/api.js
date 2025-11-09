import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
});

// Request interceptor
apiClient.interceptors.request.use(
  (config) => {
    console.log(`[API] ${config.method.toUpperCase()} ${config.url}`);
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor
apiClient.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    console.error('[API Error]', error.response?.data || error.message);
    return Promise.reject(error);
  }
);

// 위치 데이터 가져오기
export const getLocationData = async () => {
  try {
    // 백엔드에 REST API 엔드포인트가 있다면 사용
    // 예: const response = await apiClient.get('/api/map/location');
    // return response.data;
    
    // 현재는 URL 파라미터를 사용하므로 null 반환
    // 백엔드에 REST API 엔드포인트를 추가하면 위 주석을 해제하고 사용
    return null;
  } catch (error) {
    console.error('위치 데이터 가져오기 실패:', error);
    throw error;
  }
};

// Excel 파일 업로드
export const uploadExcelFile = async (file) => {
  try {
    const formData = new FormData();
    formData.append('file', file);

    // multipart/form-data 요청
    const response = await axios.post(
      `${API_BASE_URL}/api/map-excel`,
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
        withCredentials: true,
      }
    );

    // 백엔드는 { locations: [...] } 형식으로 반환
    if (response.data && response.data.locations) {
      return response.data;
    }
    
    throw new Error('응답 형식이 올바르지 않습니다.');
  } catch (error) {
    console.error('Excel 파일 업로드 실패:', error);
    // 에러 응답 처리
    if (error.response?.data?.error) {
      throw new Error(error.response.data.error);
    }
    throw error;
  }
};

// 위치 데이터를 DB에 저장
export const saveLocationsToDb = async (locations) => {
  try {
    const response = await apiClient.post('/api/place/save-all', locations);
    return response.data;
  } catch (error) {
    console.error('DB 저장 실패:', error);
    throw error;
  }
};

// Member Excel 파일 업로드
export const uploadMemberExcel = async (file) => {
  try {
    const formData = new FormData();
    formData.append('file', file);

    // multipart/form-data 요청
    const response = await axios.post(
      `${API_BASE_URL}/api/v1/member/member-excel`,
      formData,
      {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
        withCredentials: true,
      }
    );

    // 백엔드는 { members: [...] } 형식으로 반환
    if (response.data && response.data.members) {
      return response.data;
    }

    throw new Error('응답 형식이 올바르지 않습니다.');
  } catch (error) {
    console.error('Member Excel 파일 업로드 실패:', error);
    // 에러 응답 처리
    if (error.response?.data?.error) {
      throw new Error(error.response.data.error);
    }
    throw error;
  }
};

// Member 데이터를 DB에 저장
export const saveMembersToDb = async (members) => {
  try {
    const response = await apiClient.post('/api/v1/member/save-members', { members });
    return response.data;
  } catch (error) {
    console.error('회원 정보 저장 실패:', error);
    throw error;
  }
};

export default apiClient;