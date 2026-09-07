/**
 * API Service Client cho MultiMart Platform
 * Tích hợp tự động gắn JWT Bearer Token và xử lý mã lỗi chuẩn hóa ApiResponse.
 */

const API_BASE_URL = '';

// Khởi tạo instance Axios
const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  timeout: 10000,
});

// Request Interceptor: Tự động đính kèm JWT Bearer Token nếu có trong localStorage
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('multimart_token');
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Chuẩn hóa bóc tách dữ liệu từ ApiResponse<T>
apiClient.interceptors.response.use(
  (response) => {
    return response.data; // Trả về { success: true, message: ..., data: ... }
  },
  (error) => {
    const errorData = error.response ? error.response.data : null;
    let message = 'Có lỗi xảy ra, vui lòng thử lại sau';
    let code = 9999;

    if (errorData) {
      message = errorData.message || message;
      code = errorData.code || (error.response ? error.response.status : 9999);
      
      // Nếu 401 Unauthorized và đang có token -> token hết hạn
      if (error.response && error.response.status === 401) {
        if (localStorage.getItem('multimart_token')) {
          localStorage.removeItem('multimart_token');
          localStorage.removeItem('multimart_user');
          window.dispatchEvent(new Event('auth-expired'));
        }
      }
    }
    return Promise.reject({ code, message, original: error });
  }
);

// Module API chi tiết
const MultiMartAPI = {
  // 1. Xác thực & Tài khoản
  auth: {
    login: (credentials) => apiClient.post('/api/v1/auth/login', credentials),
    register: (userData) => apiClient.post('/api/v1/auth/register', userData),
  },

  // 2. Sản phẩm thông thường
  products: {
    getProducts: (page = 0, size = 12) => apiClient.get(`/api/v1/products?page=${page}&size=${size}`),
    getProductById: (id) => apiClient.get(`/api/v1/products/${id}`),
  },

  // 3. Sự kiện Flash Sale công khai
  flashSale: {
    getActiveEvents: () => apiClient.get('/api/v1/flash-sales/active'),
    getEventDetails: (id) => apiClient.get(`/api/v1/flash-sales/${id}`),
  },

  // 4. Đặt mua và Quản lý Đơn hàng Flash Sale
  orders: {
    // Đặt hàng bất đồng bộ (Redis Lua + RabbitMQ)
    placeOrderAsync: (orderData) => apiClient.post('/api/v1/flash-sales/orders', orderData),
    // Polling trạng thái đơn hàng theo trackingId
    getTrackingStatus: (trackingId) => apiClient.get(`/api/v1/flash-sales/orders/tracking/${trackingId}`),
    // Lịch sử đơn hàng của tôi
    getMyOrders: (page = 0, size = 20) => apiClient.get(`/api/v1/flash-sales/orders/my-orders?page=${page}&size=${size}`),
    // Thanh toán đơn hàng trước khi hết hạn 15 phút
    payOrder: (orderId) => apiClient.post(`/api/v1/flash-sales/orders/${orderId}/pay`),
  },

  // 5. Tiện ích quản trị viên (Admin Tools)
  admin: {
    createEvent: (eventData) => apiClient.post('/api/v1/admin/flash-sales', eventData),
    addProductToEvent: (eventId, productData) => apiClient.post(`/api/v1/admin/flash-sales/${eventId}/products`, productData),
    warmUpEvent: (eventId) => apiClient.post(`/api/v1/admin/flash-sales/${eventId}/warm-up`),
  }
};

window.MultiMartAPI = MultiMartAPI;
