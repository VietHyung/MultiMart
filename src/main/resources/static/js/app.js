/**
 * MultiMart High-Concurrency E-Commerce Frontend Application
 * Tích hợp Vue 3 Reactive State, Polling RabbitMQ, Live Countdown và Auth Management.
 */

const { createApp, ref, computed, onMounted, onUnmounted, nextTick } = Vue;

const app = createApp({
  setup() {
    // ==========================================
    // 1. STATE TOÀN CỤC & TÀI KHOẢN
    // ==========================================
    const currentTab = ref('home');
    const currentUser = ref(null);
    const toasts = ref([]);
    let toastId = 0;

    const showToast = (message, type = 'info') => {
      const id = ++toastId;
      toasts.value.push({ id, message, type });
      setTimeout(() => {
        toasts.value = toasts.value.filter(t => t.id !== id);
      }, 3500);
      nextTick(() => lucide.createIcons());
    };

    // Kiểm tra đăng nhập khi mở trang
    const checkAuth = () => {
      const token = localStorage.getItem('multimart_token');
      const savedUser = localStorage.getItem('multimart_user');
      if (token && savedUser) {
        try {
          currentUser.value = JSON.parse(savedUser);
        } catch (e) {
          currentUser.value = null;
        }
      } else {
        currentUser.value = null;
      }
    };

    const isLoggedIn = computed(() => !!currentUser.value);

    // Lắng nghe sự kiện token hết hạn từ api.js
    window.addEventListener('auth-expired', () => {
      currentUser.value = null;
      showToast('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại', 'warning');
    });

    // ==========================================
    // 2. MODAL XÁC THỰC (LOGIN / REGISTER)
    // ==========================================
    const showAuthModal = ref(false);
    const authTab = ref('login');
    const authLoading = ref(false);
    const loginForm = ref({ email: '', password: '' });
    const registerForm = ref({ fullName: '', email: '', password: '', phone: '' });

    const openAuthModal = (tab = 'login') => {
      authTab.value = tab;
      showAuthModal.value = true;
      nextTick(() => lucide.createIcons());
    };

    const fillDemoAccount = (role) => {
      if (role === 'buyer') {
        loginForm.value.email = 'buyer_demo@multimart.com';
        loginForm.value.password = '123456';
      } else {
        loginForm.value.email = 'admin@multimart.com';
        loginForm.value.password = 'Admin@123456';
      }
    };

    const handleLogin = async () => {
      authLoading.value = true;
      try {
        const res = await MultiMartAPI.auth.login(loginForm.value);
        if (res.data && res.data.accessToken) {
          localStorage.setItem('multimart_token', res.data.accessToken);
          localStorage.setItem('multimart_user', JSON.stringify(res.data.user || {}));
          checkAuth();
          showAuthModal.value = false;
          showToast(`Chào mừng trở lại, ${res.data.user ? res.data.user.fullName : 'Bạn'}!`, 'success');
          fetchMyOrders();
        }
      } catch (err) {
        showToast(err.message || 'Đăng nhập không thành công', 'error');
      } finally {
        authLoading.value = false;
      }
    };

    const handleRegister = async () => {
      authLoading.value = true;
      try {
        await MultiMartAPI.auth.register(registerForm.value);
        showToast('Đăng ký thành công! Đang tự động đăng nhập...', 'success');
        
        // Tự động đăng nhập ngay sau khi đăng ký
        const loginRes = await MultiMartAPI.auth.login({
          email: registerForm.value.email,
          password: registerForm.value.password,
        });

        if (loginRes.data && loginRes.data.accessToken) {
          localStorage.setItem('multimart_token', loginRes.data.accessToken);
          localStorage.setItem('multimart_user', JSON.stringify(loginRes.data.user || {}));
          checkAuth();
          showAuthModal.value = false;
          showToast(`Xin chào ${loginRes.data.user.fullName}!`, 'success');
        }
      } catch (err) {
        showToast(err.message || 'Đăng ký không thành công', 'error');
      } finally {
        authLoading.value = false;
      }
    };

    const handleLogout = () => {
      localStorage.removeItem('multimart_token');
      localStorage.removeItem('multimart_user');
      currentUser.value = null;
      showToast('Đã đăng xuất tài khoản an toàn', 'info');
    };

    // ==========================================
    // 3. FLASH SALE LIVE & COUNTDOWN TIMER
    // ==========================================
    const activeEvent = ref(null);
    const activeEventCountdown = ref(0);
    let countdownInterval = null;

    const fetchActiveFlashSale = async () => {
      try {
        const res = await MultiMartAPI.flashSale.getActiveEvents();
        if (res.data && res.data.length > 0) {
          activeEvent.value = res.data[0];
          activeEventCountdown.value = activeEvent.value.remainingSeconds || 0;
          startCountdownTimer();
        } else {
          activeEvent.value = null;
        }
      } catch (err) {
        console.error('Không thể tải sự kiện Flash Sale:', err);
      } finally {
        nextTick(() => lucide.createIcons());
      }
    };

    const startCountdownTimer = () => {
      if (countdownInterval) clearInterval(countdownInterval);
      countdownInterval = setInterval(() => {
        if (activeEventCountdown.value > 0) {
          activeEventCountdown.value--;
        } else {
          clearInterval(countdownInterval);
          fetchActiveFlashSale(); // Re-fetch khi hết giờ
        }
      }, 1000);
    };

    const formatCountdown = (totalSeconds) => {
      if (!totalSeconds || totalSeconds <= 0) {
        return { hours: '00', minutes: '00', seconds: '00' };
      }
      const h = Math.floor(totalSeconds / 3600);
      const m = Math.floor((totalSeconds % 3600) / 60);
      const s = totalSeconds % 60;
      return {
        hours: String(h).padStart(2, '0'),
        minutes: String(m).padStart(2, '0'),
        seconds: String(s).padStart(2, '0'),
      };
    };

    const calculateStockPercentage = (item) => {
      if (!item.totalAllocated || item.totalAllocated <= 0) return 0;
      const sold = item.totalAllocated - (item.availableStock || 0);
      return Math.min(100, Math.max(0, Math.round((sold / item.totalAllocated) * 100)));
    };

    // ==========================================
    // 4. ĐẶT MUA CHỚP NHOÁNG & POLLING RABBITMQ
    // ==========================================
    const showOrderProgressModal = ref(false);
    const isOrdering = ref(false);
    const orderPipelineStep = ref(1);
    const activeTrackingId = ref('');
    const pollCount = ref(0);
    const createdOrder = ref(null);
    let pollInterval = null;

    const initiateFlashSaleOrder = async (item) => {
      if (!isLoggedIn.value) {
        showToast('Vui lòng đăng nhập để tham gia đặt mua Flash Sale', 'warning');
        openAuthModal('login');
        return;
      }

      isOrdering.value = true;
      showOrderProgressModal.value = true;
      orderPipelineStep.value = 1; // Step 1: Submitting to Redis Lua Script
      activeTrackingId.value = '';
      pollCount.value = 0;
      createdOrder.value = null;

      nextTick(() => lucide.createIcons());

      try {
        // Gửi yêu cầu đặt mua bất đồng bộ
        const payload = {
          eventId: activeEvent.value.id,
          productId: item.productId,
          quantity: 1,
        };

        const res = await MultiMartAPI.orders.placeOrderAsync(payload);
        if (res.data && res.data.orderTrackingId) {
          activeTrackingId.value = res.data.orderTrackingId;
          orderPipelineStep.value = 2; // Step 2: Enqueued in RabbitMQ
          nextTick(() => lucide.createIcons());
          
          // Bắt đầu chu kỳ Polling
          startOrderPolling(activeTrackingId.value, item);
        }
      } catch (err) {
        showToast(err.message || 'Không thể gửi yêu cầu đặt hàng', 'error');
        showOrderProgressModal.value = false;
        isOrdering.value = false;
      }
    };

    const startOrderPolling = (trackingId, originalItem) => {
      if (pollInterval) clearInterval(pollInterval);
      
      pollInterval = setInterval(async () => {
        pollCount.value++;
        try {
          const res = await MultiMartAPI.orders.getTrackingStatus(trackingId);
          const status = res.data ? res.data.status : '';

          if (status.startsWith('SUCCESS:')) {
            // Đã lưu Database thành công!
            clearInterval(pollInterval);
            const orderId = status.split(':')[1];
            orderPipelineStep.value = 3; // Step 3: Complete
            
            createdOrder.value = {
              id: orderId,
              productName: originalItem.productName,
              quantity: 1,
              totalPrice: originalItem.flashPrice,
              trackingId: trackingId,
            };

            showToast('🎉 Đặt hàng Flash Sale thành công! Giữ chỗ trong 15 phút', 'success');
            isOrdering.value = false;
            fetchActiveFlashSale(); // Cập nhật lại tồn kho hiển thị
            fetchMyOrders(); // Cập nhật danh sách đơn hàng
            nextTick(() => lucide.createIcons());

          } else if (status === 'FAILED') {
            clearInterval(pollInterval);
            showToast('Đơn hàng không thể hoàn tất do sự cố kho', 'error');
            showOrderProgressModal.value = false;
            isOrdering.value = false;
          } else if (pollCount.value > 20) {
            // Quá 20 lượt (12 giây) -> dừng poll để người dùng tự tra cứu
            clearInterval(pollInterval);
            orderPipelineStep.value = 3;
            isOrdering.value = false;
            showToast('Đơn hàng đang tiếp tục được xử lý ngầm trong hàng đợi', 'info');
          }
        } catch (e) {
          console.error('Lỗi khi polling:', e);
        }
      }, 600);
    };

    // ==========================================
    // 5. ĐƠN HÀNG CỦA TÔI & THANH TOÁN 15 PHÚT
    // ==========================================
    const showMyOrdersModal = ref(false);
    const myOrders = ref([]);
    const myOrdersLoading = ref(false);
    const isPaying = ref(false);

    const pendingOrderCount = computed(() => {
      return myOrders.value.filter(o => o.status === 'PENDING').length;
    });

    const openMyOrdersModal = () => {
      showMyOrdersModal.value = true;
      fetchMyOrders();
      nextTick(() => lucide.createIcons());
    };

    const fetchMyOrders = async () => {
      if (!isLoggedIn.value) return;
      myOrdersLoading.value = true;
      try {
        const res = await MultiMartAPI.orders.getMyOrders(0, 30);
        if (res.data && res.data.content) {
          myOrders.value = res.data.content;
        }
      } catch (err) {
        console.error('Không thể tải danh sách đơn hàng:', err);
      } finally {
        myOrdersLoading.value = false;
        nextTick(() => lucide.createIcons());
      }
    };

    const handlePayOrder = async (orderId) => {
      isPaying.value = true;
      try {
        const res = await MultiMartAPI.orders.payOrder(orderId);
        showToast('Thanh toán đơn hàng thành công! Trạng thái đã chuyển sang CONFIRMED', 'success');
        fetchMyOrders();
        if (createdOrder.value && createdOrder.value.id == orderId) {
          showOrderProgressModal.value = false;
        }
      } catch (err) {
        showToast(err.message || 'Thanh toán thất bại', 'error');
      } finally {
        isPaying.value = false;
      }
    };

    // ==========================================
    // 6. DANH MỤC SẢN PHẨM THÔNG THƯỜNG
    // ==========================================
    const products = ref([]);
    const productsLoading = ref(false);

    const fetchProducts = async () => {
      productsLoading.value = true;
      try {
        const res = await MultiMartAPI.products.getProducts(0, 12);
        if (res.data && res.data.content) {
          products.value = res.data.content;
        }
      } catch (err) {
        console.error('Không thể tải danh sách sản phẩm:', err);
      } finally {
        productsLoading.value = false;
        nextTick(() => lucide.createIcons());
      }
    };

    // ==========================================
    // 7. BẢNG TIỆN ÍCH ADMIN & WARM-UP REDIS
    // ==========================================
    const showAdminModal = ref(false);
    const adminActionLoading = ref(false);
    const warmUpEventId = ref(1);

    const loginQuickAdmin = async () => {
      try {
        const res = await MultiMartAPI.auth.login({
          email: 'admin@multimart.com',
          password: 'Admin@123456',
        });
        if (res.data && res.data.accessToken) {
          localStorage.setItem('multimart_token', res.data.accessToken);
          localStorage.setItem('multimart_user', JSON.stringify(res.data.user || {}));
          checkAuth();
          showToast('Đã đăng nhập thành công với quyền Quản Trị Viên (Admin)', 'success');
        }
      } catch (e) {
        showToast('Không thể đăng nhập admin: ' + (e.message || 'Chưa có tài khoản admin trong DB'), 'error');
      }
    };

    const triggerWarmUp = async () => {
      const eventId = warmUpEventId.value || (activeEvent.value ? activeEvent.value.id : 1);
      adminActionLoading.value = true;
      try {
        const res = await MultiMartAPI.admin.warmUpEvent(eventId);
        showToast(`Làm nóng Redis thành công! Đã nạp ${res.data ? res.data.totalProductsWarmedUp : 'tất cả'} sản phẩm vào RAM`, 'success');
        fetchActiveFlashSale();
      } catch (err) {
        showToast(err.message || 'Warm-up thất bại. Hãy chắc chắn bạn đã đăng nhập quyền ADMIN', 'error');
      } finally {
        adminActionLoading.value = false;
      }
    };

    // ==========================================
    // 8. FORMATTERS
    // ==========================================
    const formatCurrency = (amount) => {
      if (amount === null || amount === undefined) return '0 ₫';
      return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
    };

    const formatDate = (dateStr) => {
      if (!dateStr) return '';
      try {
        const d = new Date(dateStr);
        return d.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) + ' ' + d.toLocaleDateString('vi-VN');
      } catch (e) {
        return dateStr;
      }
    };

    // ==========================================
    // LIFECYCLE HOOKS
    // ==========================================
    onMounted(() => {
      checkAuth();
      fetchActiveFlashSale();
      fetchProducts();
      if (isLoggedIn.value) {
        fetchMyOrders();
      }
      nextTick(() => lucide.createIcons());
    });

    onUnmounted(() => {
      if (countdownInterval) clearInterval(countdownInterval);
      if (pollInterval) clearInterval(pollInterval);
    });

    return {
      currentTab,
      currentUser,
      isLoggedIn,
      toasts,
      showToast,

      // Auth
      showAuthModal,
      authTab,
      authLoading,
      loginForm,
      registerForm,
      openAuthModal,
      fillDemoAccount,
      handleLogin,
      handleRegister,
      handleLogout,

      // Flash Sale
      activeEvent,
      activeEventCountdown,
      formatCountdown,
      calculateStockPercentage,
      initiateFlashSaleOrder,

      // Ordering & Polling
      showOrderProgressModal,
      isOrdering,
      orderPipelineStep,
      activeTrackingId,
      pollCount,
      createdOrder,

      // Orders & Pay
      showMyOrdersModal,
      myOrders,
      myOrdersLoading,
      pendingOrderCount,
      openMyOrdersModal,
      fetchMyOrders,
      handlePayOrder,
      isPaying,

      // Products
      products,
      productsLoading,

      // Admin Tools
      showAdminModal,
      adminActionLoading,
      warmUpEventId,
      loginQuickAdmin,
      triggerWarmUp,

      // Helpers
      formatCurrency,
      formatDate,
    };
  }
});

app.mount('#app');
