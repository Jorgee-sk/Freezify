import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { BrowserRouter, Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { ApiError } from './api/client'
import { AuthProvider } from './auth/AuthContext'
import { useAuth } from './auth/useAuth'
import { Layout } from './components/Layout'
import { HouseholdDetailPage } from './pages/HouseholdDetailPage'
import { HouseholdsPage } from './pages/HouseholdsPage'
import { InventoryPage } from './pages/InventoryPage'
import { LoginPage } from './pages/LoginPage'
import { NotificationPreferencesPage } from './pages/NotificationPreferencesPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { RecipeDetailPage } from './pages/RecipeDetailPage'
import { RecipesPage } from './pages/RecipesPage'
import { RegisterPage } from './pages/RegisterPage'

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // Retrying a 4xx only repeats the same answer.
        retry: (failures, error) => !(error instanceof ApiError && error.status < 500) && failures < 2,
      },
    },
  })
}

function RequireAuth() {
  const { status } = useAuth()
  const { t } = useTranslation()
  if (status === 'loading') return <p className="centered muted">{t('app.loading')}</p>
  return status === 'authenticated' ? <Outlet /> : <Navigate to="/login" replace />
}

function AnonymousOnly() {
  const { status } = useAuth()
  const { t } = useTranslation()
  if (status === 'loading') return <p className="centered muted">{t('app.loading')}</p>
  return status === 'anonymous' ? <Outlet /> : <Navigate to="/" replace />
}

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route element={<AnonymousOnly />}>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
        </Route>
        <Route element={<RequireAuth />}>
          <Route path="/" element={<HouseholdsPage />} />
          <Route path="/households/:householdId" element={<InventoryPage />} />
          <Route path="/households/:householdId/settings" element={<HouseholdDetailPage />} />
          <Route path="/households/:householdId/recipes" element={<RecipesPage />} />
          <Route path="/households/:householdId/recipes/:recipeId" element={<RecipeDetailPage />} />
          <Route path="/notifications" element={<NotificationsPage />} />
          <Route path="/notifications/preferences" element={<NotificationPreferencesPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}

export default function App() {
  const [queryClient] = useState(createQueryClient)
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  )
}
