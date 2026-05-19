import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import LoginPages from './pages/LoginPages';
import ForceChangePasswordPage from './pages/ForceChangePasswordPage';
import UserListPage from './components/UserListPage';
import CustomerListPage from './components/CustomerListPage';
import BookListPage from './components/BooklistPage';
import CartPage from './components/Cartpage';
import OrderListPage from './components/Orderlistpage';
import ChatWidget from './components/ChatWidget';
import RegisterPage from './components/RegisterPage';
import BookDetailPage from './components/Bookdetailpage';
import './App.css';

// Route công khai - không cần đăng nhập (xem sản phẩm)
function PublicRoute({ children }) {
  return children;
}

// Route bảo vệ - cần đăng nhập (giỏ hàng, thanh toán, đơn hàng)
function PrivateRoute({ children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />;
  return children;
}

// Route chỉ dành cho ADMIN
function AdminRoute({ children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />;
  if (user.role !== 'ADMIN') return <Navigate to="/cart" replace />;
  return children;
}

// Route đổi mật khẩu bắt buộc
function ForceChangePasswordRoute({ children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (!user.mustChangePassword) return <Navigate to="/cart" replace />;
  return children;
}

function AppContent() {
  const { user } = useAuth();
  
  return (
    <>
      <Routes>
        {/* Trang chủ - chuyển đến trang mua sách */}
        <Route path="/" element={<Navigate to="/cart" replace />} />
        
        {/* Công khai - ai cũng xem được */}
        <Route path="/login" element={<PublicRoute><LoginPages /></PublicRoute>} />
        <Route path="/register" element={<PublicRoute><RegisterPage /></PublicRoute>} />
        <Route path="/cart" element={<CartPage />} />
        <Route path="/book-detail" element={<BookDetailPage />} />
        
        {/* Bắt buộc đổi mật khẩu */}
        <Route path="/change-password" element={<ForceChangePasswordRoute><ForceChangePasswordPage /></ForceChangePasswordRoute>} />
        
        {/* Cần đăng nhập - quản lý tài khoản, đơn hàng */}
        <Route path="/orders" element={<PrivateRoute><OrderListPage /></PrivateRoute>} />
        <Route path="/customers" element={<PrivateRoute><CustomerListPage /></PrivateRoute>} />
        
        {/* Chỉ ADMIN mới vào được */}
        <Route path="/users" element={<AdminRoute><UserListPage /></AdminRoute>} />
        <Route path="/books" element={<AdminRoute><BookListPage /></AdminRoute>} />
        
        {/* Fallback */}
        <Route path="*" element={<Navigate to="/cart" replace />} />
      </Routes>
      {/* Chat widget hiện cho tất cả trừ ADMIN */}
      {(!user || user.role !== 'ADMIN') && <ChatWidget />}
    </>
  );
}

function App() {
  return (
    <AuthProvider>
      <BrowserRouter basename="/dem_login-0.0.1-SNAPSHOT">
        <AppContent />
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;