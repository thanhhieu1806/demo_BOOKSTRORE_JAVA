import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import LoginPages from './pages/LoginPages';
import ForceChangePasswordPage from './pages/ForceChangePasswordPage';
import RegisterPage from './components/RegisterPage';
import UserListPage from './components/UserListPage';
import CustomerListPage from './components/CustomerListPage';
import BookListPage from './components/BooklistPage';
import CartPage from './components/Cartpage';
import OrderListPage from './components/Orderlistpage';
import BookDetailPage from './components/Bookdetailpage';
import ChatWidget from './components/ChatWidget';
import './App.css';

//  PrivateRoute: đã login + không cần đổi mật khẩu 
function PrivateRoute({ children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />;
  return children;
}

//  PublicRoute: chưa login mới vào được 
function PublicRoute({ children }) {
  const { user } = useAuth();
  if (!user) return children;
  if (user.mustChangePassword) return <Navigate to="/change-password" replace />;
  return <Navigate to={user.role === 'ADMIN' ? '/users' : '/cart'} replace />;
}

//  ForceChangePasswordRoute 
function ForceChangePasswordRoute({ children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (!user.mustChangePassword)
    return <Navigate to={user.role === 'ADMIN' ? '/users' : '/cart'} replace />;
  return children;
}

//  AdminRoute: chỉ ADMIN mới vào được 
function AdminRoute({ children }) {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  if (user.role !== 'ADMIN') return <Navigate to="/cart" replace />;
  return children;
}

//  ChatWidgetWrapper: ẩn với ADMIN 
function ChatWidgetWrapper() {
  const { user } = useAuth();
  // Yêu cầu 6: Ẩn AI với ADMIN, chỉ hiện với USER và REPORT
  if (!user || user.role === 'ADMIN') return null;
  return <ChatWidget />;
}

function App() {
  return (
    <AuthProvider>
      <BrowserRouter basename="/dem_login-0.0.1-SNAPSHOT">
        <Routes>
          {/* Redirect trang chủ */}
          <Route path="/" element={<Navigate to="/login" replace />} />

          {/* Public routes */}
          <Route path="/login"
            element={<PublicRoute><LoginPages /></PublicRoute>} />
          <Route path="/register"
            element={<PublicRoute><RegisterPage /></PublicRoute>} />

          {/* Force change password */}
          <Route path="/change-password"
            element={
              <ForceChangePasswordRoute>
                <ForceChangePasswordPage />
              </ForceChangePasswordRoute>
            } />

          {/* ADMIN routes */}
          <Route path="/users"
            element={
              <AdminRoute><UserListPage /></AdminRoute>
            } />

          {/* Shared routes (USER + ADMIN + REPORT) */}
          <Route path="/customers"
            element={<PrivateRoute><CustomerListPage /></PrivateRoute>} />

          {/* Book management - ADMIN only */}
          <Route path="/books"
            element={<AdminRoute><BookListPage /></AdminRoute>} />

          {/* Yêu cầu 7: Xem sách không cần login,
                        nhưng CartPage xử lý redirect khi thanh toán */}
          <Route path="/cart" element={<CartPage />} />
          <Route path="/book-detail" element={<BookDetailPage />} />

          {/* Orders - cần login */}
          <Route path="/orders"
            element={<PrivateRoute><OrderListPage /></PrivateRoute>} />

          {/* Fallback */}
          <Route path="*" element={<Navigate to="/login" replace />} />
        </Routes>

        {/* Yêu cầu 6: ChatWidget ẩn với ADMIN */}
        <ChatWidgetWrapper />
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;