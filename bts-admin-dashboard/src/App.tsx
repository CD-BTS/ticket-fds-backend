import { useCallback, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import {
  Activity, AlertTriangle, ArrowDownUp, ArrowLeft, ArrowRight,
  BarChart3, CalendarDays, ChevronDown, ChevronRight, CircleHelp,
  Filter, KeyRound, LoaderCircle,
  LogOut, Search, ShieldCheck, Ticket, Users, UserRound, X,
} from 'lucide-react'
import {
  CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts'
import { api, ApiError } from './api/client'
import type {
  AdminUser, BookingAttempt, DashboardSummary, UserDetail, UserEvent,
} from './api/types'

type View = 'concerts' | 'concert' | 'concert-users' | 'users' | 'user'
type ConcertRow = { concertId: string; visibleAttempts: number; latestAt: string }

const number = (value: number | null | undefined) => value == null ? '—' : new Intl.NumberFormat('ko-KR').format(value)
const dateTime = (value?: string | null) => {
  if (!value) return '—'
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) return value.replace('T', ' ').slice(0, 19)
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(parsed)
}
const parseUtcDate = (value: string) => new Date(/[zZ]|[+-]\d{2}:?\d{2}$/.test(value) ? value : `${value}Z`)
const dateTimeUtc = (value?: string | null) => {
  if (!value) return '—'
  const parsed = parseUtcDate(value)
  return Number.isNaN(parsed.getTime()) ? value.replace('T', ' ').slice(0, 19) : new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(parsed)
}
const shortDateUtc = (value?: string | null) => {
  if (!value) return '—'
  const parsed = parseUtcDate(value)
  return Number.isNaN(parsed.getTime()) ? value : new Intl.DateTimeFormat('ko-KR', { month: '2-digit', day: '2-digit' }).format(parsed)
}
const explainError = (error: unknown) => error instanceof ApiError ? `${error.message} (${error.code})` : '요청 중 오류가 발생했습니다.'

function App() {
  const [serviceId, setServiceId] = useState('')
  const [adminKey, setAdminKey] = useState('')
  const [serviceKey, setServiceKey] = useState('')
  const [summary, setSummary] = useState<DashboardSummary | null>(null)
  const [showServiceKey, setShowServiceKey] = useState(false)
  const [connected, setConnected] = useState(false)
  const [connecting, setConnecting] = useState(false)
  const [view, setView] = useState<View>('concerts')
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [attempts, setAttempts] = useState<BookingAttempt[]>([])
  const [attemptTotal, setAttemptTotal] = useState(0)
  const [attemptLoading, setAttemptLoading] = useState(false)
  const [concertRows, setConcertRows] = useState<ConcertRow[]>([])
  const [concertSearch, setConcertSearch] = useState('')
  const [selectedConcert, setSelectedConcert] = useState('')
  const [concertAttempts, setConcertAttempts] = useState<BookingAttempt[]>([])
  const [concertAttemptTotal, setConcertAttemptTotal] = useState(0)
  const [concertLoading, setConcertLoading] = useState(false)
  const [users, setUsers] = useState<AdminUser[]>([])
  const [usersLoading, setUsersLoading] = useState(false)
  const [usersTotal, setUsersTotal] = useState(0)
  const [usersPage, setUsersPage] = useState(0)
  const [userSearch, setUserSearch] = useState('')
  const [userTier, setUserTier] = useState('')
  const [minCredit, setMinCredit] = useState('')
  const [maxCredit, setMaxCredit] = useState('')
  const [creditSort, setCreditSort] = useState<'desc' | 'asc'>('desc')
  const [selectedUserId, setSelectedUserId] = useState('')
  const [userDetail, setUserDetail] = useState<UserDetail | null>(null)
  const [userEvents, setUserEvents] = useState<UserEvent[]>([])
  const [detailLoading, setDetailLoading] = useState(false)

  const loadAttempts = useCallback(async (key = serviceKey) => {
    if (!key) {
      setAttempts([])
      setConcertRows([])
      setNotice('공연 목록은 예매 시도 데이터에서 공연 ID를 수집합니다. 서비스 API Key를 입력하면 조회할 수 있습니다.')
      return
    }
    setAttemptLoading(true)
    try {
      const result = await api.bookingAttempts(key, { page: 0, size: 100 })
      setAttempts(result.items ?? [])
      setAttemptTotal(result.bookingAttemptCount ?? 0)
      const grouped = new Map<string, ConcertRow>()
      for (const item of result.items ?? []) {
        if (!item.concertId) continue
        const current = grouped.get(item.concertId)
        if (!current) grouped.set(item.concertId, { concertId: item.concertId, visibleAttempts: 1, latestAt: item.startedAt })
        else {
          current.visibleAttempts += 1
          if (parseUtcDate(item.startedAt).getTime() > parseUtcDate(current.latestAt).getTime()) current.latestAt = item.startedAt
        }
      }
      setConcertRows([...grouped.values()].sort((a, b) => parseUtcDate(b.latestAt).getTime() - parseUtcDate(a.latestAt).getTime()))
      setNotice('공연 목록은 최근 예매 시도 100건에서 확인된 concertId 기준입니다. 공연명·공연일·예매 상태 API는 아직 제공되지 않습니다.')
    } catch (e) {
      setError(`예매 시도 조회 실패: ${explainError(e)}. 관리자 키가 아닌 해당 서비스의 API Key가 필요합니다.`)
    } finally {
      setAttemptLoading(false)
    }
  }, [serviceKey])

  const connect = async () => {
    setError('')
    setNotice('')
    if (!serviceId.trim() || !adminKey.trim()) {
      setError('서비스 ID와 관리자 API Key를 입력해 주세요.')
      return
    }
    setConnecting(true)
    try {
      const result = await api.summary(adminKey, serviceId.trim())
      setSummary(result)
      setConnected(true)
      setView('concerts')
      setNotice('관리자 인증이 완료되었습니다. 관리자 API Key는 현재 화면의 메모리에만 보관합니다.')
      if (serviceKey.trim()) await loadAttempts(serviceKey.trim())
      else {
        setAttempts([])
        setConcertRows([])
      }
    } catch (e) {
      setConnected(false)
      setSummary(null)
      setError(`관리자 인증 실패: ${explainError(e)}. 관리자 키(BTS_ADMIN_API_KEYS에 등록된 키)인지 확인해 주세요.`)
    } finally {
      setConnecting(false)
    }
  }

  const loadUsers = useCallback(async (page = 0) => {
    if (!adminKey || !connected) return
    setUsersLoading(true)
    setError('')
    try {
      const result = await api.users(adminKey, {
        serviceId,
        page,
        size: 10,
        sort: `creditScore,${creditSort}`,
        userId: userSearch.trim() || undefined,
        tier: userTier || undefined,
        minCreditScore: minCredit === '' ? undefined : Number(minCredit),
        maxCreditScore: maxCredit === '' ? undefined : Number(maxCredit),
      })
      setUsers(result.content)
      setUsersTotal(result.totalElements)
      setUsersPage(result.page)
    } catch (e) {
      setError(`사용자 목록 조회 실패: ${explainError(e)}`)
    } finally {
      setUsersLoading(false)
    }
  }, [adminKey, connected, serviceId, creditSort, userSearch, userTier, minCredit, maxCredit])

  const openUser = async (userId: string) => {
    setSelectedUserId(userId)
    setUserDetail(null)
    setUserEvents([])
    setView('user')
    if (!serviceKey) {
      setError('사용자 상세와 예매 이력은 현재 백엔드에서 서비스 API Key 인증을 사용합니다. 상단의 조회용 서비스 API Key를 입력해 주세요.')
      return
    }
    setDetailLoading(true)
    setError('')
    try {
      const [detail, events] = await Promise.all([
        api.userDetail(serviceKey, userId),
        api.userEvents(serviceKey, userId),
      ])
      setUserDetail(detail)
      setUserEvents(events.content)
    } catch (e) {
      setError(`사용자 상세 조회 실패: ${explainError(e)}`)
    } finally {
      setDetailLoading(false)
    }
  }

  const openConcert = async (concertId: string) => {
    setSelectedConcert(concertId)
    setView('concert')
    setConcertAttempts([])
    setConcertAttemptTotal(0)
    setConcertLoading(true)
    setError('')
    if (!serviceKey) {
      setConcertAttempts([])
      setConcertAttemptTotal(0)
      setConcertLoading(false)
      setError('공연별 예매 시도 조회에는 서비스 API Key가 필요합니다. 상단의 조회용 서비스 API Key를 입력해 주세요.')
      return
    }
    try {
      const result = await api.bookingAttempts(serviceKey, { concertId, page: 0, size: 100 })
      setConcertAttempts(result.items ?? [])
      setConcertAttemptTotal(result.bookingAttemptCount ?? 0)
    } catch (e) {
      setError(`공연 예매 시도 조회 실패: ${explainError(e)}`)
    } finally {
      setConcertLoading(false)
    }
  }

  const filteredConcerts = useMemo(() => concertRows.filter(row => row.concertId.toLowerCase().includes(concertSearch.toLowerCase())), [concertRows, concertSearch])
  const chartData = useMemo(() => {
    const counts = new Map<number, number>()
    for (const item of concertAttempts) {
      const time = parseUtcDate(item.startedAt).getTime()
      if (Number.isNaN(time)) continue
      const bucket = Math.floor(time / 10_000) * 10_000
      counts.set(bucket, (counts.get(bucket) ?? 0) + 1)
    }
    return [...counts.entries()].sort((a, b) => a[0] - b[0]).map(([time, count]) => ({
      label: new Date(time).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }),
      attempts: count,
    }))
  }, [concertAttempts])
  const participants = useMemo(() => {
    const map = new Map<string, BookingAttempt>()
    for (const item of concertAttempts) if (!map.has(item.userId)) map.set(item.userId, item)
    return [...map.values()]
  }, [concertAttempts])

  const goUsers = async () => {
    setView('users')
    await loadUsers(0)
  }
  const logout = () => {
    setConnected(false)
    setSummary(null)
    setAdminKey('')
    setServiceKey('')
    setUsers([])
    setAttempts([])
    setConcertRows([])
    setView('concerts')
    setNotice('연결을 종료했습니다. 인증 키는 화면 메모리에서 초기화했습니다.')
    setError('')
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand"><div className="brand-mark"><ShieldCheck size={19} /></div><div><strong>BTS Admin</strong><span>Ticket Fraud Detection System</span></div></div>
        <div className="connection-fields">
          <label className="top-field"><span>서비스 ID</span><input value={serviceId} onChange={e => setServiceId(e.target.value)} placeholder="ticket-service-a" disabled={connected} /></label>
          <label className="top-field key-field"><span>관리자 API Key</span><input type="password" value={adminKey} onChange={e => setAdminKey(e.target.value)} placeholder="관리자 인증 키" disabled={connected} /></label>
          <button className={`connect-button ${connected ? 'connected' : ''}`} onClick={connected ? logout : connect} disabled={connecting}>
            {connecting ? <LoaderCircle className="spin" size={16} /> : connected ? <LogOut size={16} /> : <KeyRound size={16} />}
            {connecting ? '확인 중' : connected ? '연결 종료' : '인증 및 연결'}
          </button>
        </div>
      </header>

      <div className="subbar">
        <div className="subbar-left"><span className={`status-dot ${connected ? 'on' : ''}`} />{connected ? <><b>{serviceId}</b><span className="muted">서비스 관리자 연결됨</span>{summary && <span className="summary-context">사용자 {number(summary.totalUsers)}명 · 이벤트 {number(summary.totalEvents)}건</span>}</> : <span className="muted">서비스 인증이 필요합니다</span>}</div>
        <button className="service-key-toggle" onClick={() => setShowServiceKey(v => !v)}><KeyRound size={14} /> 조회용 서비스 API Key {showServiceKey ? <ChevronDown size={14} /> : <ChevronRight size={14} />}</button>
      </div>
      {showServiceKey && <div className="service-key-panel"><div><b>서비스 API Key</b><p>예매 시도 및 사용자 상세·이력 API는 관리자 키가 아닌 서비스 키로 인증합니다. 키는 브라우저 메모리에만 유지됩니다.</p></div><input type="password" value={serviceKey} onChange={e => setServiceKey(e.target.value)} placeholder="해당 서비스에 발급된 API Key" /><button className="secondary-button" onClick={() => loadAttempts(serviceKey)}>예매 데이터 불러오기</button></div>}

      <div className="layout">
        <aside className="sidebar">
          <div className="workspace-label">WORKSPACE</div>
          <button className={`nav-item ${view === 'concerts' || view === 'concert' || view === 'concert-users' ? 'active' : ''}`} onClick={() => setView('concerts')}><Ticket size={18} /><span>공연 조회</span><ChevronRight size={15} className="nav-chevron" /></button>
          <button className={`nav-item ${view === 'users' || view === 'user' ? 'active' : ''}`} onClick={goUsers}><Users size={18} /><span>사용자 조회</span><ChevronRight size={15} className="nav-chevron" /></button>
          <div className="sidebar-bottom"><div className="sidebar-help"><CircleHelp size={17} /><div><b>API 연동 안내</b><span>화면별 API 제공 여부를<br />각 영역에 표시합니다.</span></div></div><div className="version">BTS Admin Console <span>v1.0.0</span></div></div>
        </aside>

        <main className="main-content">
          {notice && <div className="notice"><Activity size={16} /><span>{notice}</span><button onClick={() => setNotice('')} aria-label="안내 닫기"><X size={15} /></button></div>}
          {error && <div className="error-banner"><AlertTriangle size={16} /><span>{error}</span><button onClick={() => setError('')} aria-label="오류 닫기"><X size={15} /></button></div>}
          {!connected ? <ConnectionLanding onConnect={connect} connecting={connecting} /> : <>
            {view === 'concerts' && <ConcertList rows={filteredConcerts} search={concertSearch} setSearch={setConcertSearch} loading={attemptLoading} total={attemptTotal} onSelect={openConcert} onReload={() => loadAttempts()} hasServiceKey={!!serviceKey} />}
            {view === 'concert' && <ConcertDashboard concertId={selectedConcert} attempts={concertAttempts} total={concertAttemptTotal} chartData={chartData} loading={concertLoading} onBack={() => setView('concerts')} onParticipants={() => setView('concert-users')} />}
            {view === 'concert-users' && <ConcertParticipants concertId={selectedConcert} participants={participants} onBack={() => setView('concert')} onOpenUser={openUser} />}
            {view === 'users' && <UserList tiers={Object.keys(summary?.usersByTier ?? {})} users={users} loading={usersLoading} total={usersTotal} page={usersPage} search={userSearch} setSearch={setUserSearch} tier={userTier} setTier={setUserTier} minCredit={minCredit} setMinCredit={setMinCredit} maxCredit={maxCredit} setMaxCredit={setMaxCredit} sort={creditSort} setSort={setCreditSort} onSearch={() => loadUsers(0)} onPage={loadUsers} onOpenUser={openUser} />}
            {view === 'user' && <UserDetailPage userId={selectedUserId} detail={userDetail} events={userEvents} loading={detailLoading} onBack={() => setView('users')} />}
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

function ConcertList({ rows, search, setSearch, loading, total, onSelect, onReload, hasServiceKey }: {
  rows: ConcertRow[]; search: string; setSearch: (v: string) => void; loading: boolean; total: number; onSelect: (id: string) => void; onReload: () => void; hasServiceKey: boolean
}) {
  return <>
    <PageHeading eyebrow="CONCERT MANAGEMENT" title="공연 조회" description="관리 중인 서비스의 공연별 예매 시도 현황을 확인합니다." action={<button className="secondary-button" onClick={onReload} disabled={!hasServiceKey || loading}><Activity size={15} /> 새로고침</button>} />
    <div className="search-row"><div className="search-box"><Search size={17} /><input value={search} onChange={e => setSearch(e.target.value)} placeholder="공연 ID로 검색" /></div><span className="result-count">{rows.length}개 공연</span></div>
    {!hasServiceKey && <div className="empty-state"><div className="empty-icon"><KeyRound size={22} /></div><h3>서비스 API Key가 필요합니다</h3><p>공연 목록 API가 아직 제공되지 않아, 예매 시도 조회 결과에서 공연 ID를 수집합니다.<br />상단의 ‘조회용 서비스 API Key’를 입력해 주세요.</p></div>}
    {hasServiceKey && loading && <LoadingState label="공연별 예매 시도 데이터를 불러오는 중입니다." />}
    {hasServiceKey && !loading && rows.length === 0 && <div className="empty-state"><div className="empty-icon"><CalendarDays size={22} /></div><h3>조회된 공연이 없습니다</h3><p>최근 예매 시도 100건에서 공연 ID를 찾지 못했습니다.</p></div>}
    {hasServiceKey && !loading && rows.length > 0 && <div className="concert-list">{rows.map((row) => <button className="concert-card" key={row.concertId} onClick={() => onSelect(row.concertId)}><div className="concert-date"><CalendarDays size={17} /><span>{shortDateUtc(row.latestAt)}</span></div><div className="concert-main"><div className="concert-title">{row.concertId}</div><div className="concert-meta"><span>최근 조회 데이터 내 시도 {number(row.visibleAttempts)}건</span><span>최근 시도 {dateTimeUtc(row.latestAt)}</span></div></div><div className="concert-status"><span className="status-pill neutral">공연 정보 미제공</span><ChevronRight size={18} /></div></button>)}</div>}
    <div className="footnote"><CircleHelp size={14} />전체 예매 시도 수 {number(total)}건은 서비스 전체 기준입니다. 목록은 최근 조회된 최대 100건에 포함된 공연 ID만 표시합니다. 공연명·일자·예매 상태는 백엔드 API 미제공 항목입니다.</div>
  </>
}

function StatCard({ label, value, caption, icon, muted = false }: { label: string; value: string; caption: string; icon: ReactNode; muted?: boolean }) {
  return <div className={`stat-card ${muted ? 'stat-muted' : ''}`}><div className="stat-top"><span>{label}</span><span className="stat-icon">{icon}</span></div><strong>{value}</strong><small>{caption}</small></div>
}

function ConcertDashboard({ concertId, attempts, total, chartData, loading, onBack, onParticipants }: {
  concertId: string; attempts: BookingAttempt[]; total: number; chartData: { label: string; attempts: number }[]; loading: boolean; onBack: () => void; onParticipants: () => void
}) {
  return <>
    <button className="back-link" onClick={onBack}><ArrowLeft size={16} /> 공연 목록으로</button>
    <PageHeading eyebrow="CONCERT OVERVIEW" title={concertId} description="선택한 공연의 예매 시도 및 이상 탐지 현황입니다." action={<button className="primary-button" onClick={onParticipants}><Users size={16} /> 예매자 목록 조회</button>} />
    <div className="stats-grid four">
      <StatCard label="전체 예매 시도" value={loading ? '…' : number(total)} caption="선택 공연의 고유 예매 시도" icon={<Ticket size={17} />} />
      <StatCard label="이상 탐지 건수" value="—" caption="공연별 이상 탐지 집계 API 미제공" icon={<AlertTriangle size={17} />} muted />
      <StatCard label="제재 적용 건수" value="—" caption="공연별 제재 집계 API 미제공" icon={<ShieldCheck size={17} />} muted />
      <StatCard label="평균 Credit" value="—" caption="공연별 평균 Credit API 미제공" icon={<Activity size={17} />} muted />
    </div>
    <section className="panel chart-panel"><div className="panel-heading"><div><h2>시간대별 예매 시도 추이</h2><p>최근 조회된 예매 시도 시작 시각을 10초 단위로 묶었습니다.</p></div><span className="chart-legend"><i />예매 시도</span></div>
      {loading ? <LoadingState label="예매 시도 데이터를 불러오는 중입니다." /> : chartData.length ? <div className="chart-wrap"><ResponsiveContainer width="100%" height="100%"><LineChart data={chartData} margin={{ top: 10, right: 18, left: -18, bottom: 0 }}><CartesianGrid strokeDasharray="3 3" stroke="#edf0f4" vertical={false} /><XAxis dataKey="label" tick={{ fill: '#87909f', fontSize: 11 }} axisLine={false} tickLine={false} minTickGap={24} /><YAxis allowDecimals={false} tick={{ fill: '#87909f', fontSize: 11 }} axisLine={false} tickLine={false} /><Tooltip contentStyle={{ border: '1px solid #e8ebf0', borderRadius: 10, boxShadow: '0 8px 24px #1c27330d' }} /><Line type="monotone" dataKey="attempts" name="예매 시도" stroke="#4778e8" strokeWidth={2.5} dot={{ r: 3, fill: '#4778e8', strokeWidth: 0 }} activeDot={{ r: 5 }} /></LineChart></ResponsiveContainer></div> : <div className="chart-empty"><BarChart3 size={24} /><span>표시할 예매 시도 데이터가 없습니다.</span></div>}
      <div className="chart-footnote"><AlertTriangle size={13} />최대 100건의 조회 결과를 사용한 분포이며 전체 기간의 시계열 통계가 아닙니다. 이상 탐지 시계열 API는 아직 제공되지 않습니다.</div>
    </section>
    <div className="section-row"><div><h2>최근 예매 진행 현황</h2><p>조회된 예매 시도별 가장 최근 단계입니다.</p></div><span className="count-chip">{number(attempts.length)}건 표시</span></div>
    <div className="table-panel"><table><thead><tr><th>사용자 ID</th><th>예매 시작 시각</th><th>최근 진행 단계</th><th>상태</th><th>시퀀스</th></tr></thead><tbody>{attempts.slice(0, 8).map(item => <tr key={item.attemptId}><td className="mono">{item.userId}</td><td>{dateTimeUtc(item.startedAt)}</td><td><span className="stage-pill">{item.stage}</span></td><td>{item.status}</td><td>{item.sequence}</td></tr>)}{!attempts.length && <tr><td colSpan={5} className="empty-cell">조회된 예매 시도가 없습니다.</td></tr>}</tbody></table></div>
  </>
}

function ConcertParticipants({ concertId, participants, onBack, onOpenUser }: { concertId: string; participants: BookingAttempt[]; onBack: () => void; onOpenUser: (id: string) => void }) {
  return <>
    <button className="back-link" onClick={onBack}><ArrowLeft size={16} /> 공연 상세로</button>
    <PageHeading eyebrow="CONCERT PARTICIPANTS" title="공연별 사용자 조회" description={`${concertId} 공연의 최근 예매 시도에서 확인된 사용자입니다.`} />
    <div className="inline-warning"><AlertTriangle size={16} /><span>현재 백엔드에는 공연 ID로 사용자 목록을 필터링하는 관리자 API가 없습니다. 아래는 최근 예매 시도 100건에서 확인된 사용자이며, Credit·등급·이상 점수는 제공되지 않습니다.</span></div>
    <div className="table-panel"><table><thead><tr><th>사용자 ID</th><th>최근 예매 시작</th><th>진행 단계</th><th>상태</th><th>상세</th></tr></thead><tbody>{participants.map(item => <tr key={item.userId}><td className="mono">{item.userId}</td><td>{dateTimeUtc(item.startedAt)}</td><td><span className="stage-pill">{item.stage}</span></td><td>{item.status}</td><td><button className="table-action" onClick={() => onOpenUser(item.userId)}>상세 조회 <ChevronRight size={14} /></button></td></tr>)}{!participants.length && <tr><td colSpan={5} className="empty-cell">예매 참여 사용자가 없습니다.</td></tr>}</tbody></table></div>
  </>
}

function UserList({ tiers, users, loading, total, page, search, setSearch, tier, setTier, minCredit, setMinCredit, maxCredit, setMaxCredit, sort, setSort, onSearch, onPage, onOpenUser }: {
  tiers: string[]; users: AdminUser[]; loading: boolean; total: number; page: number; search: string; setSearch: (v: string) => void; tier: string; setTier: (v: string) => void; minCredit: string; setMinCredit: (v: string) => void; maxCredit: string; setMaxCredit: (v: string) => void; sort: 'asc' | 'desc'; setSort: (v: 'asc' | 'desc') => void; onSearch: () => void; onPage: (page: number) => void; onOpenUser: (id: string) => void
}) {
  return <>
    <PageHeading eyebrow="USER MANAGEMENT" title="사용자 조회" description="서비스에 등록된 사용자의 등급과 Credit 정보를 조회합니다." />
    <div className="filter-panel"><div className="filter-title"><Filter size={16} />검색 및 필터</div><div className="filter-grid"><label className="filter-control search-control"><span>사용자 ID</span><div className="search-box"><Search size={15} /><input value={search} onChange={e => setSearch(e.target.value)} onKeyDown={e => e.key === 'Enter' && onSearch()} placeholder="사용자 ID 정확히 입력" /></div></label><label className="filter-control"><span>등급</span><select value={tier} onChange={e => setTier(e.target.value)}><option value="">전체 등급</option>{tiers.map(value => <option key={value} value={value}>{value}</option>)}</select></label><label className="filter-control"><span>Credit 최소</span><input type="number" min="0" max="100" value={minCredit} onChange={e => setMinCredit(e.target.value)} placeholder="0" /></label><label className="filter-control"><span>Credit 최대</span><input type="number" min="0" max="100" value={maxCredit} onChange={e => setMaxCredit(e.target.value)} placeholder="100" /></label><button className="primary-button filter-submit" onClick={onSearch} disabled={loading}><Search size={15} />조회</button></div><div className="filter-note"><CircleHelp size={13} />사용자 ID는 내부 ID 완전 일치 검색입니다. 이상 점수 검색·정렬은 현재 목록 API에서 지원하지 않습니다.</div></div>
    <div className="section-row"><div><h2>사용자 목록</h2><p>총 {number(total)}명의 사용자</p></div><button className="sort-button" onClick={() => { setSort(sort === 'desc' ? 'asc' : 'desc'); setTimeout(onSearch, 0) }}><ArrowDownUp size={15} />Credit {sort === 'desc' ? '내림차순' : '오름차순'} <ChevronDown size={14} /></button></div>
    <div className="table-panel"><table><thead><tr><th>사용자 ID</th><th>등급</th><th>Credit</th><th>이상 점수</th><th>상태</th><th /></tr></thead><tbody>{loading ? <tr><td colSpan={6} className="empty-cell"><LoaderCircle className="spin" size={18} /> 불러오는 중입니다.</td></tr> : users.map(user => <tr key={user.userId}><td><span className="user-id-cell"><span className="avatar">{user.externalUserId.slice(0, 1).toUpperCase()}</span><span><b>{user.externalUserId}</b><small>{user.userId}</small></span></span></td><td><TierBadge tier={user.tier} /></td><td><b className="credit-value">{number(user.creditScore)}</b></td><td><span className="unavailable">미제공</span></td><td><span className={`status-pill ${user.accountStatus === 'ACTIVE' ? 'success' : 'neutral'}`}>{user.accountStatus}</span></td><td><button className="table-action" onClick={() => onOpenUser(user.userId)}>조회 <ChevronRight size={14} /></button></td></tr>)}{!loading && users.length === 0 && <tr><td colSpan={6} className="empty-cell">조건에 맞는 사용자가 없습니다.</td></tr>}</tbody></table></div>
    <div className="pagination"><span>{number(total)}명 중 {total ? page * 10 + 1 : 0}–{Math.min((page + 1) * 10, total)}명</span><div><button disabled={page <= 0 || loading} onClick={() => onPage(page - 1)}><ArrowLeft size={15} />이전</button><span className="page-number">{page + 1}</span><button disabled={(page + 1) * 10 >= total || loading} onClick={() => onPage(page + 1)}>다음<ArrowRight size={15} /></button></div></div>
  </>
}

function TierBadge({ tier }: { tier: string }) {
  const className = tier.toLowerCase()
  return <span className={`tier-badge ${className}`}>{tier}</span>
}

function UserDetailPage({ userId, detail, events, loading, onBack }: { userId: string; detail: UserDetail | null; events: UserEvent[]; loading: boolean; onBack: () => void }) {
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
    <button className="back-link" onClick={onBack}><ArrowLeft size={16} /> 사용자 목록으로</button>
    <PageHeading eyebrow="USER DETAIL" title={detail?.externalUserId || userId} description="사용자의 최신 위험 평가와 행동 이벤트 이력을 확인합니다." />
    {loading && <LoadingState label="사용자 상세 정보를 불러오는 중입니다." />}
    {!loading && detail && <>
      <div className="user-summary-card"><div className="user-summary-main"><div className="avatar large">{detail.externalUserId.slice(0, 1).toUpperCase()}</div><div><h2>{detail.externalUserId}</h2><p className="mono">{detail.userId}</p><span className={`status-pill ${detail.accountStatus === 'ACTIVE' ? 'success' : 'neutral'}`}>{detail.accountStatus}</span></div></div><div className="user-summary-metrics"><div><span>이상 점수</span><b className={detail.anomalyScore != null && detail.anomalyScore >= 0.7 ? 'risk-high' : ''}>{detail.anomalyScore == null ? '—' : detail.anomalyScore.toFixed(2)}</b></div><div><span>Credit</span><b>{number(detail.creditScore)}</b></div><div><span>등급</span><b><TierBadge tier={detail.tier} /></b></div><div><span>최근 평가</span><b className="small-metric">{dateTime(detail.lastAssessedAt)}</b></div></div></div>
      <section className="panel feature-panel"><div className="panel-heading"><div><h2>AI Feature</h2><p>최근 30일 행동 이벤트를 기준으로 계산하는 Feature 항목</p></div><ApiMissing>Feature 조회 API 미제공</ApiMissing></div><div className="feature-table"><div className="feature-row feature-head"><span>Feature</span><span>설명</span><span>계산값</span></div>{featureRows.map(([key, label, unit]) => <div className="feature-row" key={key}><code>{key}</code><span>{label}</span><span className="unavailable">미제공 {unit}</span></div>)}</div><div className="feature-note">백엔드에 Feature 계산 서비스와 DTO는 존재하지만, 사용자별 계산 결과를 반환하는 관리자 조회 API는 아직 없습니다. 실제 계산값을 임의로 생성하지 않습니다.</div></section>
      <section className="panel history-panel"><div className="panel-heading"><div><h2>예매 및 행동 이력</h2><p>최근 수집된 사용자 이벤트 (최대 100건)</p></div><span className="count-chip">{events.length}건</span></div><div className="table-panel inner-table"><table><thead><tr><th>이벤트</th><th>발생 일시</th><th>예약 ID</th><th>티켓 ID</th><th>추가 정보</th></tr></thead><tbody>{events.map(event => <tr key={event.eventId}><td><EventBadge type={event.eventType} /></td><td>{dateTime(event.occurredAt)}</td><td className="mono">{event.bookingId || '—'}</td><td className="mono">{event.ticketId || '—'}</td><td>{event.metadata ? Object.entries(event.metadata).slice(0, 2).map(([k, v]) => `${k}: ${String(v)}`).join(' · ') : '—'}</td></tr>)}{!events.length && <tr><td colSpan={5} className="empty-cell">조회된 이벤트 이력이 없습니다.</td></tr>}</tbody></table></div></section>
    </>}
    {!loading && !detail && <div className="empty-state"><div className="empty-icon"><UserRound size={22} /></div><h3>사용자 상세 정보를 불러오지 못했습니다</h3><p>서비스 API Key와 사용자 ID의 서비스 소속을 확인해 주세요.</p></div>}
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
