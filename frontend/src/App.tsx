import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from '@/auth/ProtectedRoute'
import { AppShell } from '@/components/layout/AppShell'
import { AuthPage } from '@/pages/AuthPage'
import { DashboardPage } from '@/pages/DashboardPage'
import { TransactionsPage } from '@/pages/TransactionsPage'
import { UploadPage } from '@/pages/UploadPage'
import { SubscriptionsPage } from '@/pages/SubscriptionsPage'
import { AskAiPage } from '@/pages/AskAiPage'
import { InsightsPage } from '@/pages/InsightsPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route index element={<DashboardPage />} />
          <Route path="transactions" element={<TransactionsPage />} />
          <Route path="upload" element={<UploadPage />} />
          <Route path="subscriptions" element={<SubscriptionsPage />} />
          <Route path="ask" element={<AskAiPage />} />
          <Route path="insights" element={<InsightsPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
