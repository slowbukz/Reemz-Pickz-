import { LEAGUES, OddsMath, clamp, getLeagueMeta } from './quant.js';

const ESPN_SITE = 'https://site.api.espn.com/apis/site/v2/sports';
const ESPN_WEB = 'https://site.web.api.espn.com/apis/common/v3/sports';

const SPORT_HEADSHOT_PATH = {
  NBA: 'nba',
  NFL: 'nfl',
  NHL: 'nhl',
  MLB: 'mlb',
  NCAAB: 'mens-college-basketball',
  NCAAF: 'college-football',
  EPL: 'soccer'
};

const NBA_TEAM_MAP = {
  hawks: 'ATL', celtics: 'BOS', nets: 'BKN', hornets: 'CHA', bulls: 'CHI',
  cavaliers: 'CLE', mavericks: 'DAL', nuggets: 'DEN', pistons: 'DET', warriors: 'GS',
  rockets: 'HOU', pacers: 'IND', clippers: 'LAC', lakers: 'LAL', grizzlies: 'MEM',
  heat: 'MIA', bucks: 'MIL', timberwolves: 'MIN', pelicans: 'NO', knicks: 'NY',
  thunder: 'OKC', magic: 'ORL', '76ers': 'PHI', suns: 'PHX', 'trail blazers': 'POR',
  kings: 'SAC', spurs: 'SA', raptors: 'TOR', jazz: 'UTAH', wizards: 'WSH'
};

async function fetchJson(url) {
  const res = await fetch(url, { mode: 'cors' });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

function getEasternDateKeys(startOffset, count) {
  const keys = [];
  const now = new Date();
  const fmt = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'America/New_York',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  });
  for (let i = 0; i < count; i++) {
    const d = new Date(now.getTime() + (startOffset + i) * 86400000);
    keys.push(fmt.format(d).replace(/-/g, ''));
  }
  return keys;
}

export function formatIsoTime(iso) {
  if (!iso) return 'TBD';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso.slice(0, 16);
  return d.toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit'
  });
}

export function formatLine(val) {
  return Math.abs(val - Math.round(val)) < 0.01 ? `${Math.round(val)}` : val.toFixed(1);
}

function parseRecordWinPct(summary) {
  if (!summary) return 0.5;
  const parts = summary.split('-').map((p) => parseInt(p.trim(), 10)).filter((n) => !Number.isNaN(n));
  if (parts.length < 2) return 0.5;
  const [w, l, t = 0] = parts;
  const tot = w + l + t;
  return tot > 0 ? (w + 0.5 * t) / tot : 0.5;
}

function mapTeamInfo(compTeam) {
  const rec = (compTeam.records || []).find((r) => r.type === 'total' || r.name === 'overall')?.summary ||
    compTeam.records?.[0]?.summary || '';
  const avgStat = (compTeam.statistics || []).find((s) =>
    /points|avgpoints/i.test(s.name || '')
  );
  return {
    id: compTeam.team?.id || compTeam.id || '',
    name: compTeam.team?.displayName || compTeam.team?.shortDisplayName || 'Team',
    abbreviation: compTeam.team?.abbreviation || 'TM',
    logoUrl: compTeam.team?.logo || null,
    record: rec,
    score: compTeam.score ?? null,
    winPct: parseRecordWinPct(rec),
    avgPointsFor: avgStat ? parseFloat(avgStat.displayValue) : null
  };
}

function parseSpreadFromDetails(details, homeName) {
  if (!details) return null;
  const tokens = details.trim().split(/\s+/);
  const num = parseFloat(tokens[tokens.length - 1]);
  if (Number.isNaN(num)) return null;
  const teamTok = tokens.slice(0, -1).join(' ').toLowerCase();
  return homeName.toLowerCase().includes(teamTok) ? num : -num;
}

function buildGameEvent(leagueCode, dto, summary) {
  if (!dto?.id) return null;
  const comp = dto.competitions?.[0];
  if (!comp) return null;
  const competitors = comp.competitors || [];
  const homeDto = competitors.find((c) => c.homeAway === 'home') || competitors[0];
  const awayDto = competitors.find((c) => c.homeAway === 'away') || competitors[1];
  if (!homeDto || !awayDto) return null;

  const homeTeam = mapTeamInfo(homeDto);
  const awayTeam = mapTeamInfo(awayDto);

  const stateStr = comp.status?.type?.state || 'pre';
  const state = stateStr === 'in' ? 'LIVE' : stateStr === 'post' ? 'FINAL' : 'UPCOMING';
  const rawDetail = (comp.status?.type?.shortDetail || '').trim();
  let statusDetail = rawDetail;
  if (state === 'LIVE' && (!rawDetail || rawDetail.toLowerCase() === 'in progress')) {
    const clock = comp.status?.displayClock;
    const period = comp.status?.period;
    if (clock && period) statusDetail = `P${period} • ${clock}`;
    else if (period) statusDetail = `Period ${period} LIVE`;
    else statusDetail = 'LIVE';
  } else if (!statusDetail) {
    statusDetail = formatIsoTime(dto.date);
  }

  const predRaw = summary?.predictor?.homeTeam?.gameProjection;
  const predictorHomeWinProb = predRaw != null && !Number.isNaN(parseFloat(predRaw))
    ? clamp(parseFloat(predRaw) / 100, 0.05, 0.95) : null;

  const espnOdds = summary?.pickcenter?.[0] || comp.odds?.[0] || null;
  const marketSnapshot = espnOdds ? {
    providerName: espnOdds.provider?.name || 'ESPN Market',
    homeMoneyline: espnOdds.homeTeamOdds?.moneyLine ?? null,
    awayMoneyline: espnOdds.awayTeamOdds?.moneyLine ?? null,
    spreadLine: espnOdds.spread ?? parseSpreadFromDetails(espnOdds.details, homeTeam.name),
    homeSpreadOdds: espnOdds.homeTeamOdds?.spreadOdds ?? -110,
    awaySpreadOdds: espnOdds.awayTeamOdds?.spreadOdds ?? -110,
    totalLine: espnOdds.overUnder ?? null,
    overOdds: espnOdds.overOdds ?? -110,
    underOdds: espnOdds.underOdds ?? -110
  } : null;

  const dataSourceLabel = espnOdds
    ? `ESPN Market (${marketSnapshot.providerName})`
    : predictorHomeWinProb != null
      ? 'ESPN Analytics Model'
      : 'ESPN Schedule Feed';

  return {
    id: dto.id,
    league: leagueCode,
    name: dto.name || `${awayTeam.name} at ${homeTeam.name}`,
    shortName: dto.shortName || `${awayTeam.abbreviation} @ ${homeTeam.abbreviation}`,
    startTimeIso: dto.date || '',
    statusDetail,
    state,
    homeTeam,
    awayTeam,
    predictorHomeWinProb,
    marketSnapshot,
    dataSourceLabel
  };
}

function createLeg(ev, suffix, timeDisplay, marketType, label, sideTag, lineVal, offeredAm, fairProb, sharpBook, isFav, total, margin) {
  const dec = OddsMath.americanToDecimal(offeredAm);
  return {
    id: `${ev.id}_${suffix}`,
    eventId: ev.id,
    league: ev.league,
    matchupShort: ev.shortName,
    homeTeamName: ev.homeTeam.name,
    awayTeamName: ev.awayTeam.name,
    homeLogoUrl: ev.homeTeam.logoUrl,
    awayLogoUrl: ev.awayTeam.logoUrl,
    startTimeDisplay: timeDisplay,
    marketType,
    selectionLabel: label,
    sideTag,
    lineValue: lineVal,
    offeredAmericanOdds: offeredAm,
    offeredDecimalOdds: dec,
    marketImpliedProb: OddsMath.americanToImpliedProb(offeredAm),
    fairTrueProb: fairProb,
    evPercent: OddsMath.expectedValue(fairProb, dec),
    sharpBookLabel: sharpBook,
    isFavoriteSide: isFav,
    expectedTotal: total,
    expectedHomeMargin: margin
  };
}

function deriveMarketLegs(ev, devigMethod) {
  const snap = ev.marketSnapshot;
  const lm = getLeagueMeta(ev.league);
  const legs = [];
  const timeDisplay = formatIsoTime(ev.startTimeIso);
  const sharpLabel = 'ESPN Predictor + Devig';

  const marketHomeMl = snap?.homeMoneyline ?? null;
  const marketAwayMl = snap?.awayMoneyline ?? null;
  const spread = snap?.spreadLine ?? null;
  const total = snap?.totalLine ?? null;
  const recordAdj = (ev.homeTeam.winPct - ev.awayTeam.winPct) * 0.14;
  const spreadImpliedHome = spread != null ? OddsMath.normalCdf((-spread) / lm.marginStd) : null;
  const deviggedMlHome = (marketHomeMl != null && marketAwayMl != null)
    ? OddsMath.devigTwoWay(OddsMath.americanToImpliedProb(marketHomeMl), OddsMath.americanToImpliedProb(marketAwayMl), devigMethod)[0]
    : null;

  let fairHomeProb = null;
  if (ev.predictorHomeWinProb != null && deviggedMlHome != null) {
    fairHomeProb = clamp(ev.predictorHomeWinProb * 0.45 + deviggedMlHome * 0.55, 0.08, 0.92);
  } else if (ev.predictorHomeWinProb != null) {
    fairHomeProb = ev.predictorHomeWinProb;
  } else if (deviggedMlHome != null) {
    fairHomeProb = clamp(deviggedMlHome + recordAdj * 0.25, 0.08, 0.92);
  } else if (spreadImpliedHome != null) {
    fairHomeProb = clamp(spreadImpliedHome + recordAdj * 0.20, 0.08, 0.92);
  }

  const expectedHomeMargin = spread != null ? -spread : fairHomeProb != null ? OddsMath.inverseNormalCdf(fairHomeProb) * lm.marginStd : 0;

  if (fairHomeProb != null) {
    const fairAwayProb = 1 - fairHomeProb;
    const offH = marketHomeMl ?? OddsMath.probToAmerican(clamp(fairHomeProb * 1.025, 0.08, 0.92));
    const offA = marketAwayMl ?? OddsMath.probToAmerican(clamp(fairAwayProb * 1.025, 0.08, 0.92));
    legs.push(createLeg(ev, 'ml_home', timeDisplay, 'MONEYLINE', `${ev.homeTeam.abbreviation} ML`, 'HOME', null, offH, fairHomeProb, sharpLabel, fairHomeProb >= 0.5, total, expectedHomeMargin));
    legs.push(createLeg(ev, 'ml_away', timeDisplay, 'MONEYLINE', `${ev.awayTeam.abbreviation} ML`, 'AWAY', null, offA, fairAwayProb, sharpLabel, fairAwayProb >= 0.5, total, expectedHomeMargin));
  }

  if (spread != null) {
    const hSpOdds = snap?.homeSpreadOdds ?? -110;
    const aSpOdds = snap?.awaySpreadOdds ?? -110;
    const [devigH] = OddsMath.devigTwoWay(OddsMath.americanToImpliedProb(hSpOdds), OddsMath.americanToImpliedProb(aSpOdds), devigMethod);
    const modelCover = OddsMath.normalCdf((expectedHomeMargin + spread) / lm.marginStd);
    const fairHSp = clamp(devigH * 0.65 + modelCover * 0.35, 0.15, 0.85);
    const fairASp = 1 - fairHSp;
    const hText = spread > 0 ? `+${formatLine(spread)}` : formatLine(spread);
    const aSp = -spread;
    const aText = aSp > 0 ? `+${formatLine(aSp)}` : formatLine(aSp);
    legs.push(createLeg(ev, 'sp_home', timeDisplay, 'SPREAD', `${ev.homeTeam.abbreviation} ${hText}`, 'HOME', spread, hSpOdds, fairHSp, sharpLabel, spread < 0, total, expectedHomeMargin));
    legs.push(createLeg(ev, 'sp_away', timeDisplay, 'SPREAD', `${ev.awayTeam.abbreviation} ${aText}`, 'AWAY', aSp, aSpOdds, fairASp, sharpLabel, aSp < 0, total, expectedHomeMargin));

    const cushion = (ev.league === 'NFL' || ev.league === 'NCAAF') ? 3.5 : (ev.league === 'NBA' || ev.league === 'NCAAB') ? 4.5 : (ev.league === 'NHL' || ev.league === 'MLB') ? 1.5 : 0.5;
    const altLine = Math.round((spread + cushion) * 2) / 2;
    const altProb = clamp(OddsMath.normalCdf((expectedHomeMargin + altLine) / lm.marginStd), 0.55, 0.88);
    const altOdds = OddsMath.probToAmerican(Math.min(0.90, altProb * 1.015));
    const altText = altLine > 0 ? `+${formatLine(altLine)}` : formatLine(altLine);
    legs.push(createLeg(ev, 'alt_sp_home', timeDisplay, 'ALT_SPREAD', `${ev.homeTeam.abbreviation} ${altText} (Alt)`, 'HOME', altLine, altOdds, altProb, 'Gaussian Margin Model', spread < 0, total, expectedHomeMargin));
  }

  if (total != null && total > 0.5) {
    const ovOdds = snap?.overOdds ?? -110;
    const unOdds = snap?.underOdds ?? -110;
    const [devigOv] = OddsMath.devigTwoWay(OddsMath.americanToImpliedProb(ovOdds), OddsMath.americanToImpliedProb(unOdds), devigMethod);
    const combAvg = (ev.homeTeam.avgPointsFor && ev.awayTeam.avgPointsFor) ? (ev.homeTeam.avgPointsFor + ev.awayTeam.avgPointsFor) : null;
    const paceShift = combAvg ? ((combAvg - total) / lm.totalStd) * 0.08 : 0;
    const fairOv = clamp(devigOv + paceShift, 0.20, 0.80);
    legs.push(createLeg(ev, 'tot_over', timeDisplay, 'TOTAL_OVER', `Over ${formatLine(total)}`, 'OVER', total, ovOdds, fairOv, sharpLabel, false, total, expectedHomeMargin));
    legs.push(createLeg(ev, 'tot_under', timeDisplay, 'TOTAL_UNDER', `Under ${formatLine(total)}`, 'UNDER', total, unOdds, 1 - fairOv, sharpLabel, false, total, expectedHomeMargin));

    const altDisc = (ev.league === 'NBA' || ev.league === 'NCAAB') ? 3.5 : (ev.league === 'NFL' || ev.league === 'NCAAF') ? 2.5 : 0.5;
    const teamStd = lm.totalStd / Math.SQRT2;
    const hImp = total / 2 + expectedHomeMargin / 2;
    const hLine = Math.max(0.5, Math.round((hImp - altDisc) * 2) / 2);
    const hProb = clamp(OddsMath.normalCdf((hImp - hLine) / teamStd), 0.45, 0.84);
    legs.push(createLeg(ev, 'tt_home_over', timeDisplay, 'TEAM_TOTAL_OVER', `${ev.homeTeam.abbreviation} TT Ov ${formatLine(hLine)}`, 'HOME', hLine, OddsMath.probToAmerican(Math.min(0.87, hProb * 1.015)), hProb, 'Implied Team Total Model', expectedHomeMargin > 0, total, expectedHomeMargin));

    const aImp = total / 2 - expectedHomeMargin / 2;
    const aLine = Math.max(0.5, Math.round((aImp - altDisc) * 2) / 2);
    const aProb = clamp(OddsMath.normalCdf((aImp - aLine) / teamStd), 0.45, 0.84);
    legs.push(createLeg(ev, 'tt_away_over', timeDisplay, 'TEAM_TOTAL_OVER', `${ev.awayTeam.abbreviation} TT Ov ${formatLine(aLine)}`, 'AWAY', aLine, OddsMath.probToAmerican(Math.min(0.87, aProb * 1.015)), aProb, 'Implied Team Total Model', expectedHomeMargin < 0, total, expectedHomeMargin));
  }

  return legs;
}

function mapEspnStatCategory(leagueCode, name, displayName) {
  const key = `${name} ${displayName}`.toLowerCase();
  if (leagueCode === 'NBA' || leagueCode === 'NCAAB') {
    if (key.includes('point')) return { label: 'Points', stdDev: 6.2, defaultPos: 'F' };
    if (key.includes('rebound')) return { label: 'Rebounds', stdDev: 2.9, defaultPos: 'C' };
    if (key.includes('assist')) return { label: 'Assists', stdDev: 2.4, defaultPos: 'G' };
  } else if (leagueCode === 'NFL' || leagueCode === 'NCAAF') {
    if (key.includes('pass')) return { label: 'Pass Yds', stdDev: 48, defaultPos: 'QB' };
    if (key.includes('rush')) return { label: 'Rush Yds', stdDev: 24, defaultPos: 'RB' };
    if (key.includes('receiv')) return { label: 'Rec Yds', stdDev: 26, defaultPos: 'WR' };
  } else if (leagueCode === 'NHL') {
    if (key.includes('point')) return { label: 'Points', stdDev: 0.72, defaultPos: 'C' };
    if (key.includes('goal')) return { label: 'Shots on Goal', stdDev: 1.35, defaultPos: 'LW' };
    if (key.includes('assist')) return { label: 'Assists', stdDev: 0.65, defaultPos: 'C' };
  } else if (leagueCode === 'MLB') {
    if (key.includes('strikeout')) return { label: 'Strikeouts', stdDev: 1.85, defaultPos: 'P' };
    if (key.includes('home') || key.includes('hit') || key.includes('rbi')) return { label: 'Hits + Runs + RBIs', stdDev: 1.25, defaultPos: 'OF' };
  } else if (leagueCode === 'EPL') {
    if (key.includes('goal')) return { label: 'Shots on Target', stdDev: 0.92, defaultPos: 'FW' };
    if (key.includes('assist')) return { label: 'Shots Attempted', stdDev: 1.25, defaultPos: 'MF' };
  }
  return null;
}

function defaultBaselineForPos(leagueCode, pos, idx) {
  const p = (pos || '').toUpperCase();
  if (leagueCode === 'NBA' || leagueCode === 'NCAAB') {
    if (idx === 0) return { label: 'Points', stdDev: 6.2, defaultPos: p || 'F', baseline: leagueCode === 'NBA' ? 23.8 : 17.2 };
    if (idx === 1) return { label: 'Rebounds', stdDev: 2.9, defaultPos: p || 'C', baseline: leagueCode === 'NBA' ? 8.4 : 7.1 };
    return { label: 'Assists', stdDev: 2.4, defaultPos: p || 'G', baseline: leagueCode === 'NBA' ? 6.4 : 4.8 };
  }
  if (leagueCode === 'NFL' || leagueCode === 'NCAAF') {
    if (p === 'QB') return { label: 'Pass Yds', stdDev: 48, defaultPos: p, baseline: 248.5 };
    if (p === 'RB') return { label: 'Rush Yds', stdDev: 24, defaultPos: p, baseline: 71.5 };
    return { label: 'Rec Yds', stdDev: 26, defaultPos: p || 'WR', baseline: 66.5 };
  }
  if (leagueCode === 'NHL') {
    return idx === 0
      ? { label: 'Shots on Goal', stdDev: 1.35, defaultPos: p || 'LW', baseline: 3.2 }
      : { label: 'Points', stdDev: 0.72, defaultPos: p || 'C', baseline: 0.85 };
  }
  if (leagueCode === 'MLB') {
    return p.includes('P')
      ? { label: 'Strikeouts', stdDev: 1.85, defaultPos: p, baseline: 5.8 }
      : { label: 'Hits + Runs + RBIs', stdDev: 1.25, defaultPos: p || 'OF', baseline: 1.7 };
  }
  return { label: 'Shots on Target', stdDev: 0.92, defaultPos: p || 'FW', baseline: 1.35 };
}

function buildPropPick({ ev, timeDisplay, playerId, playerName, teamAbbr, sideTag, pos, headshotUrl, statLabel, seasonAvg, stdDev, total, expectedHomeMargin, isFav }) {
  const lm = getLeagueMeta(ev.league);
  let marketLine = Math.round((seasonAvg - 0.15) * 2) / 2;
  if (marketLine % 1 === 0) marketLine -= 0.5;
  marketLine = Math.max(0.5, marketLine);

  const totalRatio = clamp(total / lm.avgTotal, 0.84, 1.18);
  const teamMarginFactor = sideTag === 'HOME' ? expectedHomeMargin : -expectedHomeMargin;
  const marginShift = (teamMarginFactor / lm.marginStd) * 0.055;
  const paceShift = (totalRatio - 1) * 0.68;
  const projected = Math.max(0.3, seasonAvg * (1 + paceShift + marginShift));

  const moreProb = clamp(1 - OddsMath.normalCdf((marketLine - projected) / stdDev), 0.24, 0.76);
  const lessProb = clamp(1 - moreProb, 0.24, 0.76);
  const rawMarketMore = clamp(1 - OddsMath.normalCdf((marketLine - seasonAvg) / stdDev), 0.35, 0.65);
  const moreOdds = OddsMath.probToAmerican(clamp(rawMarketMore * 1.045, 0.38, 0.68));
  const lessOdds = OddsMath.probToAmerican(clamp((1 - rawMarketMore) * 1.045, 0.38, 0.68));
  const moreEv = OddsMath.expectedValue(moreProb, OddsMath.americanToDecimal(moreOdds));
  const lessEv = OddsMath.expectedValue(lessProb, OddsMath.americanToDecimal(lessOdds));
  const recommendedSide = moreEv >= lessEv ? 'MORE' : 'LESS';
  const diffPct = ((projected - marketLine) / marketLine) * 100;
  const sign = diffPct >= 0 ? '+' : '';
  const thesis = recommendedSide === 'MORE'
    ? `Proj ${projected.toFixed(1)} vs ${formatLine(marketLine)} (${sign}${diffPct.toFixed(1)}% pace & matchup edge toward MORE).`
    : `Proj ${projected.toFixed(1)} vs ${formatLine(marketLine)} (${sign}${diffPct.toFixed(1)}% compressed script toward LESS).`;

  const cleanStat = statLabel.toLowerCase().replace(/[^a-z0-9]/g, '_');
  return {
    id: `prop_${ev.id}_${playerId}_${cleanStat}`,
    eventId: ev.id,
    league: ev.league,
    matchupShort: ev.shortName,
    startTimeDisplay: timeDisplay,
    playerId,
    playerName,
    teamAbbr,
    sideTag,
    position: pos,
    headshotUrl,
    statLabel,
    lineValue: marketLine,
    projectedValue: projected,
    moreProb,
    lessProb,
    moreAmericanOdds: moreOdds,
    lessAmericanOdds: lessOdds,
    moreEvPercent: moreEv,
    lessEvPercent: lessEv,
    recommendedSide,
    confidenceThesis: thesis,
    isTeamFavorite: isFav,
    expectedTotal: total,
    expectedHomeMargin
  };
}

function propToLeg(prop) {
  const isMore = prop.recommendedSide === 'MORE';
  const odds = isMore ? prop.moreAmericanOdds : prop.lessAmericanOdds;
  const prob = isMore ? prop.moreProb : prop.lessProb;
  const ev = isMore ? prop.moreEvPercent : prop.lessEvPercent;
  const dec = OddsMath.americanToDecimal(odds);
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
    offeredDecimalOdds: dec,
    marketImpliedProb: OddsMath.americanToImpliedProb(odds),
    fairTrueProb: prob,
    evPercent: ev,
    sharpBookLabel: 'Pace & Stat Projection',
    isFavoriteSide: prop.isTeamFavorite,
    expectedTotal: prop.expectedTotal,
    expectedHomeMargin: prop.expectedHomeMargin
  };
}

function derivePropsFromLeaders(ev, dto, summary) {
  const lm = getLeagueMeta(ev.league);
  const comp = dto.competitions?.[0];
  const timeDisplay = formatIsoTime(ev.startTimeIso);
  const total = ev.marketSnapshot?.totalLine ?? lm.avgTotal;
  const spread = ev.marketSnapshot?.spreadLine ?? 0;
  const expectedHomeMargin = -spread;
  const collected = [];
  const seen = new Set();
  const sportPath = SPORT_HEADSHOT_PATH[ev.league] || 'nba';

  const processCat = (cat, tId, tAbbr) => {
    const statInfo = mapEspnStatCategory(ev.league, cat.name || '', cat.displayName || cat.shortDisplayName || '');
    if (!statInfo) return;
    for (const entry of (cat.leaders || []).slice(0, 2)) {
      const ath = entry.athlete;
      if (!ath?.id) continue;
      const pName = ath.displayName || ath.fullName || ath.shortName;
      const base = entry.value ?? parseFloat(entry.displayValue);
      if (!pName || Number.isNaN(base) || base <= 0.1) continue;
      const k = `${ev.id}_${ath.id}_${statInfo.label}`;
      if (seen.has(k)) continue;
      seen.add(k);
      const rTid = entry.team?.id || tId;
      const rAbbr = rTid === ev.homeTeam.id ? ev.homeTeam.abbreviation : rTid === ev.awayTeam.id ? ev.awayTeam.abbreviation : tAbbr;
      const rSide = rAbbr === ev.homeTeam.abbreviation ? 'HOME' : 'AWAY';
      const rFav = rSide === 'HOME' ? expectedHomeMargin >= 0 : expectedHomeMargin < 0;
      const headshot = ath.headshot?.href || `https://a.espncdn.com/i/headshots/${sportPath}/players/full/${ath.id}.png`;
      collected.push(buildPropPick({
        ev, timeDisplay, playerId: ath.id, playerName: pName, teamAbbr: rAbbr, sideTag: rSide,
        pos: ath.position?.abbreviation || statInfo.defaultPos, headshotUrl: headshot,
        statLabel: statInfo.label, seasonAvg: base, stdDev: statInfo.stdDev,
        total, expectedHomeMargin, isFav: rFav
      }));
    }
  };

  (comp?.competitors || []).forEach((ct) => {
    const tAbbr = ct.team?.abbreviation || 'TM';
    (ct.leaders || []).forEach((c) => processCat(c, ct.team?.id, tAbbr));
  });
  (comp?.leaders || []).forEach((c) => processCat(c, ev.homeTeam.id, ev.homeTeam.abbreviation));
  (summary?.leaders || []).forEach((tl) => {
    const tId = tl.team?.id;
    const tAbbr = tl.team?.abbreviation || (tId === ev.homeTeam.id ? ev.homeTeam.abbreviation : ev.awayTeam.abbreviation);
    (tl.leaders || []).forEach((c) => processCat(c, tId, tAbbr));
  });

  return collected.slice(0, 10);
}

export async function fetchAllMarketData(enabledLeagues, devigMethod = 'POWER') {
  const targets = LEAGUES.filter((l) => !enabledLeagues.length || enabledLeagues.includes(l.code));
  const upcomingDates = getEasternDateKeys(0, 5);

  const espnByLeague = {};
  await Promise.allSettled(targets.map(async (lm) => {
    const queryDates = (lm.code === 'NFL' || lm.code === 'NCAAF' || lm.code === 'EPL')
      ? upcomingDates.slice(0, 2)
      : upcomingDates;
    const urls = [
      `${ESPN_SITE}/${lm.sport}/${lm.league}/scoreboard`,
      ...queryDates.map((d) => `${ESPN_SITE}/${lm.sport}/${lm.league}/scoreboard?dates=${d}`)
    ];
    const results = await Promise.allSettled(urls.map(fetchJson));
    const map = new Map();
    for (const r of results) {
      if (r.status === 'fulfilled') {
        for (const ev of (r.value.events || [])) {
          if (ev.id && !map.has(ev.id)) map.set(ev.id, ev);
        }
      }
    }
    espnByLeague[lm.code] = [...map.values()];
  }));

  // Pick up to 16 active games round-robin for summary enrichment
  const queues = targets.map((lm) => ({
    league: lm,
    events: (espnByLeague[lm.code] || [])
      .filter((e) => e.competitions?.[0]?.status?.type?.state !== 'post')
      .sort((a, b) => {
        const sa = a.competitions?.[0]?.status?.type?.state === 'in' ? 0 : 1;
        const sb = b.competitions?.[0]?.status?.type?.state === 'in' ? 0 : 1;
        return sa - sb || (a.date || '').localeCompare(b.date || '');
      })
  })).filter((q) => q.events.length);

  const summaryCandidates = [];
  let idx = 0;
  while (summaryCandidates.length < 16) {
    let added = false;
    for (const q of queues) {
      if (idx < q.events.length) {
        summaryCandidates.push({ league: q.league, dto: q.events[idx] });
        added = true;
        if (summaryCandidates.length >= 16) break;
      }
    }
    if (!added) break;
    idx++;
  }

  const summaryMap = {};
  await Promise.allSettled(summaryCandidates.map(async ({ league, dto }) => {
    try {
      summaryMap[dto.id] = await fetchJson(`${ESPN_SITE}/${league.sport}/${league.league}/summary?event=${dto.id}`);
    } catch (_) {}
  }));

  const events = [];
  const legs = [];
  const props = [];
  const pendingRoster = [];

  for (const lm of targets) {
    for (const dto of (espnByLeague[lm.code] || [])) {
      const ev = buildGameEvent(lm.code, dto, summaryMap[dto.id]);
      if (!ev) continue;
      events.push(ev);
      if (ev.state !== 'FINAL') {
        legs.push(...deriveMarketLegs(ev, devigMethod));
        const lp = derivePropsFromLeaders(ev, dto, summaryMap[dto.id]);
        if (lp.length) props.push(...lp);
        else pendingRoster.push(ev);
      }
    }
  }

  // Fallback roster props for upcoming games without scoreboard leaders
  const rosterTargets = pendingRoster.slice(0, 8);
  const teamFetchMap = new Map();
  rosterTargets.forEach((ev) => {
    const lm = getLeagueMeta(ev.league);
    if (ev.homeTeam.id) teamFetchMap.set(`${ev.league}_${ev.homeTeam.id}`, { lm, teamId: ev.homeTeam.id });
    if (ev.awayTeam.id) teamFetchMap.set(`${ev.league}_${ev.awayTeam.id}`, { lm, teamId: ev.awayTeam.id });
  });

  const rosterData = {};
  await Promise.allSettled([...teamFetchMap.entries()].slice(0, 14).map(async ([k, { lm, teamId }]) => {
    try {
      const data = await fetchJson(`${ESPN_SITE}/${lm.sport}/${lm.league}/teams/${teamId}/roster`);
      const list = [];
      for (const block of (data.athletes || [])) {
        if (Array.isArray(block.items)) list.push(...block.items);
        else list.push(block);
      }
      rosterData[k] = list.filter((a) => a.id && a.displayName && a.position?.abbreviation).slice(0, 8);
    } catch (_) {}
  }));

  for (const ev of rosterTargets) {
    const lm = getLeagueMeta(ev.league);
    const total = ev.marketSnapshot?.totalLine ?? lm.avgTotal;
    const spread = ev.marketSnapshot?.spreadLine ?? 0;
    const expectedHomeMargin = -spread;
    const timeDisplay = formatIsoTime(ev.startTimeIso);
    const sportPath = SPORT_HEADSHOT_PATH[ev.league] || 'nba';

    const addSide = (team, sideTag, isFav) => {
      const athletes = (rosterData[`${ev.league}_${team.id}`] || []).slice(0, 3);
      athletes.forEach((ath, i) => {
        const spec = defaultBaselineForPos(ev.league, ath.position?.abbreviation, i);
        props.push(buildPropPick({
          ev, timeDisplay, playerId: ath.id, playerName: ath.displayName, teamAbbr: team.abbreviation,
          sideTag, pos: ath.position?.abbreviation || spec.defaultPos,
          headshotUrl: ath.headshot?.href || `https://a.espncdn.com/i/headshots/${sportPath}/players/full/${ath.id}.png`,
          statLabel: spec.label, seasonAvg: spec.baseline, stdDev: spec.stdDev,
          total, expectedHomeMargin, isFav
        }));
      });
    };
    addSide(ev.homeTeam, 'HOME', expectedHomeMargin >= 0);
    addSide(ev.awayTeam, 'AWAY', expectedHomeMargin < 0);
  }

  props.forEach((p) => legs.push(propToLeg(p)));

  const stateRank = { LIVE: 0, UPCOMING: 1, FINAL: 2 };
  events.sort((a, b) => stateRank[a.state] - stateRank[b.state] || a.startTimeIso.localeCompare(b.startTimeIso));

  const seenProp = new Set();
  const dedupedProps = props.filter((p) => {
    if (seenProp.has(p.id)) return false;
    seenProp.add(p.id);
    return true;
  }).sort((a, b) =>
    (Math.max(b.moreEvPercent, b.lessEvPercent) * 1.5 + Math.max(b.moreProb, b.lessProb) * 0.5) -
    (Math.max(a.moreEvPercent, a.lessEvPercent) * 1.5 + Math.max(a.moreProb, a.lessProb) * 0.5)
  );

  return {
    events,
    legs,
    props: dedupedProps,
    lastUpdatedMillis: Date.now()
  };
}

export async function refreshLiveScoresOnly(enabledLeagues, currentEvents) {
  const targets = LEAGUES.filter((l) => !enabledLeagues.length || enabledLeagues.includes(l.code));
  const byId = new Map(currentEvents.map((e) => [e.id, e]));

  await Promise.allSettled(targets.map(async (lm) => {
    const data = await fetchJson(`${ESPN_SITE}/${lm.sport}/${lm.league}/scoreboard`);
    for (const dto of (data.events || [])) {
      const fresh = buildGameEvent(lm.code, dto, null);
      if (!fresh) continue;
      const existing = byId.get(fresh.id);
      if (existing) {
        byId.set(fresh.id, {
          ...fresh,
          predictorHomeWinProb: existing.predictorHomeWinProb ?? fresh.predictorHomeWinProb,
          marketSnapshot: existing.marketSnapshot ?? fresh.marketSnapshot
        });
      } else {
        byId.set(fresh.id, fresh);
      }
    }
  }));

  const stateRank = { LIVE: 0, UPCOMING: 1, FINAL: 2 };
  const sorted = [...byId.values()].sort((a, b) =>
    stateRank[a.state] - stateRank[b.state] || a.startTimeIso.localeCompare(b.startTimeIso)
  );
  return { events: sorted, lastUpdatedMillis: Date.now() };
}

export async function fetchMatchupInjuries(ev) {
  const lm = getLeagueMeta(ev.league);
  const collected = [];
  const seen = new Set();
  const sportPath = SPORT_HEADSHOT_PATH[ev.league] || 'nba';

  const parseInjArr = (arr) => {
    if (!Array.isArray(arr)) return;
    const hLast = ev.homeTeam.name.split(' ').pop().toLowerCase();
    const aLast = ev.awayTeam.name.split(' ').pop().toLowerCase();
    for (const block of arr) {
      const tObj = block.team || {};
      const bId = tObj.id || '';
      const bAbbr = tObj.abbreviation || '';
      const bName = (tObj.displayName || block.displayName || '').toLowerCase();
      const isHome = (ev.homeTeam.id && bId === ev.homeTeam.id) || bAbbr.toLowerCase() === ev.homeTeam.abbreviation.toLowerCase() || (hLast && bName.includes(hLast));
      const isAway = (ev.awayTeam.id && bId === ev.awayTeam.id) || bAbbr.toLowerCase() === ev.awayTeam.abbreviation.toLowerCase() || (aLast && bName.includes(aLast));
      if (!isHome && !isAway) continue;
      const resolvedAbbr = isHome ? ev.homeTeam.abbreviation : ev.awayTeam.abbreviation;
      for (const inj of (block.injuries || [])) {
        const ath = inj.athlete;
        if (!ath) continue;
        const pid = ath.id || ath.displayName;
        if (!pid || seen.has(`${resolvedAbbr}_${pid}`)) continue;
        seen.add(`${resolvedAbbr}_${pid}`);
        const statusStr = inj.status || inj.type?.description || 'Questionable';
        const sLower = statusStr.toLowerCase();
        const severity = (sLower.includes('out') || sLower.includes('ir') || sLower.includes('susp')) ? 'OUT'
          : sLower.includes('doubt') ? 'DOUBTFUL'
          : (sLower.includes('question') || sLower.includes('gtd')) ? 'QUESTIONABLE'
          : sLower.includes('day') ? 'DAY_TO_DAY' : 'PROBABLE';
        collected.push({
          playerId: pid,
          playerName: ath.displayName || ath.fullName || ath.shortName || 'Player',
          teamAbbr: resolvedAbbr,
          position: ath.position?.abbreviation || '',
          status: statusStr,
          severity,
          detail: inj.shortComment || inj.details?.detail || inj.details?.type || 'Listed on official injury report',
          headshotUrl: ath.headshot?.href || `https://a.espncdn.com/i/headshots/${sportPath}/players/full/${pid}.png`
        });
      }
    }
  };

  try {
    const sum = await fetchJson(`${ESPN_SITE}/${lm.sport}/${lm.league}/summary?event=${ev.id}`);
    parseInjArr(sum.injuries);
  } catch (_) {}
  if (!collected.length) {
    try {
      const leagueInj = await fetchJson(`${ESPN_SITE}/${lm.sport}/${lm.league}/injuries`);
      parseInjArr(leagueInj.injuries);
    } catch (_) {}
  }
  return collected;
}

export async function fetchGameResearchCandidates(ev, existingProps) {
  const lm = getLeagueMeta(ev.league);
  const sportPath = SPORT_HEADSHOT_PATH[ev.league] || 'nba';
  const result = [];
  const seen = new Set();

  existingProps.filter((p) => p.eventId === ev.id).forEach((p) => {
    if (!seen.has(p.playerId)) {
      seen.add(p.playerId);
      result.push({
        playerId: p.playerId,
        playerName: p.playerName,
        teamAbbr: p.teamAbbr,
        position: p.position,
        headshotUrl: p.headshotUrl,
        eventId: ev.id,
        league: ev.league,
        matchupShort: ev.shortName
      });
    }
  });

  const teams = [
    { id: ev.homeTeam.id, abbr: ev.homeTeam.abbreviation },
    { id: ev.awayTeam.id, abbr: ev.awayTeam.abbreviation }
  ].filter((t) => t.id);

  await Promise.allSettled(teams.map(async (t) => {
    try {
      const data = await fetchJson(`${ESPN_SITE}/${lm.sport}/${lm.league}/teams/${t.id}/roster`);
      const list = [];
      for (const b of (data.athletes || [])) {
        if (Array.isArray(b.items)) list.push(...b.items);
        else list.push(b);
      }
      for (const ath of list.slice(0, 12)) {
        if (!ath.id || !ath.displayName || seen.has(ath.id)) continue;
        seen.add(ath.id);
        result.push({
          playerId: ath.id,
          playerName: ath.displayName,
          teamAbbr: t.abbr,
          position: ath.position?.abbreviation || '',
          headshotUrl: ath.headshot?.href || `https://a.espncdn.com/i/headshots/${sportPath}/players/full/${ath.id}.png`,
          eventId: ev.id,
          league: ev.league,
          matchupShort: ev.shortName
        });
      }
    } catch (_) {}
  }));

  return result.slice(0, 26);
}

function extractGamelogProps(leagueCode, statMap) {
  const num = (...keys) => {
    for (const k of keys) {
      const raw = statMap[k];
      if (raw == null) continue;
      const clean = (raw.includes('-') && !raw.startsWith('-')) ? raw.split('-')[0] : raw;
      const n = parseFloat(clean);
      if (!Number.isNaN(n)) return n;
    }
    return null;
  };

  const out = {};
  if (leagueCode === 'NBA' || leagueCode === 'NCAAB') {
    const pts = num('PTS', 'points');
    const reb = num('REB', 'rebounds', 'totalrebounds');
    const ast = num('AST', 'assists');
    const th = num('3PT', '3PM', 'threepointfieldgoalsmade-threepointfieldgoalsattempted');
    const stl = num('STL', 'steals');
    const blk = num('BLK', 'blocks');
    if (pts != null) out.Points = pts;
    if (reb != null) out.Rebounds = reb;
    if (ast != null) out.Assists = ast;
    if (th != null) out['3PM'] = th;
    if (pts != null && reb != null && ast != null) out['Pts+Reb+Ast'] = pts + reb + ast;
    if (stl != null && blk != null) out['Stl+Blk'] = stl + blk;
  } else if (leagueCode === 'NFL' || leagueCode === 'NCAAF') {
    const py = num('passingyards', 'PYDS'); if (py != null) out['Pass Yds'] = py;
    const ptd = num('passingtouchdowns', 'PTD'); if (ptd != null) out['Pass TDs'] = ptd;
    const ry = num('rushingyards', 'RYDS'); if (ry != null) out['Rush Yds'] = ry;
    const rcy = num('receivingyards', 'RECYDS'); if (rcy != null) out['Rec Yds'] = rcy;
    const rec = num('receptions', 'REC'); if (rec != null) out.Receptions = rec;
    if (!Object.keys(out).length) {
      const y = num('YDS'); if (y != null) out.Yards = y;
      const td = num('TD'); if (td != null) out.TDs = td;
    }
  } else if (leagueCode === 'NHL') {
    const g = num('G', 'goals');
    const a = num('A', 'assists');
    const pts = num('PTS', 'points') ?? (g != null && a != null ? g + a : null);
    const sog = num('SOG', 'S', 'shots', 'shotsontarget');
    if (sog != null) out['Shots on Goal'] = sog;
    if (pts != null) out.Points = pts;
    if (g != null) out.Goals = g;
    if (a != null) out.Assists = a;
  } else if (leagueCode === 'MLB') {
    const h = num('H', 'hits'), r = num('R', 'runs'), rbi = num('RBI', 'rbis'), hr = num('HR', 'homeruns'), so = num('SO', 'K', 'strikeouts');
    if (so != null && h == null) out.Strikeouts = so;
    else {
      if (h != null && r != null && rbi != null) out['Hits + Runs + RBIs'] = h + r + rbi;
      if (h != null) out.Hits = h;
      if (hr != null) out['Home Runs'] = hr;
    }
  } else if (leagueCode === 'EPL') {
    const sh = num('SH', 'shots', 'totalshots');
    const st = num('ST', 'SOG', 'shotsontarget');
    const g = num('G', 'goals') ?? 0;
    const a = num('A', 'assists') ?? 0;
    if (st != null) out['Shots on Target'] = st;
    if (sh != null) out['Shots Attempted'] = sh;
    out['Goals + Assists'] = g + a;
  }
  return out;
}

export async function fetchPlayerResearchProfile(candidate, allProps) {
  const lm = getLeagueMeta(candidate.league);
  const url = `${ESPN_WEB}/${lm.sport}/${lm.league}/athletes/${candidate.playerId}/gamelog`;
  let gameLogs = [];
  let errorMessage = null;

  try {
    const root = await fetchJson(url);
    const labels = root.labels || [];
    const names = root.names || [];
    const eventsMap = root.events || {};
    const rawEntries = [];
    for (const st of (root.seasonTypes || [])) {
      for (const cat of (st.categories || [])) {
        for (const ev of (cat.events || [])) {
          if (ev.eventId && Array.isArray(ev.stats) && ev.stats.length) rawEntries.push([ev.eventId, ev.stats]);
        }
      }
      for (const ev of (st.events || [])) {
        if (ev.eventId && Array.isArray(ev.stats) && ev.stats.length) rawEntries.push([ev.eventId, ev.stats]);
      }
    }

    const seenEid = new Set();
    const mapped = [];
    for (const [eid, stats] of rawEntries) {
      if (seenEid.has(eid)) continue;
      seenEid.add(eid);
      if (mapped.length >= 10) break;
      const meta = eventsMap[eid] || {};
      const statMap = {};
      const fullStats = {};
      stats.forEach((val, i) => {
        const lk = labels[i] ? String(labels[i]).toUpperCase() : null;
        const nk = names[i] ? String(names[i]).toLowerCase() : null;
        if (lk) { statMap[lk] = val; fullStats[lk] = val; }
        if (nk) statMap[nk] = val;
      });
      const propValues = extractGamelogProps(candidate.league, statMap);
      if (!Object.keys(propValues).length) continue;

      const minsRaw = statMap.MIN || statMap.TOI || statMap.minutes || '';
      const oppAbbr = meta.opponent?.abbreviation || meta.opponentAbbr || 'OPP';
      const atVs = meta.atVs || (meta.homeAway === 'away' ? '@' : 'vs');
      const dStr = meta.gameDate ? new Date(meta.gameDate).toLocaleDateString('en-US', { month: 'numeric', day: 'numeric' }) : '';
      const effParts = [];
      if (statMap.FG) effParts.push(`${statMap.FG} FG`);
      if (statMap['3PT']) effParts.push(`${statMap['3PT']} 3PT`);
      if (statMap.FT) effParts.push(`${statMap.FT} FT`);
      if (statMap.TO) effParts.push(`${statMap.TO} TO`);

      mapped.push({
        eventId: eid,
        dateDisplay: dStr,
        opponentAbbr: `${atVs}${oppAbbr}`,
        scoreResult: `${meta.gameResult || ''} ${meta.score || ''}`.trim(),
        propValues,
        minutesDisplay: minsRaw,
        fullStats,
        efficiencySummary: effParts.join(' • ')
      });
    }
    gameLogs = mapped.reverse();
    if (!gameLogs.length) errorMessage = 'No recent completed season games found in ESPN gamelog.';
  } catch (e) {
    errorMessage = `Could not load ESPN gamelog for ${candidate.playerName}.`;
  }

  const propLinesByLabel = {};
  allProps.filter((p) => p.playerId === candidate.playerId).forEach((p) => {
    propLinesByLabel[p.statLabel] = p.lineValue;
  });
  const cats = [...new Set(gameLogs.flatMap((g) => Object.keys(g.propValues)))];
  for (const c of cats) {
    if (propLinesByLabel[c] == null) {
      const vals = gameLogs.map((g) => g.propValues[c]).filter((v) => v != null);
      if (vals.length) {
        const avg = vals.reduce((a, b) => a + b, 0) / vals.length;
        let line = Math.round(avg * 2) / 2;
        if (line % 1 === 0) line = Math.max(0.5, line - 0.5);
        propLinesByLabel[c] = Math.max(0.5, line);
      }
    }
  }

  return {
    ...candidate,
    propLinesByLabel,
    gameLogs,
    errorMessage
  };
}

export async function fetchNbaIntel(activeProps) {
  const allDates = getEasternDateKeys(-1, 8);
  const yesterdayKey = allDates[0];
  const weekDateKeys = allDates.slice(1);
  const weekSet = new Set(weekDateKeys);

  const [newsRes, injRes, txRes, standRes, ...schedRes] = await Promise.allSettled([
    fetchJson(`${ESPN_SITE}/basketball/nba/news?limit=30`),
    fetchJson(`${ESPN_SITE}/basketball/nba/injuries`),
    fetchJson(`${ESPN_SITE}/basketball/nba/transactions?limit=25`),
    fetchJson('https://site.api.espn.com/apis/v2/sports/basketball/nba/standings'),
    ...allDates.map((d) => fetchJson(`${ESPN_SITE}/basketball/nba/scoreboard?dates=${d}`))
  ]);

  // Build weekly team schedules + B2B detector
  const teamWeeks = new Map();
  allDates.forEach((dKey, i) => {
    const res = schedRes[i];
    if (res?.status !== 'fulfilled') return;
    for (const dto of (res.value.events || [])) {
      const comp = dto.competitions?.[0];
      const comps = comp?.competitors || [];
      const h = comps.find((c) => c.homeAway === 'home') || comps[0];
      const a = comps.find((c) => c.homeAway === 'away') || comps[1];
      if (!h || !a) continue;
      const hAbbr = h.team?.abbreviation || 'HOME';
      const aAbbr = a.team?.abbreviation || 'AWAY';
      const total = comp.odds?.[0]?.overUnder || null;
      const timeStr = comp.status?.type?.shortDetail || dKey;

      [[h, `vs ${aAbbr}`], [a, `@ ${hAbbr}`]].forEach(([tc, oppLabel]) => {
        const abbr = tc.team?.abbreviation;
        if (!abbr) return;
        const k = abbr.toUpperCase();
        if (!teamWeeks.has(k)) {
          teamWeeks.set(k, {
            teamAbbr: abbr,
            teamName: tc.team?.displayName || abbr,
            logoUrl: tc.team?.logo || null,
            playedDates: new Set(),
            futureDates: new Set(),
            nextOpponentDisplay: 'This Week',
            nextKey: '99999999',
            totals: []
          });
        }
        const entry = teamWeeks.get(k);
        entry.playedDates.add(dKey);
        if (weekSet.has(dKey)) {
          entry.futureDates.add(dKey);
          if (total && total > 150) entry.totals.push(total);
          if (dKey < entry.nextKey) {
            entry.nextKey = dKey;
            entry.nextOpponentDisplay = `${oppLabel} • ${timeStr}`;
          }
        }
      });
    }
  });

  const schedules = [...teamWeeks.values()].map((e) => {
    let hasBackToBack = false;
    for (let i = 0; i < allDates.length - 1; i++) {
      if (e.playedDates.has(allDates[i]) && e.playedDates.has(allDates[i + 1])) {
        hasBackToBack = true;
        break;
      }
    }
    return {
      teamAbbr: e.teamAbbr,
      teamName: e.teamName,
      logoUrl: e.logoUrl,
      gamesThisWeek: e.futureDates.size,
      hasBackToBack,
      nextOpponentDisplay: e.nextOpponentDisplay,
      highestTotalLine: e.totals.length ? Math.max(...e.totals) : null
    };
  }).sort((a, b) => Number(b.hasBackToBack) - Number(a.hasBackToBack) || b.gamesThisWeek - a.gamesThisWeek);

  const schedByAbbr = new Map(schedules.map((s) => [s.teamAbbr.toUpperCase(), s]));

  // Parse Articles
  const articles = [];
  if (newsRes.status === 'fulfilled') {
    const injWords = ['injury', 'out ', 'sidelined', 'questionable', 'doubtful', 'ankle', 'knee', 'hamstring', 'shoulder', 'back', 'calf', 'surgery', 'mri', 'miss', 'return', 'rest'];
    const rotWords = ['starting', 'starter', 'lineup', 'bench', 'minutes', 'rotation', 'trade', 'signed', 'waived', 'suspended', 'coach'];
    const edgeWords = ['streak', 'points', 'triple-double', 'pace', 'defense', 'scoring', 'playoff', 'standings', 'mvp', 'record'];
    for (const a of (newsRes.value.articles || [])) {
      if (!a.headline) continue;
      const lower = `${a.headline} ${a.description || ''}`.toLowerCase();
      const cat = injWords.some((w) => lower.includes(w)) ? 'INJURY_UPDATE'
        : rotWords.some((w) => lower.includes(w)) ? 'LINEUP_ROTATION'
        : edgeWords.some((w) => lower.includes(w)) ? 'PICK_EDGE' : 'LEAGUE_HEADLINE';
      const note = cat === 'INJURY_UPDATE'
        ? (lower.includes('out') || lower.includes('miss') || lower.includes('sidelined')
          ? 'Pick Edge: Absence opens primary usage & rebounds for active teammates.'
          : 'Pick Edge: Returning rotation player can trim bench usage — check minutes limits.')
        : cat === 'LINEUP_ROTATION'
          ? 'Pick Edge: Rotation/minute shifts directly impact role-player Rebounds, Assists & Alt Points.'
          : cat === 'PICK_EDGE'
            ? 'Pick Edge: Team pace & form trend supports correlated game totals and primary creator props.'
            : 'League Context: Track matchup intensity and coaching adjustments this week.';
      articles.push({
        id: a.id || a.headline,
        headline: a.headline,
        summary: a.description || '',
        publishedDisplay: a.published ? new Date(a.published).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) : 'Recent',
        category: cat,
        pickImpactNote: note,
        articleUrl: a.links?.web?.href || null,
        teamTags: (a.categories || []).filter((c) => c.type === 'team' && c.description).map((c) => c.description).slice(0, 3)
      });
    }
  }

  // Parse Weekly Injuries + Beneficiaries + Expected Active Stars
  const nbaProps = activeProps.filter((p) => p.league === 'NBA');
  const propsByTeam = new Map();
  nbaProps.forEach((p) => {
    const k = p.teamAbbr.toUpperCase();
    if (!propsByTeam.has(k)) propsByTeam.set(k, []);
    propsByTeam.get(k).push(p);
  });

  const weeklyStatuses = [];
  const injuredNames = new Set();
  if (injRes.status === 'fulfilled') {
    for (const block of (injRes.value.injuries || [])) {
      const tName = block.team?.displayName || block.displayName || '';
      const lowerTeam = tName.toLowerCase();
      let abbr = block.team?.abbreviation || Object.entries(NBA_TEAM_MAP).find(([k]) => lowerTeam.includes(k))?.[1] || tName.slice(0, 3).toUpperCase();
      const sched = schedByAbbr.get(abbr.toUpperCase());
      for (const inj of (block.injuries || [])) {
        const ath = inj.athlete;
        const name = ath?.displayName || ath?.fullName;
        if (!name) continue;
        injuredNames.add(name.toLowerCase());
        const statusLabel = (inj.status || inj.type?.description || 'Questionable').toUpperCase();
        const bucket = (statusLabel.includes('OUT') || statusLabel.includes('DOUBT') || statusLabel.includes('SUSP'))
          ? 'WONT_PLAY'
          : (statusLabel.includes('PROB') ? 'PLAYING_THIS_WEEK' : 'QUESTIONABLE');
        const beneficiaries = bucket === 'WONT_PLAY'
          ? (propsByTeam.get(abbr.toUpperCase()) || [])
              .filter((p) => p.playerName.toLowerCase() !== name.toLowerCase())
              .slice(0, 2)
              .map((p) => `${p.playerName} (${formatLine(p.lineValue)} ${p.statLabel})`)
          : [];
        weeklyStatuses.push({
          playerId: ath.id || name,
          playerName: name,
          teamAbbr: sched?.teamAbbr || abbr,
          position: ath.position?.abbreviation || '',
          headshotUrl: ath.headshot?.href || (ath.id ? `https://a.espncdn.com/i/headshots/nba/players/full/${ath.id}.png` : null),
          statusLabel,
          bucket,
          detail: inj.shortComment || inj.details?.detail || 'Listed on official NBA injury report',
          gamesThisWeek: sched?.gamesThisWeek || 0,
          nextMatchupDisplay: sched?.nextOpponentDisplay || 'Schedule TBD',
          isBackToBackTeam: sched?.hasBackToBack || false,
          beneficiaryTeammates: beneficiaries
        });
      }
    }
  }

  // Add active expected stars from posted props
  const seenActive = new Set();
  for (const p of nbaProps) {
    if (seenActive.has(p.playerId) || injuredNames.has(p.playerName.toLowerCase())) continue;
    seenActive.add(p.playerId);
    const sched = schedByAbbr.get(p.teamAbbr.toUpperCase());
    weeklyStatuses.push({
      playerId: p.playerId,
      playerName: p.playerName,
      teamAbbr: p.teamAbbr,
      position: p.position,
      headshotUrl: p.headshotUrl,
      statusLabel: 'EXPECTED ACTIVE',
      bucket: 'PLAYING_THIS_WEEK',
      detail: `Active in rotation • Proj ${p.projectedValue.toFixed(1)} vs ${formatLine(p.lineValue)} ${p.statLabel} (${p.recommendedSide})`,
      gamesThisWeek: sched?.gamesThisWeek || 1,
      nextMatchupDisplay: sched?.nextOpponentDisplay || p.matchupShort,
      isBackToBackTeam: sched?.hasBackToBack || false,
      beneficiaryTeammates: []
    });
    if (seenActive.size >= 16) break;
  }

  // Transactions
  const transactions = [];
  if (txRes.status === 'fulfilled') {
    for (const tx of (txRes.value.transactions || []).slice(0, 15)) {
      if (!tx.description) continue;
      transactions.push({
        teamAbbr: tx.team?.abbreviation || 'NBA',
        description: tx.description,
        dateDisplay: tx.date ? new Date(tx.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' }) : 'Recent'
      });
    }
  }

  // Standings
  const standings = [];
  if (standRes.status === 'fulfilled') {
    for (const conf of (standRes.value.children || [])) {
      const confName = (conf.shortName || conf.name || 'Conf').replace(' Conference', '');
      (conf.standings?.entries || []).forEach((entry, idx) => {
        const stats = {};
        (entry.stats || []).forEach((s) => {
          stats[s.name || s.abbreviation] = s.displayValue || s.value;
        });
        standings.push({
          conference: confName,
          seed: idx + 1,
          teamAbbr: entry.team?.abbreviation || 'TM',
          teamName: entry.team?.displayName || 'Team',
          wins: parseInt(stats.wins || '0', 10),
          losses: parseInt(stats.losses || '0', 10),
          streak: stats.streak || '-',
          lastTen: stats['Last Ten Games'] || stats.L10 || '-'
        });
      });
    }
  }

  return {
    articles,
    weeklyStatuses,
    schedules,
    standings,
    transactions,
    lastUpdatedMillis: Date.now()
  };
}
