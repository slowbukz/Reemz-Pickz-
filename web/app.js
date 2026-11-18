import {
  LEAGUES,
  OddsMath,
  PlayerPropSimEngine,
  QuantCorrelationEngine,
  SIM_SCENARIOS
} from './quant.js';
import {
  fetchAllMarketData,
  fetchGameResearchCandidates,
  fetchMatchupInjuries,
  fetchNbaIntel,
  fetchPlayerResearchProfile,
  formatLine,
  refreshLiveScoresOnly
} from './api.js';

const STORAGE_KEY = 'reemz_pickz_web_v1';

function loadPersisted() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function savePersisted(patch) {
  try {
    const prev = loadPersisted();
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ ...prev, ...patch }));
  } catch {}
}

const persisted = loadPersisted();

const state = {
  tab: 'parlays',
  isLoading: true,
  error: null,
  autoRefresh: true,
  lastUpdatedMillis: 0,
  events: [],
  legs: [],
  props: [],
  parlays: [],
  sandboxLegs: [],
  savedParlayIds: new Set(persisted.savedParlayIds || []),
  savedPropIds: new Set(persisted.savedPropIds || []),
  savedParlaysList: persisted.savedParlaysList || [],
  savedPropsList: persisted.savedPropsList || [],
  prefs: {
    oddsFormat: persisted.prefs?.oddsFormat || 'AMERICAN',
    devigMethod: persisted.prefs?.devigMethod || 'POWER',
    minProb: persisted.prefs?.minProb ?? 0.36,
    minRho: persisted.prefs?.minRho ?? 0.14,
    parlaySize: persisted.prefs?.parlaySize || 'THREE_LEGS',
    darkTheme: persisted.prefs?.darkTheme ?? true,
    enabledLeagues: persisted.prefs?.enabledLeagues || ['NBA', 'NFL', 'NHL', 'MLB', 'EPL', 'NCAAB']
  },
  propsMode: 'PROPS', // PROPS | RESEARCH
  propsLeague: 'ALL',
  propsDirection: 'ALL',
  slateSubTab: 'SLATE', // SLATE | NBA_NEWS
  slateLeague: 'ALL',
  evLeague: 'ALL',
  evSort: 'EV', // EV | PROB | KELLY
  nbaBucket: 'WONT_PLAY',
  nbaNewsFilter: 'ALL',
  nbaIntel: null,
  nbaLoading: false,
  research: {
    league: 'ALL',
    selectedGame: null,
    candidates: [],
    selectedCandidate: null,
    profile: null,
    injuries: [],
    selectedStat: null,
    selectedLine: 0,
    selectedBarIdx: 0,
    scenarioKey: 'BASELINE',
    simRunId: 0,
    simResult: null,
    isLoading: false
  }
};

document.documentElement.setAttribute('data-theme', state.prefs.darkTheme ? 'dark' : 'light');

const mainEl = document.getElementById('main-content');
const feedLineEl = document.getElementById('feed-status-line');
const refreshBtn = document.getElementById('refresh-all-btn');
const livePillBtn = document.getElementById('live-pill-btn');
const livePillLabel = document.getElementById('live-pill-label');
const modalOverlay = document.getElementById('modal-overlay');
const modalTitle = document.getElementById('modal-title');
const modalBody = document.getElementById('modal-body');
const sandboxBar = document.getElementById('sandbox-bar');
const sandboxCount = document.getElementById('sandbox-count');
const sandboxSummary = document.getElementById('sandbox-summary');
const toastEl = document.getElementById('toast');

function esc(str) {
  return String(str ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function showToast(msg) {
  toastEl.textContent = msg;
  toastEl.classList.remove('hidden');
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => toastEl.classList.add('hidden'), 2400);
}

function recomputeParlays() {
  state.parlays = QuantCorrelationEngine.buildTopCorrelatedParlays(
    state.legs,
    state.prefs,
    state.savedParlayIds
  );
}

function updateHeaderStatus() {
  const liveCount = state.events.filter((e) => e.state === 'LIVE').length;
  const upCount = state.events.filter((e) => e.state === 'UPCOMING').length;
  const timeStr = state.lastUpdatedMillis
    ? new Date(state.lastUpdatedMillis).toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', second: '2-digit' })
    : 'Syncing';
  feedLineEl.textContent = state.isLoading
    ? 'Scanning live ESPN markets & 25K sims…'
    : `${liveCount} Live • ${upCount} Upcoming • ${timeStr}`;
  refreshBtn.classList.toggle('spinning', state.isLoading);
  livePillBtn.classList.toggle('paused', !state.autoRefresh);
  livePillLabel.textContent = state.autoRefresh ? 'LIVE 12s' : 'PAUSED';
}

function updateSandboxBar() {
  if (!state.sandboxLegs.length) {
    sandboxBar.classList.add('hidden');
    return;
  }
  sandboxBar.classList.remove('hidden');
  sandboxCount.textContent = `${state.sandboxLegs.length} Leg${state.sandboxLegs.length > 1 ? 's' : ''} in Sandbox`;
  const sim = simulateSandboxParlay();
  sandboxSummary.textContent = `${OddsMath.formatOdds(sim.marketParlayAmericanOdds, state.prefs.oddsFormat)} • Hit ${(sim.correlatedProb * 100).toFixed(1)}% • +EV ${(sim.correlatedEvPercent * 100).toFixed(1)}%`;
}

function simulateSandboxParlay() {
  const legs = state.sandboxLegs;
  const links = [];
  const rhos = {};
  for (let i = 0; i < legs.length; i++) {
    for (let j = i + 1; j < legs.length; j++) {
      const ev = QuantCorrelationEngine.evaluatePairCorrelation(legs[i], legs[j]);
      if (ev) {
        rhos[`${i}_${j}`] = ev.rho;
        links.push({ fromLabel: legs[i].selectionLabel, toLabel: legs[j].selectionLabel, rho: ev.rho, thesis: ev.thesis });
      }
    }
  }
  return QuantCorrelationEngine.assembleParlay(legs, links, rhos, state.savedParlayIds);
}

function openModal(title, html) {
  modalTitle.textContent = title;
  modalBody.innerHTML = html;
  modalOverlay.classList.remove('hidden');
}

function closeModal() {
  modalOverlay.classList.add('hidden');
}

document.getElementById('modal-close-btn').addEventListener('click', closeModal);
modalOverlay.addEventListener('click', (e) => {
  if (e.target === modalOverlay) closeModal();
});

function toggleSaveParlay(parlay) {
  if (state.savedParlayIds.has(parlay.id)) {
    state.savedParlayIds.delete(parlay.id);
    state.savedParlaysList = state.savedParlaysList.filter((p) => p.id !== parlay.id);
    showToast('Removed parlay from saved');
  } else {
    state.savedParlayIds.add(parlay.id);
    state.savedParlaysList = [parlay, ...state.savedParlaysList.filter((p) => p.id !== parlay.id)].slice(0, 25);
    showToast('Saved parlay to Quant Lab');
  }
  savePersisted({
    savedParlayIds: [...state.savedParlayIds],
    savedParlaysList: state.savedParlaysList
  });
  recomputeParlays();
  render();
}

function toggleSaveProp(prop) {
  if (state.savedPropIds.has(prop.id)) {
    state.savedPropIds.delete(prop.id);
    state.savedPropsList = state.savedPropsList.filter((p) => p.id !== prop.id);
    showToast('Removed prop from saved');
  } else {
    state.savedPropIds.add(prop.id);
    state.savedPropsList = [prop, ...state.savedPropsList.filter((p) => p.id !== prop.id)].slice(0, 40);
    showToast('Saved prop pick');
  }
  savePersisted({
    savedPropIds: [...state.savedPropIds],
    savedPropsList: state.savedPropsList
  });
  render();
}

function toggleLegInSandbox(leg) {
  const exists = state.sandboxLegs.some((l) => l.id === leg.id);
  if (exists) {
    state.sandboxLegs = state.sandboxLegs.filter((l) => l.id !== leg.id);
  } else if (state.sandboxLegs.length >= 5) {
    showToast('Max 5 legs in Parlay Sandbox');
    return;
  } else {
    state.sandboxLegs = [...state.sandboxLegs, leg];
  }
  updateSandboxBar();
  render();
}

function propToMarketLeg(prop) {
  const isMore = prop.recommendedSide === 'MORE';
  const odds = isMore ? prop.moreAmericanOdds : prop.lessAmericanOdds;
  const prob = isMore ? prop.moreProb : prop.lessProb;
  const ev = isMore ? prop.moreEvPercent : prop.lessEvPercent;
  return {
    id: prop.id,
    eventId: prop.eventId,
    league: prop.league,
    matchupShort: prop.matchupShort,
    homeTeamName: prop.teamAbbr,
    awayTeamName: prop.teamAbbr,
    homeLogoUrl: prop.headshotUrl,
    awayLogoUrl: prop.headshotUrl,
    startTimeDisplay: prop.startTimeDisplay,
    marketType: isMore ? 'PLAYER_PROP_MORE' : 'PLAYER_PROP_LESS',
    selectionLabel: `${prop.playerName} ${prop.recommendedSide} ${formatLine(prop.lineValue)} ${prop.statLabel}`,
    sideTag: prop.sideTag,
    lineValue: prop.lineValue,
    offeredAmericanOdds: odds,
    offeredDecimalOdds: OddsMath.americanToDecimal(odds),
    marketImpliedProb: OddsMath.americanToImpliedProb(odds),
    fairTrueProb: prob,
    evPercent: ev,
    sharpBookLabel: 'Pace & Stat Projection',
    isFavoriteSide: prop.isTeamFavorite,
    expectedTotal: prop.expectedTotal,
    expectedHomeMargin: prop.expectedHomeMargin
  };
}

function openParlayDetailModal(parlay) {
  const fmt = state.prefs.oddsFormat;
  const html = `
    <div class="card">
      <div class="card-header">
        <span class="tag tag-emerald">${esc(parlay.sharpGrade)}</span>
        <span class="tag tag-cyan">${parlay.simConsensusCount.toLocaleString()} SIMS • ${Math.round(parlay.simAgreementScore * 100)}% AGREE</span>
      </div>
      <div style="font-size:13px;color:var(--text-sec);margin-bottom:8px;">${esc(parlay.primaryThesis)}</div>
      <div class="parlay-stats-row">
        <div><div class="stat-mini-val" style="color:var(--emerald)">${(parlay.correlatedProb * 100).toFixed(1)}%</div><div class="stat-mini-lbl">Corr Hit</div></div>
        <div><div class="stat-mini-val">${OddsMath.formatOdds(parlay.marketParlayAmericanOdds, fmt)}</div><div class="stat-mini-lbl">Market</div></div>
        <div><div class="stat-mini-val" style="color:var(--cyan)">${OddsMath.formatPercent(parlay.correlatedEvPercent, true)}</div><div class="stat-mini-lbl">Corr +EV</div></div>
        <div><div class="stat-mini-val">${parlay.quarterKellyPercent.toFixed(1)}%</div><div class="stat-mini-lbl">¼ Kelly</div></div>
      </div>
      <div class="kv-grid">
        <div class="kv-cell"><b>${(parlay.gaussianCopulaProb * 100).toFixed(1)}%</b><span>Gaussian 10K</span></div>
        <div class="kv-cell"><b>${(parlay.studentTCopulaProb * 100).toFixed(1)}%</b><span>Student-t 10K</span></div>
        <div class="kv-cell"><b>${(parlay.paceStressSimProb * 100).toFixed(1)}%</b><span>Pace Stress 5K</span></div>
      </div>
    </div>

    <div class="section-title">Slip Legs (Tap Player Prop for Deep Rationale)</div>
    ${parlay.legs.map((leg, i) => `
      <div class="leg-row" data-modal-leg="${i}">
        <div class="leg-left">
          ${leg.homeLogoUrl ? `<img class="avatar-img" src="${esc(leg.homeLogoUrl)}" alt="" onerror="this.style.display='none'">` : ''}
          <div>
            <div class="leg-title">${esc(leg.selectionLabel)}</div>
            <div class="leg-sub">${esc(leg.league)} • ${esc(leg.matchupShort)} • ${esc(leg.sharpBookLabel)}</div>
          </div>
        </div>
        <div class="leg-odds">
          <div class="odds-num">${OddsMath.formatOdds(leg.offeredAmericanOdds, fmt)}</div>
          <div class="prob-num">${(leg.fairTrueProb * 100).toFixed(1)}% fair</div>
        </div>
      </div>
    `).join('')}

    ${parlay.correlationLinks.length ? `
      <div class="section-title">Pairwise Covariance Links</div>
      ${parlay.correlationLinks.map((lk) => `
        <div class="card" style="padding:10px;">
          <div style="display:flex;justify-content:space-between;font-size:12px;font-weight:700;">
            <span>${esc(lk.fromLabel)} ↔ ${esc(lk.toLabel)}</span>
            <span style="color:var(--emerald)">ρ = +${lk.rho.toFixed(2)}</span>
          </div>
          <div style="font-size:11px;color:var(--text-sec);margin-top:4px;">${esc(lk.thesis)}</div>
        </div>
      `).join('')}
    ` : ''}

    <div class="btn-row">
      <button class="btn btn-emerald" id="modal-save-parlay">${state.savedParlayIds.has(parlay.id) ? '★ Saved' : '☆ Save Parlay'}</button>
      <button class="btn" id="modal-copy-parlay">Copy Slip Text</button>
    </div>
  `;
  openModal(`#${parlay.rank || 'Custom'} • ${parlay.legs.length}-Pick Correlated Slip`, html);

  document.getElementById('modal-save-parlay')?.addEventListener('click', () => {
    toggleSaveParlay(parlay);
    openParlayDetailModal(parlay);
  });
  document.getElementById('modal-copy-parlay')?.addEventListener('click', async () => {
    const txt = `REEMZ PICKZ (${parlay.legs.length}-Pick | ${OddsMath.formatOdds(parlay.marketParlayAmericanOdds, fmt)} | +EV ${(parlay.correlatedEvPercent * 100).toFixed(1)}%)\n` +
      parlay.legs.map((l, idx) => `${idx + 1}. ${l.selectionLabel} (${l.matchupShort})`).join('\n');
    try { await navigator.clipboard.writeText(txt); showToast('Copied parlay slip'); } catch { showToast('Ready to share'); }
  });
  modalBody.querySelectorAll('[data-modal-leg]').forEach((el) => {
    el.addEventListener('click', () => {
      const leg = parlay.legs[Number(el.dataset.modalLeg)];
      const matchingProp = state.props.find((p) => p.id === leg.id);
      if (matchingProp) openPropDetailModal(matchingProp);
      else {
        const ev = state.events.find((e) => e.id === leg.eventId);
        if (ev) openGameDetailModal(ev);
      }
    });
  });
}

async function openPropDetailModal(prop) {
  const ev = state.events.find((e) => e.id === prop.eventId);
  const isMore = prop.recommendedSide === 'MORE';
  const recOdds = isMore ? prop.moreAmericanOdds : prop.lessAmericanOdds;
  const recProb = isMore ? prop.moreProb : prop.lessProb;
  const recEv = isMore ? prop.moreEvPercent : prop.lessEvPercent;
  const inSandbox = state.sandboxLegs.some((l) => l.id === prop.id);

  openModal(`${prop.playerName} • ${prop.statLabel}`, `
    <div class="card">
      <div style="display:flex;align-items:center;gap:12px;">
        <img class="avatar-img" style="width:52px;height:52px;" src="${esc(prop.headshotUrl || '')}" alt="" onerror="this.style.display='none'">
        <div style="flex:1;">
          <div style="font-size:16px;font-weight:800;">${esc(prop.playerName)} <span class="tag tag-cyan">${esc(prop.teamAbbr)} ${esc(prop.position)}</span></div>
          <div style="font-size:12px;color:var(--text-sec);margin-top:2px;">${esc(prop.league)} • ${esc(prop.matchupShort)} • ${esc(prop.startTimeDisplay)}</div>
        </div>
      </div>
      <div class="parlay-stats-row" style="margin-top:12px;">
        <div><div class="stat-mini-val" style="color:var(--emerald)">${prop.recommendedSide} ${formatLine(prop.lineValue)}</div><div class="stat-mini-lbl">Pick</div></div>
        <div><div class="stat-mini-val">${prop.projectedValue.toFixed(1)}</div><div class="stat-mini-lbl">Model Proj</div></div>
        <div><div class="stat-mini-val" style="color:var(--cyan)">${(recProb * 100).toFixed(1)}%</div><div class="stat-mini-lbl">Hit Prob</div></div>
        <div><div class="stat-mini-val">${OddsMath.formatPercent(recEv, true)}</div><div class="stat-mini-lbl">+EV (${OddsMath.formatOdds(recOdds, state.prefs.oddsFormat)})</div></div>
      </div>
      <div style="font-size:12px;color:var(--text);background:var(--surface);padding:10px;border-radius:10px;border:1px solid var(--border);">
        <strong>Why This Pick:</strong> ${esc(prop.confidenceThesis)}<br/>
        <span style="color:var(--text-sec);">Vegas Game Environment: Total ${prop.expectedTotal.toFixed(1)} • Implied Home Margin ${prop.expectedHomeMargin >= 0 ? '+' : ''}${prop.expectedHomeMargin.toFixed(1)}</span>
      </div>
    </div>
    <div id="prop-modal-dynamic" class="card"><span style="font-size:12px;color:var(--text-sec);">Loading ESPN season gamelog &amp; matchup injuries…</span></div>
    <div class="btn-row">
      <button class="btn btn-emerald" id="prop-modal-research">Open Full Simulator &amp; Alt Lines</button>
      <button class="btn" id="prop-modal-sandbox">${inSandbox ? '✓ In Sandbox' : '+ Add to Sandbox'}</button>
      <button class="btn" id="prop-modal-save">${state.savedPropIds.has(prop.id) ? '★ Saved' : '☆ Save'}</button>
    </div>
  `);

  document.getElementById('prop-modal-research')?.addEventListener('click', () => {
    closeModal();
    openPlayerInResearch(prop);
  });
  document.getElementById('prop-modal-sandbox')?.addEventListener('click', () => {
    toggleLegInSandbox(propToMarketLeg(prop));
    openPropDetailModal(prop);
  });
  document.getElementById('prop-modal-save')?.addEventListener('click', () => {
    toggleSaveProp(prop);
    openPropDetailModal(prop);
  });

  const [profile, injuries] = await Promise.all([
    fetchPlayerResearchProfile({
      playerId: prop.playerId,
      playerName: prop.playerName,
      teamAbbr: prop.teamAbbr,
      position: prop.position,
      headshotUrl: prop.headshotUrl,
      eventId: prop.eventId,
      league: prop.league,
      matchupShort: prop.matchupShort
    }, state.props),
    ev ? fetchMatchupInjuries(ev) : Promise.resolve([])
  ]);

  const dyn = document.getElementById('prop-modal-dynamic');
  if (!dyn) return;
  const recentVals = profile.gameLogs.map((g) => g.propValues[prop.statLabel]).filter((v) => v != null);
  const hits = recentVals.filter((v) => isMore ? v > prop.lineValue : v < prop.lineValue).length;
  const avg = recentVals.length ? (recentVals.reduce((a, b) => a + b, 0) / recentVals.length).toFixed(1) : 'N/A';
  const teamInj = injuries.filter((i) => i.teamAbbr === prop.teamAbbr).slice(0, 4);

  dyn.innerHTML = `
    <div style="font-size:13px;font-weight:800;margin-bottom:6px;">Recent Form &amp; Matchup Context</div>
    <div style="font-size:12px;color:var(--text-sec);">
      • <strong>ESPN L${recentVals.length || 0} Avg:</strong> ${avg} ${esc(prop.statLabel)} (${recentVals.length ? `${hits}/${recentVals.length} hit rate vs ${formatLine(prop.lineValue)}` : 'No completed logs'})<br/>
      ${teamInj.length ? `• <strong>${esc(prop.teamAbbr)} Injury Report:</strong> ${teamInj.map((i) => `${esc(i.playerName)} (${esc(i.status)})`).join(', ')}` : '• No active team injury absences reported on ESPN.'}
    </div>
  `;
}

async function openGameDetailModal(ev) {
  const gameProps = state.props.filter((p) => p.eventId === ev.id);
  const gameLegs = state.legs.filter((l) => l.eventId === ev.id);
  openModal(`${ev.shortName} • ${ev.league}`, `
    <div class="card">
      <div class="card-header">
        <span class="tag ${ev.state === 'LIVE' ? 'tag-emerald' : 'tag-cyan'}">${esc(ev.state)} • ${esc(ev.statusDetail)}</span>
        <span style="font-size:11px;color:var(--text-sec);">${esc(ev.dataSourceLabel)}</span>
      </div>
      <div class="team-line">
        <div class="team-info">
          ${ev.awayTeam.logoUrl ? `<img class="team-logo" src="${esc(ev.awayTeam.logoUrl)}" alt="">` : ''}
          <div><div class="team-name">${esc(ev.awayTeam.name)}</div><div class="team-rec">${esc(ev.awayTeam.record)}</div></div>
        </div>
        <div class="team-score">${esc(ev.awayTeam.score ?? '-')}</div>
      </div>
      <div class="team-line">
        <div class="team-info">
          ${ev.homeTeam.logoUrl ? `<img class="team-logo" src="${esc(ev.homeTeam.logoUrl)}" alt="">` : ''}
          <div><div class="team-name">${esc(ev.homeTeam.name)}</div><div class="team-rec">${esc(ev.homeTeam.record)}</div></div>
        </div>
        <div class="team-score">${esc(ev.homeTeam.score ?? '-')}</div>
      </div>
      ${ev.marketSnapshot ? `
        <div class="kv-grid">
          <div class="kv-cell"><b>${ev.marketSnapshot.spreadLine ?? '-'}</b><span>Home Spread</span></div>
          <div class="kv-cell"><b>${ev.marketSnapshot.totalLine ?? '-'}</b><span>O/U Total</span></div>
          <div class="kv-cell"><b>${ev.predictorHomeWinProb != null ? `${Math.round(ev.predictorHomeWinProb * 100)}%` : '-'}</b><span>Home Win %</span></div>
        </div>
      ` : ''}
    </div>
    <div class="btn-row" style="margin-bottom:10px;">
      <button class="btn btn-cyan" id="game-modal-research-btn">Research Players in This Game</button>
    </div>
    ${gameLegs.length ? `<div class="section-title">Market Lines (+ Add to Sandbox)</div>` : ''}
    ${gameLegs.slice(0, 8).map((leg, i) => `
      <div class="leg-row" data-game-leg="${i}">
        <div>
          <div class="leg-title">${esc(leg.selectionLabel)}</div>
          <div class="leg-sub">Fair ${(leg.fairTrueProb * 100).toFixed(1)}% • +EV ${OddsMath.formatPercent(leg.evPercent, true)}</div>
        </div>
        <div class="leg-odds">
          <div class="odds-num">${OddsMath.formatOdds(leg.offeredAmericanOdds, state.prefs.oddsFormat)}</div>
          <div class="prob-num">${state.sandboxLegs.some((s) => s.id === leg.id) ? 'Added' : '+ Sandbox'}</div>
        </div>
      </div>
    `).join('')}
    ${gameProps.length ? `<div class="section-title">Featured Player Props</div>` : ''}
    ${gameProps.slice(0, 6).map((p, i) => `
      <div class="leg-row" data-game-prop="${i}">
        <div>
          <div class="leg-title">${esc(p.playerName)} • ${p.recommendedSide} ${formatLine(p.lineValue)} ${esc(p.statLabel)}</div>
          <div class="leg-sub">Proj ${p.projectedValue.toFixed(1)} • ${esc(p.confidenceThesis)}</div>
        </div>
        <div class="prob-num">${(Math.max(p.moreProb, p.lessProb) * 100).toFixed(0)}%</div>
      </div>
    `).join('')}
  `);

  document.getElementById('game-modal-research-btn')?.addEventListener('click', () => {
    closeModal();
    state.tab = 'props';
    state.propsMode = 'RESEARCH';
    state.research.league = ev.league;
    selectResearchGame(ev);
    syncNavButtons();
  });
  modalBody.querySelectorAll('[data-game-leg]').forEach((el) => {
    el.addEventListener('click', () => {
      toggleLegInSandbox(gameLegs[Number(el.dataset.gameLeg)]);
      openGameDetailModal(ev);
    });
  });
  modalBody.querySelectorAll('[data-game-prop]').forEach((el) => {
    el.addEventListener('click', () => openPropDetailModal(gameProps[Number(el.dataset.gameProp)]));
  });
}

async function selectResearchGame(ev) {
  state.research.selectedGame = ev;
  state.research.isLoading = true;
  state.research.profile = null;
  state.research.simResult = null;
  render();

  const [candidates, injuries] = await Promise.all([
    fetchGameResearchCandidates(ev, state.props),
    fetchMatchupInjuries(ev)
  ]);
  state.research.candidates = candidates;
  state.research.injuries = injuries;
  if (candidates.length) {
    await selectResearchCandidate(candidates[0]);
  } else {
    state.research.isLoading = false;
    render();
  }
}

async function selectResearchCandidate(cand, preferStat = null) {
  state.research.selectedCandidate = cand;
  state.research.isLoading = true;
  render();

  const profile = await fetchPlayerResearchProfile(cand, state.props);
  state.research.profile = profile;
  const stats = Object.keys(profile.propLinesByLabel);
  const chosenStat = (preferStat && stats.includes(preferStat)) ? preferStat : (stats[0] || 'Points');
  state.research.selectedStat = chosenStat;
  state.research.selectedLine = profile.propLinesByLabel[chosenStat] ?? 10.5;
  state.research.selectedBarIdx = Math.max(0, profile.gameLogs.length - 1);
  runResearchSimulation();
  state.research.isLoading = false;
  render();
}

function runResearchSimulation() {
  const { profile, selectedStat, selectedLine, selectedGame, injuries, scenarioKey, simRunId } = state.research;
  if (!profile || !selectedStat) return;
  const hist = profile.gameLogs.map((g) => g.propValues[selectedStat]).filter((v) => v != null);
  state.research.simResult = PlayerPropSimEngine.simulatePropOutcomes({
    statLabel: selectedStat,
    historicalValues: hist,
    lineValue: selectedLine,
    upcomingGame: selectedGame,
    playerTeamAbbr: profile.teamAbbr,
    matchupInjuries: injuries,
    scenarioKey,
    runId: simRunId
  });
}

async function openPlayerInResearch(prop) {
  state.tab = 'props';
  state.propsMode = 'RESEARCH';
  syncNavButtons();
  const ev = state.events.find((e) => e.id === prop.eventId);
  if (ev) {
    state.research.league = ev.league;
    state.research.selectedGame = ev;
    const [candidates, injuries] = await Promise.all([
      fetchGameResearchCandidates(ev, state.props),
      fetchMatchupInjuries(ev)
    ]);
    state.research.candidates = candidates;
    state.research.injuries = injuries;
    const match = candidates.find((c) => c.playerId === prop.playerId) || candidates[0];
    if (match) await selectResearchCandidate(match, prop.statLabel);
  }
  render();
}

function renderParlaysTab() {
  const top3 = state.parlays.slice(0, 3);
  const rest = state.parlays.slice(3, 12);
  const fmt = state.prefs.oddsFormat;

  return `
    <div class="hero-card">
      <span class="hero-badge">25,000-Trial Multi-Sim Engine</span>
      <h1 class="hero-title">Top 3 Correlated Parlays</h1>
      <p class="hero-sub">Gaussian Copula + Student-t Heavy Tail (df=5) + Pace Stress across all upcoming sports slates.</p>
      <div class="hero-metrics">
        <div class="metric-box"><div class="metric-val" style="color:var(--emerald)">${state.parlays.length}</div><div class="metric-lbl">Ranked Slips</div></div>
        <div class="metric-box"><div class="metric-val">${state.props.length}</div><div class="metric-lbl">Player Props</div></div>
        <div class="metric-box"><div class="metric-val" style="color:var(--cyan)">${state.events.filter((e) => e.state !== 'FINAL').length}</div><div class="metric-lbl">Active Games</div></div>
      </div>
    </div>

    <div class="card card-clickable" id="quick-nba-news-banner" style="display:flex;align-items:center;justify-content:space-between;gap:10px;">
      <div>
        <div style="font-size:13px;font-weight:800;color:var(--cyan);">NBA News &amp; Weekly Player Availability</div>
        <div style="font-size:11px;color:var(--text-sec);">See who will &amp; won't play this week, B2B fatigue, and pick edges →</div>
      </div>
      <span class="tag tag-cyan">OPEN</span>
    </div>

    <div class="section-title">
      <span>Top 3 Picks (3-Pick Style)</span>
      <button class="btn-ghost-sm" id="go-lab-filters">Filters</button>
    </div>

    ${!top3.length ? `<div class="empty-box">${state.isLoading ? 'Running 25K multi-simulations on live markets…' : 'No parlays match current filter thresholds. Adjust filters in Lab.'}</div>` : ''}

    ${top3.map((p, idx) => renderParlayCard(p, idx, true, fmt)).join('')}

    ${rest.length ? `<div class="section-title">Additional Correlated Candidates</div>` : ''}
    ${rest.map((p, idx) => renderParlayCard(p, idx + 3, false, fmt)).join('')}
  `;
}

function renderParlayCard(p, idx, highlight, fmt) {
  return `
    <div class="card card-clickable" data-open-parlay="${idx}" style="${highlight ? 'border-color:color-mix(in srgb, var(--emerald) 45%, var(--border));' : ''}">
      <div class="card-header">
        <div style="display:flex;align-items:center;gap:6px;flex-wrap:wrap;">
          <span class="tag tag-emerald">#${p.rank} • ${p.legs.length}-PICK</span>
          <span class="tag tag-cyan">${esc(p.sharpGrade)}</span>
          ${p.isSameGame ? '<span class="tag tag-amber">SGP</span>' : ''}
        </div>
        <button class="btn-ghost-sm" data-save-parlay="${idx}">${state.savedParlayIds.has(p.id) ? '★' : '☆'}</button>
      </div>
      <div class="parlay-stats-row">
        <div><div class="stat-mini-val" style="color:var(--emerald)">${(p.correlatedProb * 100).toFixed(1)}%</div><div class="stat-mini-lbl">Corr Hit</div></div>
        <div><div class="stat-mini-val">${OddsMath.formatOdds(p.marketParlayAmericanOdds, fmt)}</div><div class="stat-mini-lbl">Odds</div></div>
        <div><div class="stat-mini-val" style="color:var(--cyan)">${OddsMath.formatPercent(p.correlatedEvPercent, true)}</div><div class="stat-mini-lbl">Corr +EV</div></div>
        <div><div class="stat-mini-val">+${p.avgRho.toFixed(2)}</div><div class="stat-mini-lbl">Avg ρ</div></div>
      </div>
      ${p.legs.map((leg, lIdx) => `
        <div class="leg-row" data-parlay-leg="${idx}:${lIdx}">
          <div class="leg-left">
            ${leg.homeLogoUrl ? `<img class="avatar-img" src="${esc(leg.homeLogoUrl)}" alt="" onerror="this.style.display='none'">` : ''}
            <div style="min-width:0;">
              <div class="leg-title">${esc(leg.selectionLabel)}</div>
              <div class="leg-sub">${esc(leg.league)} • ${esc(leg.matchupShort)}</div>
            </div>
          </div>
          <div class="leg-odds">
            <div class="odds-num">${OddsMath.formatOdds(leg.offeredAmericanOdds, fmt)}</div>
            <div class="prob-num">${(leg.fairTrueProb * 100).toFixed(0)}%</div>
          </div>
        </div>
      `).join('')}
      <div style="font-size:11px;color:var(--text-sec);margin-top:8px;">${esc(p.primaryThesis)}</div>
    </div>
  `;
}

function renderPropsTab() {
  return `
    <div class="seg-control">
      <button class="seg-btn ${state.propsMode === 'PROPS' ? 'active' : ''}" data-props-mode="PROPS">Player Props Slate</button>
      <button class="seg-btn ${state.propsMode === 'RESEARCH' ? 'active' : ''}" data-props-mode="RESEARCH">Player Research &amp; Sim</button>
    </div>
    ${state.propsMode === 'PROPS' ? renderPropsSlate() : renderResearchView()}
  `;
}

function renderPropsSlate() {
  const leagues = ['ALL', ...LEAGUES.map((l) => l.code)];
  const filtered = state.props.filter((p) => {
    if (state.propsLeague !== 'ALL' && p.league !== state.propsLeague) return false;
    if (state.propsDirection !== 'ALL' && p.recommendedSide !== state.propsDirection) return false;
    return true;
  });

  return `
    <div class="chip-row">
      ${leagues.map((l) => `<button class="chip ${state.propsLeague === l ? 'active' : ''}" data-props-league="${l}">${l}</button>`).join('')}
    </div>
    <div class="chip-row">
      ${['ALL', 'MORE', 'LESS'].map((d) => `<button class="chip cyan ${state.propsDirection === d ? 'active' : ''}" data-props-dir="${d}">${d === 'ALL' ? 'All Sides' : d}</button>`).join('')}
    </div>
    ${!filtered.length ? '<div class="empty-box">No player props match this filter yet. Try switching sport to ALL.</div>' : ''}
    ${filtered.slice(0, 40).map((p, i) => {
      const isMore = p.recommendedSide === 'MORE';
      const prob = isMore ? p.moreProb : p.lessProb;
      const ev = isMore ? p.moreEvPercent : p.lessEvPercent;
      const odds = isMore ? p.moreAmericanOdds : p.lessAmericanOdds;
      return `
        <div class="card card-clickable" data-open-prop="${esc(p.id)}">
          <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;">
            <div style="display:flex;align-items:center;gap:10px;min-width:0;">
              <img class="avatar-img" src="${esc(p.headshotUrl || '')}" alt="" onerror="this.style.display='none'">
              <div style="min-width:0;">
                <div style="font-size:14px;font-weight:800;">${esc(p.playerName)} <span class="tag tag-neutral">${esc(p.league)}</span></div>
                <div style="font-size:11px;color:var(--text-sec);">${esc(p.teamAbbr)} • ${esc(p.matchupShort)} • ${esc(p.startTimeDisplay)}</div>
              </div>
            </div>
            <span class="tag ${isMore ? 'tag-emerald' : 'tag-amber'}">${p.recommendedSide} ${formatLine(p.lineValue)} ${esc(p.statLabel)}</span>
          </div>
          <div style="font-size:12px;color:var(--text-sec);margin:8px 0;">${esc(p.confidenceThesis)}</div>
          <div style="display:flex;align-items:center;justify-content:space-between;font-size:12px;font-weight:700;">
            <span>Hit Prob: <b style="color:var(--emerald)">${(prob * 100).toFixed(1)}%</b> • +EV <b style="color:var(--cyan)">${OddsMath.formatPercent(ev, true)}</b> (${OddsMath.formatOdds(odds, state.prefs.oddsFormat)})</span>
            <button class="btn-ghost-sm" data-research-prop="${esc(p.id)}">Research →</button>
          </div>
        </div>
      `;
    }).join('')}
  `;
}

function renderResearchView() {
  const upcomingGames = state.events.filter((e) => e.state !== 'FINAL');
  const activeSports = ['ALL', ...LEAGUES.map((l) => l.code).filter((c) => upcomingGames.some((g) => g.league === c))];
  const games = upcomingGames.filter((g) => state.research.league === 'ALL' || g.league === state.research.league);
  const r = state.research;

  const gameChips = `
    <div class="section-title">1. Upcoming Games by Sport (${games.length} Games)</div>
    <div class="chip-row">
      ${activeSports.map((s) => `<button class="chip ${r.league === s ? 'active' : ''}" data-res-league="${s}">${s}</button>`).join('')}
    </div>
    <div class="chip-row">
      ${games.map((g) => `<button class="chip cyan ${r.selectedGame?.id === g.id ? 'active' : ''}" data-res-game="${esc(g.id)}">${esc(g.league)}: ${esc(g.shortName)} (${esc(g.statusDetail)})</button>`).join('')}
    </div>
  `;

  if (!r.selectedGame) {
    return `${gameChips}<div class="empty-box">Select any upcoming game above to research player box-score logs, alt lines &amp; 10,000-trial simulations.</div>`;
  }

  const candChips = r.candidates.length ? `
    <div class="section-title">2. Select Player (${esc(r.selectedGame.shortName)})</div>
    <div class="chip-row">
      ${r.candidates.map((c) => `<button class="chip ${r.selectedCandidate?.playerId === c.playerId ? 'active' : ''}" data-res-cand="${esc(c.playerId)}">${esc(c.teamAbbr)} • ${esc(c.playerName)}</button>`).join('')}
    </div>
  ` : '';

  if (r.isLoading) {
    return `${gameChips}${candChips}<div class="empty-box">Loading ESPN season gamelogs &amp; running 10K Monte Carlo…</div>`;
  }

  const profile = r.profile;
  if (!profile) return `${gameChips}${candChips}<div class="empty-box">Pick a player above to inspect recent games.</div>`;

  const statKeys = Object.keys(profile.propLinesByLabel);
  const logs = profile.gameLogs || [];
  const stat = r.selectedStat || statKeys[0] || 'Points';
  const line = r.selectedLine;
  const vals = logs.map((g) => g.propValues[stat] ?? 0);
  const maxVal = Math.max(line * 1.25, ...vals, 1);
  const hits = logs.filter((g) => (g.propValues[stat] ?? 0) > line).length;
  const presets = PlayerPropSimEngine.buildAltLinePresets(profile.propLinesByLabel[stat] ?? line, stat);
  const selectedBar = logs[r.selectedBarIdx] || logs[logs.length - 1];
  const sim = r.simResult;

  let barDetailHtml = '';
  if (selectedBar && selectedBar.propValues[stat] != null) {
    const actual = selectedBar.propValues[stat];
    const cleared = actual > line;
    const diff = actual - line;
    const reason = cleared
      ? `CLEARED by +${diff.toFixed(1)} ${stat} (${selectedBar.minutesDisplay ? selectedBar.minutesDisplay + ' min' : 'Active'}, ${selectedBar.efficiencySummary || 'Strong usage'}).`
      : `MISSED by ${diff.toFixed(1)} ${stat} (${selectedBar.minutesDisplay ? selectedBar.minutesDisplay + ' min' : 'Limited'}, ${selectedBar.efficiencySummary || 'Lower volume'}).`;
    barDetailHtml = `
      <div class="card" style="background:var(--surface);">
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <strong>${esc(selectedBar.dateDisplay)} ${esc(selectedBar.opponentAbbr)} (${esc(selectedBar.scoreResult)})</strong>
          <span class="tag ${cleared ? 'tag-emerald' : 'tag-coral'}">${cleared ? 'CLEARED' : 'MISSED'} (${actual} vs ${formatLine(line)})</span>
        </div>
        <div style="font-size:12px;color:var(--text-sec);margin-top:6px;">${esc(reason)}</div>
        <div style="font-size:11px;color:var(--text-muted);margin-top:6px;">
          Box: ${Object.entries(selectedBar.fullStats || {}).slice(0, 8).map(([k, v]) => `${k}: ${v}`).join(' • ')}
        </div>
      </div>
    `;
  }

  return `
    ${gameChips}
    ${candChips}
    <div class="card">
      <div style="display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:8px;">
        <div style="display:flex;align-items:center;gap:10px;">
          <img class="avatar-img" src="${esc(profile.headshotUrl || '')}" alt="" onerror="this.style.display='none'">
          <div>
            <div style="font-size:15px;font-weight:800;">${esc(profile.playerName)} <span class="tag tag-neutral">${esc(profile.teamAbbr)}</span></div>
            <div style="font-size:11px;color:var(--text-sec);">Hit Rate: <b style="color:var(--emerald)">${hits}/${logs.length}</b> (${logs.length ? Math.round((hits / logs.length) * 100) : 0}%) vs ${formatLine(line)} ${esc(stat)}</div>
          </div>
        </div>
      </div>

      <div class="chip-row">
        ${statKeys.map((sk) => `<button class="chip ${sk === stat ? 'active' : ''}" data-res-stat="${esc(sk)}">${esc(sk)}</button>`).join('')}
      </div>

      <div style="display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap;margin:8px 0;">
        <div class="chip-row" style="margin:0;">
          ${presets.map((pl) => `<button class="chip cyan ${Math.abs(pl - line) < 0.05 ? 'active' : ''}" data-res-line="${pl}">${formatLine(pl)}</button>`).join('')}
        </div>
        <div class="stepper-wrap">
          <button class="stepper-btn" id="line-dec-btn">-</button>
          <span style="font-size:13px;font-weight:800;">${formatLine(line)}</span>
          <button class="stepper-btn" id="line-inc-btn">+</button>
        </div>
      </div>

      ${logs.length ? `
        <div class="chart-scroll">
          ${logs.map((g, idx) => {
            const v = g.propValues[stat] ?? 0;
            const pct = Math.max(6, Math.min(100, (v / maxVal) * 100));
            const hit = v > line;
            return `
              <div class="bar-col ${idx === r.selectedBarIdx ? 'selected' : ''}" data-res-bar="${idx}">
                <div class="bar-val" style="color:${hit ? 'var(--emerald)' : 'var(--coral)'}">${v}</div>
                <div class="bar-track"><div class="bar-fill ${hit ? 'hit' : 'miss'}" style="height:${pct}%"></div></div>
                <div class="bar-opp">${esc(g.opponentAbbr)}</div>
                <div class="bar-date">${esc(g.dateDisplay)}</div>
              </div>
            `;
          }).join('')}
        </div>
        ${barDetailHtml}
      ` : `<div class="empty-box">${esc(profile.errorMessage || 'No recent gamelogs recorded for this player.')}</div>`}
    </div>

    ${sim ? `
      <div class="card">
        <div class="card-header">
          <span class="tag tag-emerald">10,000-Trial Prop Monte Carlo</span>
          <button class="btn-ghost-sm" id="rerun-prop-sim-btn">Re-Simulate</button>
        </div>
        <div class="chip-row">
          ${SIM_SCENARIOS.map((sc) => `<button class="chip ${r.scenarioKey === sc.key ? 'active' : ''}" data-res-scen="${sc.key}">${esc(sc.label)}</button>`).join('')}
        </div>
        <div class="parlay-stats-row">
          <div><div class="stat-mini-val" style="color:var(--emerald)">${(sim.moreProb * 100).toFixed(1)}%</div><div class="stat-mini-lbl">MORE ${formatLine(line)}</div></div>
          <div><div class="stat-mini-val" style="color:var(--coral)">${(sim.lessProb * 100).toFixed(1)}%</div><div class="stat-mini-lbl">LESS ${formatLine(line)}</div></div>
          <div><div class="stat-mini-val">${sim.simMean.toFixed(1)}</div><div class="stat-mini-lbl">Sim Mean</div></div>
          <div><div class="stat-mini-val">${sim.floorP10.toFixed(0)}–${sim.ceilingP90.toFixed(0)}</div><div class="stat-mini-lbl">10–90% Range</div></div>
        </div>
        <div class="hist-row">
          ${sim.histogram.map((b) => `
            <div class="hist-col">
              <div class="hist-bar" style="height:${Math.max(6, Math.round(b.fraction * 72))}px;background:${b.isMoreSide ? 'var(--emerald)' : 'var(--coral)'}"></div>
              <div class="hist-lbl">${esc(b.label)}</div>
            </div>
          `).join('')}
        </div>
      </div>
    ` : ''}
  `;
}

function renderLeaguesTab() {
  return `
    <div class="seg-control">
      <button class="seg-btn ${state.slateSubTab === 'SLATE' ? 'active' : ''}" data-slate-tab="SLATE">Live Scores &amp; Slate</button>
      <button class="seg-btn ${state.slateSubTab === 'NBA_NEWS' ? 'active' : ''}" data-slate-tab="NBA_NEWS">NBA News &amp; Week Outlook</button>
    </div>
    ${state.slateSubTab === 'SLATE' ? renderLiveSlateView() : renderNbaNewsView()}
  `;
}

function renderLiveSlateView() {
  const leagues = ['ALL', ...LEAGUES.map((l) => l.code)];
  const filtered = state.events.filter((e) => state.slateLeague === 'ALL' || e.league === state.slateLeague);
  return `
    <div class="chip-row">
      ${leagues.map((l) => `<button class="chip ${state.slateLeague === l ? 'active' : ''}" data-slate-league="${l}">${l}</button>`).join('')}
    </div>
    ${!filtered.length ? '<div class="empty-box">No events loaded for this league yet.</div>' : ''}
    <div class="game-grid">
      ${filtered.map((ev) => `
        <div class="card card-clickable" data-open-game="${esc(ev.id)}">
          <div class="card-header">
            <span class="tag ${ev.state === 'LIVE' ? 'tag-emerald' : ev.state === 'FINAL' ? 'tag-neutral' : 'tag-cyan'}">${esc(ev.league)} • ${esc(ev.statusDetail)}</span>
            ${ev.marketSnapshot?.totalLine ? `<span style="font-size:11px;color:var(--text-sec);">O/U ${ev.marketSnapshot.totalLine}</span>` : ''}
          </div>
          <div class="team-line">
            <div class="team-info">
              ${ev.awayTeam.logoUrl ? `<img class="team-logo" src="${esc(ev.awayTeam.logoUrl)}" alt="">` : ''}
              <div><div class="team-name">${esc(ev.awayTeam.abbreviation)}</div><div class="team-rec">${esc(ev.awayTeam.record)}</div></div>
            </div>
            <div class="team-score">${esc(ev.awayTeam.score ?? '-')}</div>
          </div>
          <div class="team-line">
            <div class="team-info">
              ${ev.homeTeam.logoUrl ? `<img class="team-logo" src="${esc(ev.homeTeam.logoUrl)}" alt="">` : ''}
              <div><div class="team-name">${esc(ev.homeTeam.abbreviation)}</div><div class="team-rec">${esc(ev.homeTeam.record)}</div></div>
            </div>
            <div class="team-score">${esc(ev.homeTeam.score ?? '-')}</div>
          </div>
        </div>
      `).join('')}
    </div>
  `;
}

function renderNbaNewsView() {
  if (state.nbaLoading && !state.nbaIntel) {
    return '<div class="empty-box">Fetching ESPN NBA News, Weekly Injury Report, Back-to-Backs &amp; Standings…</div>';
  }
  const intel = state.nbaIntel;
  if (!intel) {
    return '<div class="empty-box"><button class="btn btn-emerald" id="load-nba-intel-btn">Load NBA News &amp; Weekly Outlook</button></div>';
  }

  const counts = {
    WONT_PLAY: intel.weeklyStatuses.filter((s) => s.bucket === 'WONT_PLAY').length,
    QUESTIONABLE: intel.weeklyStatuses.filter((s) => s.bucket === 'QUESTIONABLE').length,
    PLAYING_THIS_WEEK: intel.weeklyStatuses.filter((s) => s.bucket === 'PLAYING_THIS_WEEK').length
  };
  const filteredPlayers = intel.weeklyStatuses.filter((s) => s.bucket === state.nbaBucket);
  const filteredArticles = intel.articles.filter((a) => state.nbaNewsFilter === 'ALL' || a.category === state.nbaNewsFilter);

  return `
    <div class="card">
      <div class="card-header">
        <strong>Who Will &amp; Won't Play This Week</strong>
        <button class="btn-ghost-sm" id="refresh-nba-intel-btn">Refresh</button>
      </div>
      <div class="seg-control">
        <button class="seg-btn ${state.nbaBucket === 'WONT_PLAY' ? 'active' : ''}" data-nba-bucket="WONT_PLAY">Won't Play (${counts.WONT_PLAY})</button>
        <button class="seg-btn ${state.nbaBucket === 'QUESTIONABLE' ? 'active' : ''}" data-nba-bucket="QUESTIONABLE">GTD (${counts.QUESTIONABLE})</button>
        <button class="seg-btn ${state.nbaBucket === 'PLAYING_THIS_WEEK' ? 'active' : ''}" data-nba-bucket="PLAYING_THIS_WEEK">Playing (${counts.PLAYING_THIS_WEEK})</button>
      </div>
      ${filteredPlayers.slice(0, 14).map((s) => `
        <div class="leg-row" style="cursor:default;align-items:flex-start;">
          <div class="leg-left">
            ${s.headshotUrl ? `<img class="avatar-img" src="${esc(s.headshotUrl)}" alt="" onerror="this.style.display='none'">` : ''}
            <div>
              <div class="leg-title">${esc(s.playerName)} <span class="tag ${s.bucket === 'WONT_PLAY' ? 'tag-coral' : s.bucket === 'QUESTIONABLE' ? 'tag-amber' : 'tag-emerald'}">${esc(s.statusLabel)}</span></div>
              <div class="leg-sub">${esc(s.teamAbbr)} • ${s.gamesThisWeek}G this week ${s.isBackToBackTeam ? '• B2B' : ''} • ${esc(s.nextMatchupDisplay)}</div>
              <div style="font-size:11px;color:var(--text-sec);margin-top:3px;">${esc(s.detail)}</div>
              ${s.beneficiaryTeammates.length ? `<div style="font-size:11px;color:var(--emerald);margin-top:3px;">Usage Boost Targets: ${esc(s.beneficiaryTeammates.join(' • '))}</div>` : ''}
            </div>
          </div>
        </div>
      `).join('')}
    </div>

    <div class="section-title">7-Day Schedule Density &amp; Back-to-Backs</div>
    <div class="chip-row">
      ${intel.schedules.slice(0, 12).map((sc) => `
        <div class="card" style="min-width:150px;margin:0;padding:10px;">
          <div style="font-weight:800;font-size:13px;">${esc(sc.teamAbbr)} • ${sc.gamesThisWeek} Games</div>
          <div style="font-size:11px;color:${sc.hasBackToBack ? 'var(--amber)' : 'var(--text-sec)'};">${sc.hasBackToBack ? '⚠ Back-to-Back Slate' : 'Normal Rest'}</div>
          <div style="font-size:10px;color:var(--text-muted);margin-top:3px;">${esc(sc.nextOpponentDisplay)}</div>
        </div>
      `).join('')}
    </div>

    <div class="section-title">Major NBA News &amp; Pick Impact</div>
    <div class="chip-row">
      ${[
        ['ALL', 'All News'],
        ['INJURY_UPDATE', 'Injuries'],
        ['LINEUP_ROTATION', 'Rotations'],
        ['PICK_EDGE', 'Pick Edges']
      ].map(([k, lbl]) => `<button class="chip ${state.nbaNewsFilter === k ? 'active' : ''}" data-nba-news="${k}">${lbl}</button>`).join('')}
    </div>
    ${filteredArticles.slice(0, 16).map((a) => `
      <div class="card">
        <div class="card-header">
          <span class="tag tag-cyan">${esc(a.category.replace('_', ' '))}</span>
          <span style="font-size:11px;color:var(--text-muted);">${esc(a.publishedDisplay)}</span>
        </div>
        <div style="font-size:14px;font-weight:800;margin-bottom:4px;">${esc(a.headline)}</div>
        <div style="font-size:12px;color:var(--text-sec);margin-bottom:6px;">${esc(a.summary)}</div>
        <div style="font-size:11px;color:var(--emerald);background:var(--emerald-dim);padding:6px 8px;border-radius:8px;">${esc(a.pickImpactNote)}</div>
      </div>
    `).join('')}

    ${intel.standings.length ? `
      <div class="section-title">NBA Standings Snapshot</div>
      <div class="card">
        ${intel.standings.slice(0, 10).map((st) => `
          <div class="control-row" style="padding:6px 0;font-size:12px;">
            <span><strong>${st.seed}. ${esc(st.teamAbbr)}</strong> <span style="color:var(--text-muted)">(${esc(st.conference)})</span></span>
            <span>${st.wins}-${st.losses} • L10 ${esc(st.lastTen)} • Strk ${esc(st.streak)}</span>
          </div>
        `).join('')}
      </div>
    ` : ''}
  `;
}

function renderEvTab() {
  const leagues = ['ALL', ...LEAGUES.map((l) => l.code)];
  const sorted = state.legs
    .filter((l) => state.evLeague === 'ALL' || l.league === state.evLeague)
    .sort((a, b) => {
      if (state.evSort === 'PROB') return b.fairTrueProb - a.fairTrueProb;
      if (state.evSort === 'KELLY') {
        return OddsMath.quarterKellyPercent(b.fairTrueProb, b.offeredDecimalOdds) -
          OddsMath.quarterKellyPercent(a.fairTrueProb, a.offeredDecimalOdds);
      }
      return b.evPercent - a.evPercent;
    });

  return `
    <div class="chip-row">
      ${leagues.map((l) => `<button class="chip ${state.evLeague === l ? 'active' : ''}" data-ev-league="${l}">${l}</button>`).join('')}
    </div>
    <div class="chip-row">
      ${[['EV', 'Sort: +EV %'], ['PROB', 'Sort: Hit Prob'], ['KELLY', 'Sort: ¼ Kelly']].map(([k, lbl]) => `
        <button class="chip cyan ${state.evSort === k ? 'active' : ''}" data-ev-sort="${k}">${lbl}</button>
      `).join('')}
    </div>
    ${sorted.slice(0, 45).map((leg) => {
      const inBox = state.sandboxLegs.some((s) => s.id === leg.id);
      const kelly = OddsMath.quarterKellyPercent(leg.fairTrueProb, leg.offeredDecimalOdds);
      return `
        <div class="card card-clickable" data-toggle-leg="${esc(leg.id)}" style="${inBox ? 'border-color:var(--emerald);' : ''}">
          <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;">
            <div>
              <div style="font-size:14px;font-weight:800;">${esc(leg.selectionLabel)}</div>
              <div style="font-size:11px;color:var(--text-sec);">${esc(leg.league)} • ${esc(leg.matchupShort)} • ${esc(leg.sharpBookLabel)}</div>
            </div>
            <span class="tag ${leg.evPercent >= 0 ? 'tag-emerald' : 'tag-neutral'}">${OddsMath.formatPercent(leg.evPercent, true)} EV</span>
          </div>
          <div style="display:flex;justify-content:space-between;align-items:center;margin-top:8px;font-size:12px;">
            <span>Odds <b>${OddsMath.formatOdds(leg.offeredAmericanOdds, state.prefs.oddsFormat)}</b> • Fair <b>${(leg.fairTrueProb * 100).toFixed(1)}%</b> • Kelly <b>${kelly.toFixed(1)}%</b></span>
            <span style="color:${inBox ? 'var(--emerald)' : 'var(--cyan)'};font-weight:800;">${inBox ? '✓ In Slip' : '+ Add'}</span>
          </div>
        </div>
      `;
    }).join('')}
  `;
}

function renderLabTab() {
  const p = state.prefs;
  return `
    <div class="card">
      <div class="card-header"><strong>Quant Engine &amp; Display Settings</strong></div>
      <div class="control-row">
        <span>Odds Format</span>
        <div style="display:flex;gap:6px;">
          <button class="chip ${p.oddsFormat === 'AMERICAN' ? 'active' : ''}" data-pref-odds="AMERICAN">American</button>
          <button class="chip ${p.oddsFormat === 'DECIMAL' ? 'active' : ''}" data-pref-odds="DECIMAL">Decimal</button>
        </div>
      </div>
      <div class="control-row">
        <span>Devig Method</span>
        <div style="display:flex;gap:6px;">
          <button class="chip ${p.devigMethod === 'POWER' ? 'active' : ''}" data-pref-devig="POWER">Power</button>
          <button class="chip ${p.devigMethod === 'MULTIPLICATIVE' ? 'active' : ''}" data-pref-devig="MULTIPLICATIVE">Mult</button>
        </div>
      </div>
      <div class="control-row">
        <span>Parlay Size</span>
        <div style="display:flex;gap:6px;">
          <button class="chip ${p.parlaySize === 'THREE_LEGS' ? 'active' : ''}" data-pref-size="THREE_LEGS">3-Pick</button>
          <button class="chip ${p.parlaySize === 'TWO_LEGS' ? 'active' : ''}" data-pref-size="TWO_LEGS">2-Leg</button>
          <button class="chip ${p.parlaySize === 'ALL' ? 'active' : ''}" data-pref-size="ALL">All</button>
        </div>
      </div>
      <div class="control-row">
        <span>Min Hit Prob (${Math.round(p.minProb * 100)}%)</span>
        <input type="range" id="pref-min-prob" min="25" max="60" value="${Math.round(p.minProb * 100)}" />
      </div>
      <div class="control-row">
        <span>Min Correlation ρ (+${p.minRho.toFixed(2)})</span>
        <input type="range" id="pref-min-rho" min="5" max="45" value="${Math.round(p.minRho * 100)}" />
      </div>
      <div class="control-row">
        <span>Dark Terminal Theme</span>
        <button class="chip ${p.darkTheme ? 'active' : ''}" id="pref-theme-btn">${p.darkTheme ? 'Obsidian Dark' : 'Light Mode'}</button>
      </div>
    </div>

    <div class="card">
      <div class="card-header"><strong>Active Sports Leagues</strong></div>
      <div class="chip-row" style="flex-wrap:wrap;overflow:visible;">
        ${LEAGUES.map((l) => `
          <button class="chip ${p.enabledLeagues.includes(l.code) ? 'active' : ''}" data-pref-league="${l.code}">${l.code}</button>
        `).join('')}
      </div>
    </div>

    <div class="section-title">Saved Parlays (${state.savedParlaysList.length})</div>
    ${!state.savedParlaysList.length ? '<div class="empty-box">Bookmark any correlated parlay with ☆ to track it here.</div>' : ''}
    ${state.savedParlaysList.map((sp, i) => `
      <div class="card card-clickable" data-open-saved-parlay="${i}">
        <div style="display:flex;justify-content:space-between;font-size:13px;font-weight:800;">
          <span>${sp.legs.length}-Pick • ${OddsMath.formatOdds(sp.marketParlayAmericanOdds, p.oddsFormat)}</span>
          <span style="color:var(--emerald)">${(sp.correlatedProb * 100).toFixed(1)}% Hit</span>
        </div>
        <div style="font-size:11px;color:var(--text-sec);margin-top:4px;">${sp.legs.map((l) => l.selectionLabel).join(' • ')}</div>
      </div>
    `).join('')}

    <div class="section-title">Saved Player Props (${state.savedPropsList.length})</div>
    ${!state.savedPropsList.length ? '<div class="empty-box">Bookmark any player prop pick to keep it handy on iPhone Safari.</div>' : ''}
    ${state.savedPropsList.map((pr, i) => `
      <div class="card card-clickable" data-open-saved-prop="${i}">
        <div style="display:flex;justify-content:space-between;font-size:13px;font-weight:800;">
          <span>${esc(pr.playerName)} • ${pr.recommendedSide} ${formatLine(pr.lineValue)} ${esc(pr.statLabel)}</span>
          <span style="color:var(--cyan)">${esc(pr.league)}</span>
        </div>
        <div style="font-size:11px;color:var(--text-sec);margin-top:3px;">${esc(pr.matchupShort)} • ${esc(pr.confidenceThesis)}</div>
      </div>
    `).join('')}
  `;
}

function render() {
  updateHeaderStatus();
  updateSandboxBar();
  let html = '';
  if (state.tab === 'parlays') html = renderParlaysTab();
  else if (state.tab === 'props') html = renderPropsTab();
  else if (state.tab === 'leagues') html = renderLeaguesTab();
  else if (state.tab === 'ev') html = renderEvTab();
  else html = renderLabTab();
  mainEl.innerHTML = html;
  bindViewEvents();
}

function bindViewEvents() {
  document.getElementById('quick-nba-news-banner')?.addEventListener('click', () => {
    state.tab = 'leagues';
    state.slateSubTab = 'NBA_NEWS';
    syncNavButtons();
    ensureNbaIntel();
    render();
  });
  document.getElementById('go-lab-filters')?.addEventListener('click', () => {
    state.tab = 'lab';
    syncNavButtons();
    render();
  });

  mainEl.querySelectorAll('[data-open-parlay]').forEach((el) => {
    el.addEventListener('click', (e) => {
      if (e.target.closest('[data-save-parlay]') || e.target.closest('[data-parlay-leg]')) return;
      const p = state.parlays[Number(el.dataset.openParlay)];
      if (p) openParlayDetailModal(p);
    });
  });
  mainEl.querySelectorAll('[data-save-parlay]').forEach((el) => {
    el.addEventListener('click', (e) => {
      e.stopPropagation();
      const p = state.parlays[Number(el.dataset.saveParlay)];
      if (p) toggleSaveParlay(p);
    });
  });
  mainEl.querySelectorAll('[data-parlay-leg]').forEach((el) => {
    el.addEventListener('click', (e) => {
      e.stopPropagation();
      const [pi, li] = el.dataset.parlayLeg.split(':').map(Number);
      const leg = state.parlays[pi]?.legs[li];
      if (!leg) return;
      const prop = state.props.find((p) => p.id === leg.id);
      if (prop) openPropDetailModal(prop);
      else openParlayDetailModal(state.parlays[pi]);
    });
  });

  mainEl.querySelectorAll('[data-props-mode]').forEach((el) => {
    el.addEventListener('click', () => {
      state.propsMode = el.dataset.propsMode;
      if (state.propsMode === 'RESEARCH' && !state.research.selectedGame) {
        const firstUpcoming = state.events.find((e) => e.state !== 'FINAL');
        if (firstUpcoming) selectResearchGame(firstUpcoming);
      }
      render();
    });
  });
  mainEl.querySelectorAll('[data-props-league]').forEach((el) => el.addEventListener('click', () => { state.propsLeague = el.dataset.propsLeague; render(); }));
  mainEl.querySelectorAll('[data-props-dir]').forEach((el) => el.addEventListener('click', () => { state.propsDirection = el.dataset.propsDir; render(); }));
  mainEl.querySelectorAll('[data-open-prop]').forEach((el) => {
    el.addEventListener('click', (e) => {
      if (e.target.closest('[data-research-prop]')) return;
      const p = state.props.find((x) => x.id === el.dataset.openProp);
      if (p) openPropDetailModal(p);
    });
  });
  mainEl.querySelectorAll('[data-research-prop]').forEach((el) => {
    el.addEventListener('click', (e) => {
      e.stopPropagation();
      const p = state.props.find((x) => x.id === el.dataset.researchProp);
      if (p) openPlayerInResearch(p);
    });
  });

  // Research controls
  mainEl.querySelectorAll('[data-res-league]').forEach((el) => el.addEventListener('click', () => { state.research.league = el.dataset.resLeague; render(); }));
  mainEl.querySelectorAll('[data-res-game]').forEach((el) => el.addEventListener('click', () => {
    const g = state.events.find((e) => e.id === el.dataset.resGame);
    if (g) selectResearchGame(g);
  }));
  mainEl.querySelectorAll('[data-res-cand]').forEach((el) => el.addEventListener('click', () => {
    const c = state.research.candidates.find((x) => x.playerId === el.dataset.resCand);
    if (c) selectResearchCandidate(c);
  }));
  mainEl.querySelectorAll('[data-res-stat]').forEach((el) => el.addEventListener('click', () => {
    const s = el.dataset.resStat;
    state.research.selectedStat = s;
    state.research.selectedLine = state.research.profile?.propLinesByLabel[s] ?? state.research.selectedLine;
    runResearchSimulation();
    render();
  }));
  mainEl.querySelectorAll('[data-res-line]').forEach((el) => el.addEventListener('click', () => {
    state.research.selectedLine = parseFloat(el.dataset.resLine);
    runResearchSimulation();
    render();
  }));
  document.getElementById('line-dec-btn')?.addEventListener('click', () => {
    const step = PlayerPropSimEngine.stepIncrementForStat(state.research.selectedStat || '');
    state.research.selectedLine = Math.max(0.5, state.research.selectedLine - step);
    runResearchSimulation();
    render();
  });
  document.getElementById('line-inc-btn')?.addEventListener('click', () => {
    const step = PlayerPropSimEngine.stepIncrementForStat(state.research.selectedStat || '');
    state.research.selectedLine = state.research.selectedLine + step;
    runResearchSimulation();
    render();
  });
  mainEl.querySelectorAll('[data-res-bar]').forEach((el) => el.addEventListener('click', () => {
    state.research.selectedBarIdx = Number(el.dataset.resBar);
    render();
  }));
  mainEl.querySelectorAll('[data-res-scen]').forEach((el) => el.addEventListener('click', () => {
    state.research.scenarioKey = el.dataset.resScen;
    runResearchSimulation();
    render();
  }));
  document.getElementById('rerun-prop-sim-btn')?.addEventListener('click', () => {
    state.research.simRunId += 1;
    runResearchSimulation();
    render();
  });

  // Leagues & NBA News
  mainEl.querySelectorAll('[data-slate-tab]').forEach((el) => el.addEventListener('click', () => {
    state.slateSubTab = el.dataset.slateTab;
    if (state.slateSubTab === 'NBA_NEWS') ensureNbaIntel();
    render();
  }));
  mainEl.querySelectorAll('[data-slate-league]').forEach((el) => el.addEventListener('click', () => { state.slateLeague = el.dataset.slateLeague; render(); }));
  mainEl.querySelectorAll('[data-open-game]').forEach((el) => el.addEventListener('click', () => {
    const g = state.events.find((e) => e.id === el.dataset.openGame);
    if (g) openGameDetailModal(g);
  }));
  document.getElementById('load-nba-intel-btn')?.addEventListener('click', () => ensureNbaIntel(true));
  document.getElementById('refresh-nba-intel-btn')?.addEventListener('click', () => ensureNbaIntel(true));
  mainEl.querySelectorAll('[data-nba-bucket]').forEach((el) => el.addEventListener('click', () => { state.nbaBucket = el.dataset.nbaBucket; render(); }));
  mainEl.querySelectorAll('[data-nba-news]').forEach((el) => el.addEventListener('click', () => { state.nbaNewsFilter = el.dataset.nbaNews; render(); }));

  // +EV Scanner
  mainEl.querySelectorAll('[data-ev-league]').forEach((el) => el.addEventListener('click', () => { state.evLeague = el.dataset.evLeague; render(); }));
  mainEl.querySelectorAll('[data-ev-sort]').forEach((el) => el.addEventListener('click', () => { state.evSort = el.dataset.evSort; render(); }));
  mainEl.querySelectorAll('[data-toggle-leg]').forEach((el) => el.addEventListener('click', () => {
    const leg = state.legs.find((l) => l.id === el.dataset.toggleLeg);
    if (leg) toggleLegInSandbox(leg);
  }));

  // Lab Settings
  mainEl.querySelectorAll('[data-pref-odds]').forEach((el) => el.addEventListener('click', () => {
    state.prefs.oddsFormat = el.dataset.prefOdds;
    savePersisted({ prefs: state.prefs });
    render();
  }));
  mainEl.querySelectorAll('[data-pref-devig]').forEach((el) => el.addEventListener('click', () => {
    state.prefs.devigMethod = el.dataset.prefDevig;
    savePersisted({ prefs: state.prefs });
    loadAllMarkets();
  }));
  mainEl.querySelectorAll('[data-pref-size]').forEach((el) => el.addEventListener('click', () => {
    state.prefs.parlaySize = el.dataset.prefSize;
    savePersisted({ prefs: state.prefs });
    recomputeParlays();
    render();
  }));
  document.getElementById('pref-min-prob')?.addEventListener('input', (e) => {
    state.prefs.minProb = Number(e.target.value) / 100;
    savePersisted({ prefs: state.prefs });
    recomputeParlays();
    render();
  });
  document.getElementById('pref-min-rho')?.addEventListener('input', (e) => {
    state.prefs.minRho = Number(e.target.value) / 100;
    savePersisted({ prefs: state.prefs });
    recomputeParlays();
    render();
  });
  document.getElementById('pref-theme-btn')?.addEventListener('click', () => {
    state.prefs.darkTheme = !state.prefs.darkTheme;
    document.documentElement.setAttribute('data-theme', state.prefs.darkTheme ? 'dark' : 'light');
    savePersisted({ prefs: state.prefs });
    render();
  });
  mainEl.querySelectorAll('[data-pref-league]').forEach((el) => el.addEventListener('click', () => {
    const code = el.dataset.prefLeague;
    const set = new Set(state.prefs.enabledLeagues);
    if (set.has(code) && set.size > 1) set.delete(code);
    else set.add(code);
    state.prefs.enabledLeagues = [...set];
    savePersisted({ prefs: state.prefs });
    loadAllMarkets();
  }));
  mainEl.querySelectorAll('[data-open-saved-parlay]').forEach((el) => el.addEventListener('click', () => {
    const sp = state.savedParlaysList[Number(el.dataset.openSavedParlay)];
    if (sp) openParlayDetailModal(sp);
  }));
  mainEl.querySelectorAll('[data-open-saved-prop]').forEach((el) => el.addEventListener('click', () => {
    const pr = state.savedPropsList[Number(el.dataset.openSavedProp)];
    if (pr) openPropDetailModal(pr);
  }));
}

function syncNavButtons() {
  document.querySelectorAll('.nav-btn').forEach((btn) => {
    btn.classList.toggle('active', btn.dataset.tab === state.tab);
  });
}

document.querySelectorAll('.nav-btn').forEach((btn) => {
  btn.addEventListener('click', () => {
    state.tab = btn.dataset.tab;
    syncNavButtons();
    if (state.tab === 'leagues' && state.slateSubTab === 'NBA_NEWS') ensureNbaIntel();
    render();
  });
});

document.getElementById('sandbox-clear-btn').addEventListener('click', () => {
  state.sandboxLegs = [];
  updateSandboxBar();
  render();
});

document.getElementById('sandbox-open-btn').addEventListener('click', () => {
  if (!state.sandboxLegs.length) return;
  openParlayDetailModal(simulateSandboxParlay());
});

livePillBtn.addEventListener('click', () => {
  state.autoRefresh = !state.autoRefresh;
  updateHeaderStatus();
  showToast(state.autoRefresh ? 'Live 12s auto-refresh enabled' : 'Auto-refresh paused');
});

refreshBtn.addEventListener('click', () => loadAllMarkets());

async function ensureNbaIntel(force = false) {
  if (state.nbaLoading) return;
  if (!force && state.nbaIntel && Date.now() - state.nbaIntel.lastUpdatedMillis < 45000) return;
  state.nbaLoading = true;
  render();
  try {
    state.nbaIntel = await fetchNbaIntel(state.props);
  } finally {
    state.nbaLoading = false;
    render();
  }
}

async function loadAllMarkets() {
  state.isLoading = true;
  updateHeaderStatus();
  render();
  try {
    const data = await fetchAllMarketData(state.prefs.enabledLeagues, state.prefs.devigMethod);
    state.events = data.events;
    state.legs = data.legs;
    state.props = data.props;
    state.lastUpdatedMillis = data.lastUpdatedMillis;
    recomputeParlays();
  } catch (e) {
    state.error = e.message;
  } finally {
    state.isLoading = false;
    render();
  }
}

setInterval(async () => {
  if (!state.autoRefresh || state.isLoading || !state.events.length) return;
  try {
    const res = await refreshLiveScoresOnly(state.prefs.enabledLeagues, state.events);
    state.events = res.events;
    state.lastUpdatedMillis = res.lastUpdatedMillis;
    updateHeaderStatus();
    if (state.tab === 'leagues' && state.slateSubTab === 'SLATE') render();
  } catch {}
}, 12000);

loadAllMarkets();
