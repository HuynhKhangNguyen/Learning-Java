import { Navigate, Route, Routes } from 'react-router';
import { AppShell } from './components/AppShell';
import { HomePage } from './pages/HomePage';
import { HttpLessonPage } from './pages/HttpLessonPage';
import { RequestLifecyclePage } from './pages/RequestLifecyclePage';
import { JourneyPage } from './pages/JourneyPage';
import { LearningPage } from './pages/LearningPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { paths } from './routes';

export function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route index element={<HomePage />} />
        <Route path="journey" element={<Navigate to={paths.journey} replace />} />
        <Route path="journey/:roadmapId" element={<JourneyPage />} />
        <Route path="learn" element={<Navigate to={paths.lab} replace />} />
        <Route path="learn/http-foundations" element={<HttpLessonPage />} />
        <Route path="learn/request-lifecycle" element={<RequestLifecyclePage />} />
        <Route path="learn/:lessonId" element={<LearningPage />} />
        <Route path="learn/:lessonId/:stepId" element={<LearningPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
