import { useCallback, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import {
  Activity, AlertTriangle, ArrowDownUp, ArrowLeft, ArrowRight,
  BarChart3, CalendarDays, ChevronDown, ChevronRight, CircleHelp,
  Filter, KeyRound, LoaderCircle,
  LogOut, MapPin, Search, ShieldCheck, Ticket, Users, UserRound, X,
} from 'lucide-react'
import { api, ApiError } from './api/client'
import type {
  AdminUser, Assessment, Concert, ConcertDetail, ConcertUser, DashboardSummary,
  AdminUserDetail, PageResponse, UserEvent,
} from './api/types'

type View = 'concerts' | 'concert' | 'concert-users' | 'users' | 'user'
type Back = 'users' | 'concert-users'

const number = (value: number | null | undefined) => value == null ? '—' : new Intl.NumberFormat('ko-KR').format(value)
const fmt = (parsed: Date, value: string) =>
  Number.isNaN(parsed.getTime()) ? value.replace('T', ' ').slice(0, 19)
    : new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(parsed)
/** 사용자·평가 시각은 서버가 타임존 없이 내려주므로 변환하지 않고 그대로 보여줍니다. */
const dateTime = (value?: string | null) => value ? fmt(new Date(value), value) : '—'
/** 공연·이벤트·정책 시각은 오프셋이 붙어 있으므로 브라우저 시간대로 보여줍니다. */
const dateTimeUtc = (value?: string | null) => {
  if (!value) return '—'
  const iso = /[zZ]|[+-]\d{2}:?\d{2}$/.test(value) ? value : `${value}Z`
  return fmt(new Date(iso), value)
}
const score = (value: number | null | undefined) => value == null ? '—' : value.toFixed(2)
const explainError = (error: unknown) => error instanceof ApiError ? `${error.message} (${error.code})` : '요청 중 오류가 발생했습니다.'

function concertStatus(c: Concert): { label: string; tone: string } {
  const now = Date.now()
  if (now < new Date(c.startsAt).getTime()) return { label: '예정', tone: 'info' }
  if (now <= new Date(c.endsAt).getTime()) return { label: '진행 중', tone: 'success' }
  return { label: '종료', tone: 'neutral' }
}

const PAGE = 10

function usePaged<T>() {
  const [data, setData] = useState<PageResponse<T> | null>(null)
  const [loading, setLoading] = useState(false)
  return { data, loading, setData, setLoading }
}

function App() {
  const [serviceId, setServiceId] = useState('')
  const [adminKey, setAdminKey] = useState('')
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [connected, setConnected] = useState(false)
  const [connecting, setConnecting] = useState(false)
  const [view, setView] = useState<View>('concerts')
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')

  // 공연 목록
  const concerts = usePaged<Concert>()
  const [concertSearch, setConcertSearch] = useState('')
  const [concertSort, setConcertSort] = useState('startsAt,desc')
  const [concertPage, setConcertPage] = useState(0)
  // 공연 상세 / 공연별 사용자
  const [selectedConcert, setSelectedConcert] = useState('')
  const [concertDetail, setConcertDetail] = useState<ConcertDetail | null>(null)
  const [concertDetailLoading, setConcertDetailLoading] = useState(false)
  const [recentUsers, setRecentUsers] = useState<ConcertUser[]>([])
  const cUsers = usePaged<ConcertUser>()
  const [cuSort, setCuSort] = useState<'desc' | 'asc'>('desc')
  const [cuTier, setCuTier] = useState('')
  // 전체 사용자
  const users = usePaged<AdminUser>()
  const [userSearch, setUserSearch] = useState('')
  const [userTier, setUserTier] = useState('')
  const [userStatus, setUserStatus] = useState('')
  const [minCredit, setMinCredit] = useState('')
  const [maxCredit, setMaxCredit] = useState('')
  const [creditSort, setCreditSort] = useState<'desc' | 'asc'>('desc')
  // 사용자 상세
  const [selectedUserId, setSelectedUserId] = useState('')
  const [userDetail, setUserDetail] = useState<AdminUserDetail | null>(null)
  const [userEvents, setUserEvents] = useState<UserEvent[]>([])
  const [assessments, setAssessments] = useState<Assessment[]>([])
  const [detailLoading, setDetailLoading] = useState(false)
  const [backTo, setBackTo] = useState<Back>('users')

  const connect = async () => {
    setError('')
    setNotice('')
    if (!serviceId.trim() || !adminKey.trim()) {
      setError('서비스 ID와 관리자 API Key를 입력해 주세요.')
      return
    }
    setConnecting(true)
    try {
      setSummary(await api.summary(adminKey, serviceId.trim()))
      setConnected(true)
      setView('concerts')
      setNotice('관리자 인증이 완료되었습니다. 관리자 API Key는 현재 화면의 메모리에만 보관합니다.')
    } catch (e) {
      setConnected(false)
      setSummary(null)
      setError(`관리자 인증 실패: ${explainError(e)}. 관리자 키(BTS_ADMIN_API_KEYS에 등록된 키)인지 확인해 주세요.`)
    } finally {
      setConnecting(false)
    }
  }

  const sid = serviceId.trim()

  const loadConcerts = useCallback(async (page = 0, sort = concertSort) => {
    concerts.setLoading(true)
    setError('')
    try {
      concerts.setData(await api.concerts(adminKey, sid, { page, size: PAGE, sort }))
      setConcertPage(page)
    } catch (e) {
      setError(`공연 목록 조회 실패: ${explainError(e)}`)
    } finally {
      concerts.setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [adminKey, sid, concertSort])

  useEffect(() => { if (connected) void loadConcerts(0) }, [connected]) // eslint-disable-line react-hooks/exhaustive-deps

  const openConcert = async (concertId: string) => {
    setSelectedConcert(concertId)
    setConcertDetail(null)
    setRecentUsers([])
    setView('concert')
    setConcertDetailLoading(true)
    setError('')
    try {
      const [detail, latest] = await Promise.all([
        api.concert(adminKey, sid, concertId),
        api.concertUsers(adminKey, sid, concertId, { page: 0, size: 8, sort: 'createdAt,desc' }),
      ])
      setConcertDetail(detail)
      setRecentUsers(latest.content)
    } catch (e) {
      setError(`공연 상세 조회 실패: ${explainError(e)}`)
    } finally {
      setConcertDetailLoading(false)
    }
  }

  const loadConcertUsers = async (concertId: string, page = 0, sortDir = cuSort, tier = cuTier) => {
    cUsers.setLoading(true)
    setError('')
    try {
      cUsers.setData(await api.concertUsers(adminKey, sid, concertId, {
        page, size: PAGE, sort: `creditScore,${sortDir}`, tier: tier || undefined,
      }))
    } catch (e) {
      setError(`공연별 사용자 조회 실패: ${explainError(e)}`)
    } finally {
      cUsers.setLoading(false)
    }
  }

  const goConcertUsers = async () => {
    setView('concert-users')
    await loadConcertUsers(selectedConcert, 0)
  }

  const loadUsers = async (page = 0, over: { sort?: 'asc' | 'desc' } = {}) => {
    users.setLoading(true)
    setError('')
    try {
      users.setData(await api.users(adminKey, {
        serviceId: sid,
        page,
        size: PAGE,
        sort: `creditScore,${over.sort ?? creditSort}`,
        userId: userSearch.trim() || undefined,
        tier: userTier || undefined,
        accountStatus: userStatus || undefined,
        minCreditScore: minCredit === '' ? undefined : Number(minCredit),
        maxCreditScore: maxCredit === '' ? undefined : Number(maxCredit),
      }))
    } catch (e) {
      setError(`사용자 목록 조회 실패: ${explainError(e)}`)
    } finally {
      users.setLoading(false)
    }
  }

  const goUsers = async () => {
    setView('users')
    await loadUsers(0)
  }

  const openUser = async (userId: string, from: Back) => {
    setSelectedUserId(userId)
    setUserDetail(null)
    setUserEvents([])
    setAssessments([])
    setBackTo(from)
    setView('user')
    setDetailLoading(true)
    setError('')
    try {
      const [detail, events, assess] = await Promise.all([
        api.user(adminKey, userId),
        api.userEvents(adminKey, userId),
        api.userAssessments(adminKey, userId),
      ])
      setUserDetail(detail)
      setUserEvents(events.content)
      setAssessments(assess.content)
    } catch (e) {
      setError(`사용자 상세 조회 실패: ${explainError(e)}`)
    } finally {
      setDetailLoading(false)
    }
  }

  const logout = () => {
    setConnected(false)
    setSummary(null)
    setAdminKey('')
    concerts.setData(null)
    users.setData(null)
    cUsers.setData(null)
    setView('concerts')
    setNotice('연결을 종료했습니다. 인증 키는 화면 메모리에서 초기화했습니다.')
    setError('')
  }

  const tiers = Object.keys(summary?.usersByTier ?? {})
  const concertRows = (concerts.data?.content ?? []).filter(c => {
    const q = concertSearch.trim().toLowerCase()
    return !q || c.name.toLowerCase().includes(q) || c.concertId.toLowerCase().includes(q)
  })

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand"><div className="brand-mark"><ShieldCheck size={19} /></div><div><strong>BTS Admin</strong><span>Ticket Fraud Detection System</span></div></div>
        <div className="connection-fields">
          <label className="top-field"><span>서비스 ID</span><input value={serviceId} onChange={e => setServiceId(e.target.value)} onKeyDown={e => e.key === 'Enter' && !connected && connect()} placeholder="ticket-service-a" disabled={connected} /></label>
          <label className="top-field key-field"><span>관리자 API Key</span><input type="password" value={adminKey} onChange={e => setAdminKey(e.target.value)} onKeyDown={e => e.key === 'Enter' && !connected && connect()} placeholder="관리자 인증 키" disabled={connected} /></label>
          <button className={`connect-button ${connected ? 'connected' : ''}`} onClick={connected ? logout : connect} disabled={connecting}>
            {connecting ? <LoaderCircle className="spin" size={16} /> : connected ? <LogOut size={16} /> : <KeyRound size={16} />}
            {connecting ? '확인 중' : connected ? '연결 종료' : '인증 및 연결'}
          </button>
        </div>
      </header>

      <div className="subbar">
        <div className="subbar-left"><span className={`status-dot ${connected ? 'on' : ''}`} />{connected ? <><b>{serviceId}</b><span className="muted">서비스 관리자 연결됨</span>{summary && <span className="summary-context">사용자 {number(summary.totalUsers)}명 · 이벤트 {number(summary.totalEvents)}건</span>}</> : <span className="muted">서비스 인증이 필요합니다</span>}</div>
      </div>

      <div className="layout">
        <aside className="sidebar">
          <div className="workspace-label">WORKSPACE</div>
          <button className={`nav-item ${view === 'concerts' || view === 'concert' || view === 'concert-users' ? 'active' : ''}`} onClick={() => { setView('concerts'); if (connected) void loadConcerts(concertPage) }}><Ticket size={18} /><span>공연 조회</span><ChevronRight size={15} className="nav-chevron" /></button>
          <button className={`nav-item ${view === 'users' || view === 'user' ? 'active' : ''}`} onClick={goUsers}><Users size={18} /><span>사용자 조회</span><ChevronRight size={15} className="nav-chevron" /></button>
          <div className="sidebar-bottom"><div className="sidebar-help"><CircleHelp size={17} /><div><b>API 연동 안내</b><span>제공되지 않는 항목은<br />각 영역에 표시합니다.</span></div></div><div className="version">BTS Admin Console <span>v1.1.0</span></div></div>
        </aside>

        <main className="main-content">
          {notice && <div className="notice"><Activity size={16} /><span>{notice}</span><button onClick={() => setNotice('')} aria-label="안내 닫기"><X size={15} /></button></div>}
          {error && <div className="error-banner"><AlertTriangle size={16} /><span>{error}</span><button onClick={() => setError('')} aria-label="오류 닫기"><X size={15} /></button></div>}
          {!connected ? <ConnectionLanding onConnect={connect} connecting={connecting} /> : <>
            {view === 'concerts' && <ConcertList rows={concertRows} data={concerts.data} loading={concerts.loading} search={concertSearch} setSearch={setConcertSearch} sort={concertSort} setSort={s => { setConcertSort(s); void loadConcerts(0, s) }} page={concertPage} onPage={p => loadConcerts(p)} onSelect={openConcert} onReload={() => loadConcerts(concertPage)} />}
            {view === 'concert' && <ConcertDashboard detail={concertDetail} concertId={selectedConcert} recent={recentUsers} loading={concertDetailLoading} onBack={() => setView('concerts')} onParticipants={goConcertUsers} onOpenUser={id => openUser(id, 'concert-users')} />}
            {view === 'concert-users' && <ConcertParticipants concertId={selectedConcert} title={concertDetail?.concert.name} data={cUsers.data} loading={cUsers.loading} tiers={tiers} tier={cuTier} sort={cuSort} onTier={t => { setCuTier(t); void loadConcertUsers(selectedConcert, 0, cuSort, t) }} onSort={s => { setCuSort(s); void loadConcertUsers(selectedConcert, 0, s, cuTier) }} onPage={p => loadConcertUsers(selectedConcert, p)} onBack={() => setView('concert')} onOpenUser={id => openUser(id, 'concert-users')} />}
            {view === 'users' && <UserList tiers={tiers} data={users.data} loading={users.loading} search={userSearch} setSearch={setUserSearch} tier={userTier} setTier={setUserTier} status={userStatus} setStatus={setUserStatus} minCredit={minCredit} setMinCredit={setMinCredit} maxCredit={maxCredit} setMaxCredit={setMaxCredit} sort={creditSort} onSort={s => { setCreditSort(s); void loadUsers(0, { sort: s }) }} onSearch={() => loadUsers(0)} onPage={p => loadUsers(p)} onOpenUser={id => openUser(id, 'users')} />}
            {view === 'user' && <UserDetailPage userId={selectedUserId} detail={userDetail} events={userEvents} assessments={assessments} loading={detailLoading} backLabel={backTo === 'users' ? '사용자 목록으로' : '공연별 사용자로'} onBack={() => setView(backTo)} />}
          </>}
        </main>
      </div>
    </div>
  )
}

function ConnectionLanding({ onConnect, connecting }: { onConnect: () => void; connecting: boolean }) {
  return <div className="landing"><div className="landing-icon"><ShieldCheck size={28} /></div><div className="eyebrow">BTS SECURITY CONSOLE</div><h1>관리자 대시보드에<br />오신 것을 환영합니다.</h1><p>상단에 서비스 ID와 관리자 API Key를 입력한 뒤<br />인증 및 연결을 진행해 주세요.</p><div className="landing-steps"><div><span>01</span><b>서비스 ID 입력</b><small>조회할 티켓팅 서비스를 지정합니다.</small></div><div><span>02</span><b>관리자 API Key 인증</b><small>관리 권한이 있는 키인지 확인합니다.</small></div><div><span>03</span><b>대시보드 조회</b><small>인증된 서비스 범위의 데이터를 조회합니다.</small></div></div><button className="primary-button" onClick={onConnect} disabled={connecting}>{connecting ? <LoaderCircle className="spin" size={17} /> : <KeyRound size={17} />} 상단에서 인증하기</button></div>
}

function PageHeading({ eyebrow, title, description, action }: { eyebrow: string; title: string; description: string; action?: ReactNode }) {
  return <div className="page-heading"><div><div className="eyebrow">{eyebrow}</div><h1>{title}</h1><p>{description}</p></div>{action}</div>
}

function ApiMissing({ children }: { children: ReactNode }) {
  return <span className="api-missing"><AlertTriangle size={12} />{children}</span>
}

function StatCard({ label, value, caption, icon, muted = false }: { label: string; value: string; caption: string; icon: ReactNode; muted?: boolean }) {
  return <div className={`stat-card ${muted ? 'stat-muted' : ''}`}><div className="stat-top"><span>{label}</span><span className="stat-icon">{icon}</span></div><strong>{value}</strong><small>{caption}</small></div>
}

function Pager({ data, loading, onPage }: { data: PageResponse<unknown> | null; loading: boolean; onPage: (p: number) => void }) {
  if (!data) return null
  const total = data.totalElements
  const from = total ? data.page * data.size + 1 : 0
  const to = Math.min((data.page + 1) * data.size, total)
  return <div className="pagination"><span>{number(total)}건 중 {from}–{to}</span><div><button disabled={data.page <= 0 || loading} onClick={() => onPage(data.page - 1)}><ArrowLeft size={15} />이전</button><span className="page-number">{data.page + 1} / {Math.max(data.totalPages, 1)}</span><button disabled={!data.hasNext || loading} onClick={() => onPage(data.page + 1)}>다음<ArrowRight size={15} /></button></div></div>
}

function ConcertList({ rows, data, loading, search, setSearch, sort, setSort, page, onPage, onSelect, onReload }: {
  rows: Concert[]; data: PageResponse<Concert> | null; loading: boolean; search: string; setSearch: (v: string) => void
  sort: string; setSort: (v: string) => void; page: number; onPage: (p: number) => void; onSelect: (id: string) => void; onReload: () => void
}) {
  return <>
    <PageHeading eyebrow="CONCERT MANAGEMENT" title="공연 조회" description="BTS에 등록된 공연을 확인하고 공연별 예매 시도 현황을 조회합니다." action={<button className="secondary-button" onClick={onReload} disabled={loading}><Activity size={15} /> 새로고침</button>} />
    <div className="search-row">
      <div className="search-box"><Search size={17} /><input value={search} onChange={e => setSearch(e.target.value)} placeholder="공연명 또는 공연 ID로 검색 (현재 페이지)" /></div>
      <select className="inline-select" value={sort} onChange={e => setSort(e.target.value)} aria-label="정렬">
        <option value="startsAt,desc">공연일 최신순</option><option value="startsAt,asc">공연일 오래된순</option>
        <option value="name,asc">공연명 가나다순</option><option value="concertId,asc">공연 ID순</option>
      </select>
      <span className="result-count">총 {number(data?.totalElements ?? 0)}개 공연</span>
    </div>
    {loading && !data && <LoadingState label="공연 목록을 불러오는 중입니다." />}
    {data && !loading && data.totalElements === 0 && <div className="empty-state"><div className="empty-icon"><CalendarDays size={22} /></div><h3>등록된 공연이 없습니다</h3><p>외부 서비스가 <code>PUT /api/v1/concerts/{'{concertId}'}</code> 로 공연을 등록하면 이곳에 나타납니다.</p></div>}
    {data && data.totalElements > 0 && rows.length === 0 && <div className="empty-state"><div className="empty-icon"><Search size={22} /></div><h3>검색 결과가 없습니다</h3><p>현재 페이지에서 일치하는 공연이 없습니다.</p></div>}
    {rows.length > 0 && <div className="concert-list">{rows.map(c => { const st = concertStatus(c); return <button className="concert-card" key={c.concertId} onClick={() => onSelect(c.concertId)}><div className="concert-date"><CalendarDays size={17} /><span>{new Intl.DateTimeFormat('ko-KR', { month: '2-digit', day: '2-digit' }).format(new Date(c.startsAt))}</span></div><div className="concert-main"><div className="concert-title">{c.name}</div><div className="concert-meta"><span><MapPin size={11} /> {c.venue}</span><span>{dateTimeUtc(c.startsAt)} – {dateTimeUtc(c.endsAt)}</span><span className="mono">{c.concertId}</span></div></div><div className="concert-status"><span className={`status-pill ${st.tone}`}>{st.label}</span><ChevronRight size={18} /></div></button> })}</div>}
    <Pager data={data} loading={loading} onPage={onPage} />
    <span hidden>{page}</span>
  </>
}

function ConcertDashboard({ detail, concertId, recent, loading, onBack, onParticipants, onOpenUser }: {
  detail: ConcertDetail | null; concertId: string; recent: ConcertUser[]; loading: boolean; onBack: () => void; onParticipants: () => void; onOpenUser: (id: string) => void
}) {
  const c = detail?.concert
  const st = c ? concertStatus(c) : null
  return <>
    <button className="back-link" onClick={onBack}><ArrowLeft size={16} /> 공연 목록으로</button>
    <PageHeading eyebrow="CONCERT OVERVIEW" title={c?.name ?? concertId} description={c ? `${c.venue} · ${dateTimeUtc(c.startsAt)} – ${dateTimeUtc(c.endsAt)}` : '공연 정보를 불러오는 중입니다.'} action={<div className="heading-actions">{st && <span className={`status-pill ${st.tone}`}>{st.label}</span>}<button className="primary-button" onClick={onParticipants} disabled={!c}><Users size={16} /> 예매자 목록 조회</button></div>} />
    <div className="stats-grid four">
      <StatCard label="예매 시도" value={loading ? '…' : number(detail?.bookingAttemptCount)} caption="이 공연의 예매 시도 건수" icon={<Ticket size={17} />} />
      <StatCard label="예매 시도 사용자" value={loading ? '…' : number(detail?.bookingAttemptUserCount)} caption="시작 이력이 있는 고유 사용자" icon={<Users size={17} />} />
      <StatCard label="이상 탐지 · 제재" value="—" caption="공연별 이상 탐지·제재 집계 API 미제공" icon={<AlertTriangle size={17} />} muted />
      <StatCard label="평균 Credit" value="—" caption="공연별 평균 Credit API 미제공" icon={<Activity size={17} />} muted />
    </div>
    <section className="panel chart-panel"><div className="panel-heading"><div><h2>시간대별 예매 시도 추이</h2><p>공연별 시계열 통계</p></div><ApiMissing>시계열 API 미제공</ApiMissing></div>
      <div className="chart-empty"><BarChart3 size={24} /><span>관리자 키로 조회할 수 있는 공연별 시계열 API가 없어 그래프를 표시하지 않습니다.</span></div>
      <div className="chart-footnote"><AlertTriangle size={13} />예매 시도 건수와 사용자 수는 합계만 제공됩니다. 10초 단위 추이와 이상 탐지 추이는 백엔드 API가 추가되면 연결할 수 있습니다.</div>
    </section>
    <div className="section-row"><div><h2>최근 가입 사용자 중 예매 시도자</h2><p>이 공연에 예매를 시도한 사용자 일부입니다. 전체는 ‘예매자 목록 조회’에서 확인하세요.</p></div><span className="count-chip">{number(recent.length)}명 표시</span></div>
    <div className="table-panel"><table><thead><tr><th>사용자</th><th>Credit</th><th>마지막 예매 시도</th><th /></tr></thead><tbody>{recent.map(({ user, lastBookingAttemptAt }) => <tr key={user.userId}><td><b>{user.externalUserId}</b><small className="sub-id">{user.userId}</small></td><td><b className="credit-value">{number(user.creditScore)}</b></td><td>{dateTimeUtc(lastBookingAttemptAt)}</td><td><button className="table-action" onClick={() => onOpenUser(user.userId)}>상세 조회 <ChevronRight size={14} /></button></td></tr>)}{!loading && !recent.length && <tr><td colSpan={4} className="empty-cell">예매를 시도한 사용자가 없습니다.</td></tr>}{loading && <tr><td colSpan={4} className="empty-cell"><LoaderCircle className="spin" size={18} /> 불러오는 중입니다.</td></tr>}</tbody></table></div>
  </>
}

function AnomalyCell({ value }: { value: number | null }) {
  if (value == null) return <span className="unavailable">평가 없음</span>
  return <b className={value >= 0.7 ? 'risk-high' : ''}>{score(value)}</b>
}

function ConcertParticipants({ concertId, title, data, loading, tiers, tier, sort, onTier, onSort, onPage, onBack, onOpenUser }: {
  concertId: string; title?: string; data: PageResponse<ConcertUser> | null; loading: boolean; tiers: string[]; tier: string; sort: 'asc' | 'desc'
  onTier: (t: string) => void; onSort: (s: 'asc' | 'desc') => void; onPage: (p: number) => void; onBack: () => void; onOpenUser: (id: string) => void
}) {
  return <>
    <button className="back-link" onClick={onBack}><ArrowLeft size={16} /> 공연 상세로</button>
    <PageHeading eyebrow="CONCERT PARTICIPANTS" title="공연별 사용자 조회" description={`${title ?? concertId} 공연에 예매를 시도한 사용자입니다.`} />
    <div className="inline-warning"><CircleHelp size={16} /><span>이상 점수는 이 공연에 대한 평가가 아니라 사용자 전체의 최신 평가입니다.</span></div>
    <div className="section-row"><div><h2>예매 시도 사용자</h2><p>총 {number(data?.totalElements)}명</p></div>
      <div className="heading-actions">
        <select className="inline-select" value={tier} onChange={e => onTier(e.target.value)} aria-label="등급"><option value="">전체 등급</option>{tiers.map(t => <option key={t} value={t}>{t}</option>)}</select>
        <button className="sort-button" onClick={() => onSort(sort === 'desc' ? 'asc' : 'desc')}><ArrowDownUp size={15} />Credit {sort === 'desc' ? '내림차순' : '오름차순'} <ChevronDown size={14} /></button>
      </div></div>
    <div className="table-panel"><table><thead><tr><th>사용자</th><th>등급</th><th>Credit</th><th>이상 점수</th><th>마지막 예매 시도</th><th>상세</th></tr></thead><tbody>{loading ? <tr><td colSpan={6} className="empty-cell"><LoaderCircle className="spin" size={18} /> 불러오는 중입니다.</td></tr> : (data?.content ?? []).map(({ user, lastBookingAttemptAt }) => <tr key={user.userId}><td><b>{user.externalUserId}</b><small className="sub-id">{user.userId}</small></td><td><TierBadge tier={user.tier} /></td><td><b className="credit-value">{number(user.creditScore)}</b></td><td><AnomalyCell value={user.anomalyScore} /></td><td>{dateTimeUtc(lastBookingAttemptAt)}</td><td><button className="table-action" onClick={() => onOpenUser(user.userId)}>상세 조회 <ChevronRight size={14} /></button></td></tr>)}{!loading && !data?.content.length && <tr><td colSpan={6} className="empty-cell">예매를 시도한 사용자가 없습니다.</td></tr>}</tbody></table></div>
    <Pager data={data} loading={loading} onPage={onPage} />
  </>
}

function UserList({ tiers, data, loading, search, setSearch, tier, setTier, status, setStatus, minCredit, setMinCredit, maxCredit, setMaxCredit, sort, onSort, onSearch, onPage, onOpenUser }: {
  tiers: string[]; data: PageResponse<AdminUser> | null; loading: boolean; search: string; setSearch: (v: string) => void; tier: string; setTier: (v: string) => void
  status: string; setStatus: (v: string) => void; minCredit: string; setMinCredit: (v: string) => void; maxCredit: string; setMaxCredit: (v: string) => void
  sort: 'asc' | 'desc'; onSort: (v: 'asc' | 'desc') => void; onSearch: () => void; onPage: (page: number) => void; onOpenUser: (id: string) => void
}) {
  return <>
    <PageHeading eyebrow="USER MANAGEMENT" title="사용자 조회" description="서비스에 등록된 사용자의 등급, Credit, 최신 이상 점수를 조회합니다." />
    <div className="filter-panel"><div className="filter-title"><Filter size={16} />검색 및 필터</div><div className="filter-grid"><label className="filter-control search-control"><span>사용자 ID</span><div className="search-box"><Search size={15} /><input value={search} onChange={e => setSearch(e.target.value)} onKeyDown={e => e.key === 'Enter' && onSearch()} placeholder="사용자 ID 정확히 입력" /></div></label><label className="filter-control"><span>등급</span><select value={tier} onChange={e => setTier(e.target.value)}><option value="">전체 등급</option>{tiers.map(value => <option key={value} value={value}>{value}</option>)}</select></label><label className="filter-control"><span>상태</span><select value={status} onChange={e => setStatus(e.target.value)}><option value="">전체</option><option value="ACTIVE">ACTIVE</option><option value="SUSPENDED">SUSPENDED</option></select></label><label className="filter-control"><span>Credit 최소</span><input type="number" min="0" max="100" value={minCredit} onChange={e => setMinCredit(e.target.value)} placeholder="0" /></label><label className="filter-control"><span>Credit 최대</span><input type="number" min="0" max="100" value={maxCredit} onChange={e => setMaxCredit(e.target.value)} placeholder="100" /></label><button className="primary-button filter-submit" onClick={onSearch} disabled={loading}><Search size={15} />조회</button></div><div className="filter-note"><CircleHelp size={13} />사용자 ID는 내부 ID 완전 일치 검색입니다. 이상 점수 검색·정렬은 목록 API에서 지원하지 않습니다.</div></div>
    <div className="section-row"><div><h2>사용자 목록</h2><p>총 {number(data?.totalElements)}명의 사용자</p></div><button className="sort-button" onClick={() => onSort(sort === 'desc' ? 'asc' : 'desc')}><ArrowDownUp size={15} />Credit {sort === 'desc' ? '내림차순' : '오름차순'} <ChevronDown size={14} /></button></div>
    <div className="table-panel"><table><thead><tr><th>사용자</th><th>등급</th><th>Credit</th><th>이상 점수</th><th>상태</th><th /></tr></thead><tbody>{loading ? <tr><td colSpan={6} className="empty-cell"><LoaderCircle className="spin" size={18} /> 불러오는 중입니다.</td></tr> : (data?.content ?? []).map(user => <tr key={user.userId}><td><span className="user-id-cell"><span className="avatar">{user.externalUserId.slice(0, 1).toUpperCase()}</span><span><b>{user.externalUserId}</b><small>{user.userId}</small></span></span></td><td><TierBadge tier={user.tier} /></td><td><b className="credit-value">{number(user.creditScore)}</b></td><td><AnomalyCell value={user.anomalyScore} /></td><td><span className={`status-pill ${user.accountStatus === 'ACTIVE' ? 'success' : 'neutral'}`}>{user.accountStatus}</span></td><td><button className="table-action" onClick={() => onOpenUser(user.userId)}>조회 <ChevronRight size={14} /></button></td></tr>)}{!loading && data && data.content.length === 0 && <tr><td colSpan={6} className="empty-cell">조건에 맞는 사용자가 없습니다.</td></tr>}</tbody></table></div>
    <Pager data={data} loading={loading} onPage={onPage} />
  </>
}

function TierBadge({ tier }: { tier: string | null }) {
  if (!tier) return <span className="unavailable">—</span>
  return <span className={`tier-badge ${tier.toLowerCase()}`}>{tier}</span>
}

const ACTIONS: Record<string, string> = { ALLOW: '허용', REQUIRE_CAPTCHA: '캡차 요구', DELAY: '지연', RESTRICT: '제한' }
function ActionPill({ action }: { action: string | null }) {
  if (!action) return <span className="unavailable">—</span>
  const tone = action === 'ALLOW' ? 'success' : action === 'RESTRICT' ? 'danger' : action === 'DELAY' ? 'warn' : 'info'
  return <span className={`status-pill ${tone}`}>{ACTIONS[action] ?? action}</span>
}

function UserDetailPage({ userId, detail, events, assessments, loading, backLabel, onBack }: {
  userId: string; detail: AdminUserDetail | null; events: UserEvent[]; assessments: Assessment[]; loading: boolean; backLabel: string; onBack: () => void
}) {
  const user = detail?.user
  const policy = detail?.latestPolicy
  const featureRows = [
    ['bookingCount', '최근 30일 고유 예매 건수', '건'],
    ['cancelCount', '최근 30일 고유 취소 건수', '건'],
    ['cancelRate', '예매 대비 취소 비율', '%'],
    ['avgBookingInterval', '평균 예매 간격', '초'],
    ['shortIntervalBookingCount', '5초 이하 연속 예매 횟수', '회'],
    ['attendanceRate', '출석률', '%'],
    ['avgBookingDuration', '평균 예매 소요 시간', '초'],
  ]
  return <>
    <button className="back-link" onClick={onBack}><ArrowLeft size={16} /> {backLabel}</button>
    <PageHeading eyebrow="USER DETAIL" title={user?.externalUserId || userId} description="사용자의 최신 위험 평가, 정책 결정과 행동 이벤트 이력을 확인합니다." />
    {loading && <LoadingState label="사용자 상세 정보를 불러오는 중입니다." />}
    {!loading && user && <>
      <div className="user-summary-card"><div className="user-summary-main"><div className="avatar large">{user.externalUserId.slice(0, 1).toUpperCase()}</div><div><h2>{user.externalUserId}</h2><p className="mono">{user.userId}</p><span className={`status-pill ${user.accountStatus === 'ACTIVE' ? 'success' : 'neutral'}`}>{user.accountStatus}</span></div></div><div className="user-summary-metrics"><div><span>이상 점수</span><b className={user.anomalyScore != null && user.anomalyScore >= 0.7 ? 'risk-high' : ''}>{score(user.anomalyScore)}</b></div><div><span>Credit</span><b>{number(user.creditScore)}</b></div><div><span>등급</span><b><TierBadge tier={user.tier} /></b></div><div><span>최근 조치</span><b><ActionPill action={user.latestAction} /></b></div><div><span>최근 평가</span><b className="small-metric">{dateTime(user.lastAssessedAt)}</b></div></div></div>
      <section className="panel"><div className="panel-heading"><div><h2>최근 정책 결정</h2><p>정책 생성 당시 점수의 스냅샷입니다.</p></div></div>
        {policy ? <div className="policy-grid"><div><span>조치</span><ActionPill action={policy.action} /></div><div><span>상황</span><b>{policy.context}</b></div><div><span>당시 Credit</span><b>{number(policy.creditScore)}</b></div><div><span>당시 이상 점수</span><b>{score(policy.anomalyScore)}</b></div><div><span>결정 시각</span><b>{dateTimeUtc(policy.decidedAt)}</b></div><div className="policy-reason"><span>사유</span><b>{policy.reason}</b></div></div> : <div className="chart-empty"><span>아직 정책 결정이 없습니다.</span></div>}
      </section>
      <section className="panel"><div className="panel-heading"><div><h2>위험 평가 이력</h2><p>최근 평가 결과 (최대 10건)</p></div><span className="count-chip">{assessments.length}건</span></div><div className="table-panel inner-table"><table><thead><tr><th>평가 시각</th><th>이상 점수</th><th>연결된 조치</th><th>모델 버전</th></tr></thead><tbody>{assessments.map(a => <tr key={a.assessmentId}><td>{dateTime(a.assessedAt)}</td><td><AnomalyCell value={a.anomalyScore} /></td><td><ActionPill action={a.policyAction} /></td><td className="mono">{a.modelVersion ?? '—'}</td></tr>)}{!assessments.length && <tr><td colSpan={4} className="empty-cell">평가 이력이 없습니다.</td></tr>}</tbody></table></div></section>
      <section className="panel feature-panel"><div className="panel-heading"><div><h2>AI Feature</h2><p>최근 30일 행동 이벤트를 기준으로 계산하는 Feature 항목</p></div><ApiMissing>Feature 조회 API 미제공</ApiMissing></div><div className="feature-table"><div className="feature-row feature-head"><span>Feature</span><span>설명</span><span>계산값</span></div>{featureRows.map(([key, label, unit]) => <div className="feature-row" key={key}><code>{key}</code><span>{label}</span><span className="unavailable">미제공 {unit}</span></div>)}</div><div className="feature-note">백엔드에 Feature 계산 서비스와 DTO는 있지만 사용자별 계산 결과를 반환하는 조회 API는 아직 없습니다. 값을 임의로 만들지 않습니다.</div></section>
      <section className="panel history-panel"><div className="panel-heading"><div><h2>예매 및 행동 이력</h2><p>최근 수집된 사용자 이벤트 (최대 50건)</p></div><span className="count-chip">{events.length}건</span></div><div className="table-panel inner-table"><table><thead><tr><th>이벤트</th><th>발생 일시</th><th>예약 ID</th><th>티켓 ID</th><th>추가 정보</th></tr></thead><tbody>{events.map(event => <tr key={event.eventId}><td><EventBadge type={event.eventType} /></td><td>{dateTimeUtc(event.occurredAt)}</td><td className="mono">{event.bookingId || '—'}</td><td className="mono">{event.ticketId || '—'}</td><td>{event.metadata ? Object.entries(event.metadata).slice(0, 2).map(([k, v]) => `${k}: ${String(v)}`).join(' · ') : '—'}</td></tr>)}{!events.length && <tr><td colSpan={5} className="empty-cell">조회된 이벤트 이력이 없습니다.</td></tr>}</tbody></table></div></section>
    </>}
    {!loading && !user && <div className="empty-state"><div className="empty-icon"><UserRound size={22} /></div><h3>사용자 상세 정보를 불러오지 못했습니다</h3><p>관리자 키의 서비스 범위와 사용자 ID를 확인해 주세요.</p></div>}
  </>
}

function EventBadge({ type }: { type: string }) {
  const label: Record<string, string> = { BOOKING: '예매', CANCEL: '취소', CHECK_IN: '체크인', BOOKING_ATTEMPT: '예매 시도' }
  return <span className={`event-badge ${type.toLowerCase()}`}>{label[type] || type}</span>
}

function LoadingState({ label }: { label: string }) {
  return <div className="loading-state"><LoaderCircle className="spin" size={19} /><span>{label}</span></div>
}


export default App
