import { useState } from 'react';
import { ArrowRight, ChevronDown, Compass, Coffee, Database, FlaskConical, Globe, Layers3, Rocket, Shield, Container, GitBranch, Server } from 'lucide-react';
import { Link, NavLink, useParams, useSearchParams } from 'react-router';
import { PageHeading } from '../components/PageHeading';
import { getLesson } from '../content/catalog';
import { paths } from '../routes';
import { NotFoundPage } from './NotFoundPage';
import { javaStages, devopsStages, type MapStage } from '../data/journey';
import { useLocale } from '../i18n/locale-store';
import { pair, type Pair } from '../i18n/types';
import '../styles/journey.css';

const symbols = [Compass, Coffee, Layers3, FlaskConical, Globe, Rocket, Database, Database, Shield, Server, Container, GitBranch];

const copy = {
  mapSummary: (stages: number, topics: number): Pair => pair(`${stages} stages · ${topics} topics`, `${stages} chặng · ${topics} chủ đề`),
  mapHint: pair('Open a stage, then choose a topic to see its goal.', 'Mở một chặng rồi chọn chủ đề để xem mục tiêu học tập.'),
  collapseAll: pair('Collapse all', 'Thu gọn tất cả'),
  expandAll: pair('Expand all', 'Mở tất cả'),
  suggestedOrder: pair('Suggested learning order', 'Thứ tự học gợi ý'),
  knowledgeBranch: pair('Knowledge branch', 'Nhánh kiến thức'),
  draftLegend: pair('Outline · Lessons are being authored', 'Đề cương · Bài học đang biên soạn'),
  start: pair('START EXPLORING', 'BẮT ĐẦU KHÁM PHÁ'),
  topicCount: (count: number): Pair => pair(`${count} topics`, `${count} chủ đề`),
  topicDetail: (title: string): Pair => pair(`${title} details`, `Chi tiết ${title}`),
  outcome: pair('What will you be able to do?', 'Bạn sẽ làm được gì?'),
  entryPoint: pair('Entry point: basic programming knowledge.', 'Điểm vào: biết lập trình cơ bản.'),
  prerequisite: (stage: string): Pair => pair(`Recommended first: ${stage}.`, `Nên học trước: ${stage}.`),
  relatedPractice: pair('Related practice lesson', 'Bài thực hành liên quan'),
  lessonStats: (minutes: number, objectives: number): Pair => pair(`About ${minutes} minutes · ${objectives} objectives`, `Khoảng ${minutes} phút · ${objectives} mục tiêu`),
  startLesson: pair('Start lesson', 'Bắt đầu bài'),
  lessonDraft: pair('BEING AUTHORED · NOT AVAILABLE YET', 'ĐANG BIÊN SOẠN · CHƯA MỞ BÀI'),
  topicDraft: pair('LESSON IS BEING AUTHORED', 'BÀI HỌC ĐANG BIÊN SOẠN'),
  finishTitle: pair('Turn knowledge into a product', 'Biến kiến thức thành sản phẩm'),
  finishBody: pair('Build, test, and deploy an application to a runtime environment.', 'Xây dựng, kiểm thử và triển khai ứng dụng lên môi trường chạy.'),
  labTitle: pair('HTTP Fundamentals → Request lifecycle', 'HTTP Fundamentals → Request lifecycle'),
  labBody: pair('Two bilingual lessons: learn HTTP through diagrams, before-and-after examples, and self-checks, then trace a request through the backend.', 'Hai bài học song ngữ giúp bạn hiểu HTTP qua sơ đồ, ví dụ trước–sau và câu hỏi tự kiểm tra, rồi theo dấu một request trong backend.'),
  learnSample: pair('Study the sample lesson', 'Học bài mẫu'),
  viewLab: pair('View the lab', 'Xem phòng lab'),
  pageEyebrow: pair('Knowledge map / Explore every branch', 'Bản đồ kiến thức / Khám phá từng nhánh'),
  javaDescription: pair('See the whole journey. Open each branch. Build your first backend.', 'Xem toàn bộ lộ trình, khám phá từng nhánh và xây dựng backend đầu tiên của bạn.'),
  devopsTitle: pair('DevOps & operations', 'DevOps & vận hành'),
  devopsDescription: pair('From runtime environments to deployment and operations. Docker connects this journey to Java.', 'Học từ môi trường chạy đến triển khai và vận hành. Docker kết nối lộ trình này với Java Backend.'),
  chooseRoadmap: pair('Choose a roadmap', 'Chọn lộ trình'),
} as const;

const relatedLessonTitles: Record<string, Pair> = {
  'api-debug-500': pair('Rescue an API returning 500', 'Xử lý API trả lỗi 500'),
};

function KnowledgeMap({ stages }: { stages: MapStage[] }) {
  const { text } = useLocale();
  const [searchParams, setSearchParams] = useSearchParams();
  const stageIds = stages.map((stage) => stage.id);
  const topicIds = new Set(stages.flatMap((stage) => stage.topics.map((_, topic) => `${stage.id}-${topic}`)));
  const [expanded, setExpanded] = useState<string[]>(() => {
    const open = searchParams.get('open');
    if (open === 'none') return [];
    const saved = open?.split(',').filter((id) => stageIds.includes(id));
    return saved?.length ? saved : [stages[0].id];
  });
  const [selected, setSelected] = useState<string | null>(() => {
    const saved = searchParams.get('topic');
    return saved && topicIds.has(saved) ? saved : null;
  });
  const total = stages.reduce((sum, stage) => sum + stage.topics.length, 0);
  function saveContext(nextExpanded: string[], nextSelected: string | null) {
    setExpanded(nextExpanded);
    setSelected(nextSelected);
    const params = new URLSearchParams();
    params.set('open', nextExpanded.length ? nextExpanded.join(',') : 'none');
    if (nextSelected) params.set('topic', nextSelected);
    setSearchParams(params, { replace: true, preventScrollReset: true });
  }
  function toggleAll() {
    const collapse = expanded.length === stages.length;
    saveContext(collapse ? [] : stageIds, collapse ? null : selected);
  }
  function toggleStage(stageId: string, open: boolean) {
    const nextExpanded = open ? expanded.filter((id) => id !== stageId) : [...expanded, stageId];
    const nextSelected = open && selected?.startsWith(`${stageId}-`) ? null : selected;
    saveContext(nextExpanded, nextSelected);
  }
  return <>
    <div className="map-toolbar"><div><strong>{text(copy.mapSummary(stages.length, total))}</strong><p className="small muted">{text(copy.mapHint)}</p></div><button className="button" onClick={toggleAll}>{text(expanded.length === stages.length ? copy.collapseAll : copy.expandAll)}</button></div>
    <div className="map-legend"><span><i /> {text(copy.suggestedOrder)}</span><span><i /> {text(copy.knowledgeBranch)}</span><span>{text(copy.draftLegend)}</span></div>
    <div className="knowledge-map">
      <div className="map-origin"><Compass aria-hidden="true" /> {text(copy.start)}</div>
      <ol className="map-stages">{stages.map((stage, index) => {
        const Icon = symbols[stage.icon];
        const open = expanded.includes(stage.id);
        return <li key={stage.id} className={`map-stage tone-${stage.tone} ${open ? 'is-expanded' : ''}`}>
          <button className="stage-node" aria-expanded={open} aria-controls={`branches-${stage.id}`} onClick={() => toggleStage(stage.id, open)}>
            <span className="stage-number">{String(index + 1).padStart(2, '0')}</span><span className="stage-icon"><Icon aria-hidden="true" /></span><span className="stage-copy"><small>{text(stage.label)}</small><strong>{text(stage.title)}</strong><span>{text(copy.topicCount(stage.topics.length))}</span></span><ChevronDown className="stage-chevron" aria-hidden="true" />
          </button>
          <div id={`branches-${stage.id}`} className="topic-branches" hidden={!open}>{stage.topics.map(([title, goal, lessonId], topic) => {
            const id = `${stage.id}-${topic}`;
            const lesson = lessonId ? getLesson(lessonId) : undefined;
            const topicTitle = text(title);
            return <div className="topic-item" key={id}><button className="topic-node" aria-expanded={selected === id} aria-controls={`detail-${id}`} onClick={() => saveContext(expanded, selected === id ? null : id)}><span className="topic-dot" /><span>{topicTitle}</span><ArrowRight aria-hidden="true" /></button><section id={`detail-${id}`} className="topic-detail" hidden={selected !== id} aria-label={text(copy.topicDetail(topicTitle))}><span className="eyebrow">{text(copy.outcome)}</span><p>{text(goal)}</p><p className="small muted">{index === 0 ? text(copy.entryPoint) : text(copy.prerequisite(text(stages[index - 1].title)))}</p>{lesson ? <div className="topic-lesson"><span className="eyebrow">{text(copy.relatedPractice)}</span><strong>{text(relatedLessonTitles[lesson.id] ?? pair(lesson.title, lesson.title))}</strong><span className="small muted">{text(copy.lessonStats(lesson.estimatedMinutes, lesson.objectives.length))}</span>{lesson.contentStatus === 'published' ? <Link className="button primary" to={`/learn/${lesson.id}/${lesson.entryStepId}`}>{text(copy.startLesson)} <ArrowRight aria-hidden="true" /></Link> : <span className="tag">{text(copy.lessonDraft)}</span>}</div> : <span className="tag">{text(copy.topicDraft)}</span>}</section></div>;
          })}</div>
        </li>;
      })}</ol>
      <div className="map-finish"><Rocket aria-hidden="true" /><strong>{text(copy.finishTitle)}</strong><span>{text(copy.finishBody)}</span></div>
    </div>
    <div className="map-lab panel"><div><strong>{text(copy.labTitle)}</strong><p className="muted small">{text(copy.labBody)}</p></div><div className="actions"><Link className="button" to="/learn/http-foundations">{text(copy.learnSample)} <ArrowRight aria-hidden="true" /></Link><Link className="text-link" to={paths.lab}>{text(copy.viewLab)}</Link></div></div>
  </>;
}

export function JourneyPage() {
  const { text } = useLocale();
  const { roadmapId } = useParams();
  if (roadmapId !== 'java-backend' && roadmapId !== 'devops') return <NotFoundPage />;
  const isJava = roadmapId === 'java-backend';
  return <><PageHeading eyebrow={text(copy.pageEyebrow)} title={isJava ? 'Java Backend' : text(copy.devopsTitle)} description={text(isJava ? copy.javaDescription : copy.devopsDescription)} /><nav className="track-nav" aria-label={text(copy.chooseRoadmap)}><NavLink to={paths.journey}>Java Backend</NavLink><NavLink to={paths.devops}>DevOps</NavLink></nav><KnowledgeMap key={roadmapId} stages={isJava ? javaStages : devopsStages} /></>;
}
