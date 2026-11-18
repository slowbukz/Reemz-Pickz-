// Quantitative Sports Math, 25K Multi-Sim Copula Suite, and 10K Prop Monte Carlo Engine

export const LEAGUES = [
  { code: 'NBA', label: 'NBA', sport: 'basketball', league: 'nba', avgTotal: 226.5, marginStd: 12.2, totalStd: 18.5 },
  { code: 'NFL', label: 'NFL', sport: 'football', league: 'nfl', avgTotal: 45.5, marginStd: 13.8, totalStd: 14.2 },
  { code: 'NHL', label: 'NHL', sport: 'hockey', league: 'nhl', avgTotal: 6.0, marginStd: 2.1, totalStd: 2.3 },
  { code: 'MLB', label: 'MLB', sport: 'baseball', league: 'mlb', avgTotal: 8.5, marginStd: 3.2, totalStd: 3.6 },
  { code: 'EPL', label: 'EPL', sport: 'soccer', league: 'eng.1', avgTotal: 2.75, marginStd: 1.5, totalStd: 1.65 },
  { code: 'NCAAF', label: 'NCAAF', sport: 'football', league: 'college-football', avgTotal: 52.0, marginStd: 16.5, totalStd: 17.5 },
  { code: 'NCAAB', label: 'NCAAB', sport: 'basketball', league: 'mens-college-basketball', avgTotal: 144.0, marginStd: 11.8, totalStd: 16.0 }
];

export function getLeagueMeta(code) {
  return LEAGUES.find((l) => l.code === code) || LEAGUES[0];
}

export function clamp(val, min, max) {
  return Math.max(min, Math.min(max, val));
}

export const OddsMath = {
  americanToDecimal(american) {
    if (american >= 100) return 1 + american / 100;
    if (american <= -100) return 1 + 100 / Math.abs(american);
    return 1.91;
  },

  decimalToAmerican(decimal) {
    const safe = Math.max(1.01, decimal);
    if (safe >= 2.0) return Math.round((safe - 1) * 100);
    return Math.round(-100 / (safe - 1));
  },

  americanToImpliedProb(american) {
    return clamp(1 / this.americanToDecimal(american), 0.01, 0.99);
  },

  probToAmerican(prob) {
    const clamped = clamp(prob, 0.015, 0.985);
    return this.decimalToAmerican(1 / clamped);
  },

  formatOdds(american, format = 'AMERICAN') {
    if (format === 'DECIMAL') return this.americanToDecimal(american).toFixed(2);
    return american > 0 ? `+${american}` : `${american}`;
  },

  formatPercent(value, includeSign = false, decimals = 1) {
    const pct = value * 100;
    const str = `${pct.toFixed(decimals)}%`;
    return includeSign && pct > 0 ? `+${str}` : str;
  },

  devigTwoWay(probA, probB, method = 'POWER') {
    const pA = clamp(probA, 0.01, 0.99);
    const pB = clamp(probB, 0.01, 0.99);
    const sum = pA + pB;
    if (sum <= 1.0 || method === 'MULTIPLICATIVE') {
      return [pA / sum, pB / sum];
    }
    let low = 1.0;
    let high = 3.0;
    for (let i = 0; i < 24; i++) {
      const mid = (low + high) / 2;
      if (Math.pow(pA, mid) + Math.pow(pB, mid) > 1.0) low = mid;
      else high = mid;
    }
    const k = (low + high) / 2;
    const fairA = Math.pow(pA, k);
    const fairB = Math.pow(pB, k);
    const norm = fairA + fairB;
    return [fairA / norm, fairB / norm];
  },

  expectedValue(trueProb, decimalOdds) {
    return clamp(trueProb, 0.001, 0.999) * decimalOdds - 1.0;
  },

  quarterKellyPercent(trueProb, decimalOdds) {
    const b = Math.max(0.01, decimalOdds - 1.0);
    const p = clamp(trueProb, 0.01, 0.99);
    const fullKelly = (b * p - (1 - p)) / b;
    return Math.min(5.0, Math.max(0.0, fullKelly) * 0.25 * 100);
  },

  normalCdf(x) {
    const z = x / Math.SQRT2;
    const t = 1 / (1 + 0.3275911 * Math.abs(z));
    const poly = (((((1.061405429 * t - 1.453152027) * t + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t);
    const erf = 1 - poly * Math.exp(-z * z);
    return z >= 0 ? 0.5 * (1 + erf) : 0.5 * (1 - erf);
  },

  inverseNormalCdf(p) {
    const prob = clamp(p, 1e-6, 1 - 1e-6);
    const a = [-39.69683028665376, 220.9460984245205, -275.9285104469687, 138.357751867269, -30.66479806614716, 2.506628277459239];
    const b = [-54.47609879822406, 161.5858368580409, -155.6989798598866, 66.80131188771972, -13.28068155288572];
    const c = [-0.007784894002430293, -0.3223964580411365, -2.400758277161838, -2.549732539343734, 4.374664141464968, 2.938163982698783];
    const d = [0.007784695709041462, 0.3224671290700398, 2.445134137142996, 3.754408661907416];
    const pLow = 0.02425;
    if (prob < pLow) {
      const q = Math.sqrt(-2 * Math.log(prob));
      return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
        ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
    }
    if (prob <= 1 - pLow) {
      const q = prob - 0.5;
      const r = q * q;
      return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q /
        (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
    }
    const q = Math.sqrt(-2 * Math.log(1 - prob));
    return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) /
      ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
  },

  bivariateNormalJointProb(pA, pB, rho) {
    const clippedRho = clamp(rho, -0.92, 0.92);
    if (Math.abs(clippedRho) < 1e-4) return pA * pB;
    const cA = this.inverseNormalCdf(pA);
    const cB = this.inverseNormalCdf(pB);
    const steps = 64;
    const low = -5.5;
    if (cA <= low) return 0.0001;
    const h = (cA - low) / steps;
    const denom = Math.sqrt(1 - clippedRho * clippedRho);
    let sum = 0;
    for (let i = 0; i <= steps; i++) {
      const z = low + i * h;
      const weight = i === 0 || i === steps ? 0.5 : 1.0;
      const pdf = Math.exp(-0.5 * z * z) / Math.sqrt(2 * Math.PI);
      const cond = this.normalCdf((cB - clippedRho * z) / denom);
      sum += weight * pdf * cond;
    }
    return clamp(sum * h, 0.005, Math.min(pA, pB));
  }
};

function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0;
    a = (a + 0x6D2B79F5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export const QuantCorrelationEngine = {
  evaluatePairCorrelation(a, b) {
    if (a.id === b.id) return null;

    if (a.eventId === b.eventId) {
      if ((a.sideTag === 'HOME' && b.sideTag === 'AWAY') || (a.sideTag === 'AWAY' && b.sideTag === 'HOME')) return null;
      if ((a.sideTag === 'OVER' && b.sideTag === 'UNDER') || (a.sideTag === 'UNDER' && b.sideTag === 'OVER')) return null;

      const aIsTeamSide = a.sideTag === 'HOME' || a.sideTag === 'AWAY';
      const bIsTeamSide = b.sideTag === 'HOME' || b.sideTag === 'AWAY';
      const aPropMore = a.marketType === 'PLAYER_PROP_MORE';
      const bPropMore = b.marketType === 'PLAYER_PROP_MORE';
      const aPropLess = a.marketType === 'PLAYER_PROP_LESS';
      const bPropLess = b.marketType === 'PLAYER_PROP_LESS';
      const aIsProp = aPropMore || aPropLess;
      const bIsProp = bPropMore || bPropLess;

      if (aIsProp && bIsProp) {
        if (aPropMore && bPropMore && a.sideTag === b.sideTag) {
          return { rho: 0.34, thesis: 'Teammate offensive volume aligns positively in high-efficiency team scoring scripts.' };
        }
        if (aPropMore && bPropMore) {
          return { rho: 0.28, thesis: 'Competitive shootout pace boosts offensive usage for primary stars on both teams.' };
        }
        if (aPropLess && bPropLess) {
          return { rho: 0.26, thesis: 'Slow-tempo / defensive grind script suppresses box-score volume on both sides.' };
        }
        return null;
      }

      if (aIsProp || bIsProp) {
        const propLeg = aIsProp ? a : b;
        const otherLeg = aIsProp ? b : a;
        const propMore = propLeg.marketType === 'PLAYER_PROP_MORE';

        if (otherLeg.marketType === 'TOTAL_OVER' && propMore) {
          return { rho: 0.47, thesis: `High-scoring game total directly expands stat production for ${propLeg.selectionLabel.split(' MORE')[0]}.` };
        }
        if (otherLeg.marketType === 'TOTAL_UNDER' && !propMore) {
          return { rho: 0.45, thesis: `Low-scoring game total compresses pace and stat volume toward ${propLeg.selectionLabel}.` };
        }
        if (aIsTeamSide && bIsTeamSide) {
          const sameTeam = propLeg.sideTag === otherLeg.sideTag;
          if (sameTeam && propMore) {
            const rhoMap = { NFL: 0.58, NCAAF: 0.58, NBA: 0.50, NCAAB: 0.50, NHL: 0.52, MLB: 0.49, EPL: 0.55 };
            return {
              rho: rhoMap[propLeg.league] || 0.50,
              thesis: 'Primary contributor clearing MORE stat line strongly drives team win & margin probability.'
            };
          }
          if (!sameTeam && !propMore) {
            return { rho: 0.38, thesis: 'Opponent defensive dominance suppresses opposing star production while boosting margin.' };
          }
        }
        return null;
      }

      if (aIsTeamSide && bIsTeamSide && a.sideTag === b.sideTag) {
        const aTT = a.marketType === 'TEAM_TOTAL_OVER' || a.marketType === 'TEAM_TOTAL_UNDER';
        const bTT = b.marketType === 'TEAM_TOTAL_OVER' || b.marketType === 'TEAM_TOTAL_UNDER';
        if (!aTT && bTT && b.marketType === 'TEAM_TOTAL_OVER') {
          const rhoMap = { NFL: 0.59, NCAAF: 0.59, NBA: 0.52, NCAAB: 0.52, NHL: 0.56, MLB: 0.54, EPL: 0.61 };
          return { rho: rhoMap[a.league] || 0.52, thesis: `Team win/cover probability rises steeply when ${a.selectionLabel.split(' ')[0]} clears team scoring projection.` };
        }
        if (!bTT && aTT && a.marketType === 'TEAM_TOTAL_OVER') {
          const rhoMap = { NFL: 0.59, NCAAF: 0.59, NBA: 0.52, NCAAB: 0.52, NHL: 0.56, MLB: 0.54, EPL: 0.61 };
          return { rho: rhoMap[a.league] || 0.52, thesis: `Team scoring volume drives margin separation in ${a.league} win-script distributions.` };
        }
        return null;
      }

      if ((a.marketType === 'TEAM_TOTAL_OVER' && b.marketType === 'TOTAL_OVER') ||
          (b.marketType === 'TEAM_TOTAL_OVER' && a.marketType === 'TOTAL_OVER')) {
        return { rho: 0.66, thesis: 'Direct additive covariance: team total accounts for ~50-55% of full-game scoring variance.' };
      }

      const sideLeg = aIsTeamSide ? a : bIsTeamSide ? b : null;
      const totalLeg = (a.marketType === 'TOTAL_OVER' || a.marketType === 'TOTAL_UNDER') ? a
        : (b.marketType === 'TOTAL_OVER' || b.marketType === 'TOTAL_UNDER') ? b : null;
      if (sideLeg && totalLeg) {
        const isOver = totalLeg.marketType === 'TOTAL_OVER';
        if (sideLeg.isFavoriteSide && isOver) {
          const rhoMap = { EPL: 0.48, NHL: 0.37, NFL: 0.34, NCAAF: 0.34, NBA: 0.29, NCAAB: 0.29, MLB: 0.33 };
          return { rho: rhoMap[sideLeg.league] || 0.30, thesis: 'Favorite blowout / pace script lifts both favorite margin and full-game over.' };
        }
        if (!sideLeg.isFavoriteSide && !isOver) {
          const rhoMap = { EPL: 0.46, NFL: 0.39, NCAAF: 0.39, NHL: 0.41, NBA: 0.35, NCAAB: 0.35, MLB: 0.38 };
          return { rho: rhoMap[sideLeg.league] || 0.36, thesis: 'Low-scoring variance compression boosts underdog cover rates.' };
        }
        if (!sideLeg.isFavoriteSide && isOver) {
          return { rho: 0.24, thesis: 'High-variance shootout environment widens tail outcomes for live underdog scoring.' };
        }
        return { rho: 0.14, thesis: 'Clock-control favorite script modestly aligns with game under.' };
      }
      return null;
    }

    if (a.league === b.league) {
      if (a.marketType === 'TOTAL_OVER' && b.marketType === 'TOTAL_OVER') {
        return { rho: 0.16, thesis: `Shared ${a.league} slate pace/officiating environment creates mild positive alignment.` };
      }
      if (a.marketType === 'TOTAL_UNDER' && b.marketType === 'TOTAL_UNDER') {
        return { rho: 0.18, thesis: `Shared ${a.league} slate tempo/defensive regime mildly correlates game unders.` };
      }
      if (a.isFavoriteSide && b.isFavoriteSide && a.fairTrueProb >= 0.62 && b.fairTrueProb >= 0.62) {
        return { rho: 0.12, thesis: `High-conviction ${a.league} chalk favorites pair cleanly with minimal variance drag.` };
      }
    }
    return null;
  },

  choleskyOrDamped(matrix) {
    const n = matrix.length;
    let scale = 1.0;
    for (let attempt = 0; attempt < 6; attempt++) {
      const m = matrix.map((row, i) => row.map((v, j) => (i === j ? 1.0 : v * scale)));
      const l = Array.from({ length: n }, () => new Array(n).fill(0));
      let ok = true;
      for (let i = 0; i < n; i++) {
        for (let j = 0; j <= i; j++) {
          let sum = 0;
          for (let k = 0; k < j; k++) sum += l[i][k] * l[j][k];
          if (i === j) {
            const diag = m[i][i] - sum;
            if (diag <= 1e-6) { ok = false; break; }
            l[i][j] = Math.sqrt(diag);
          } else {
            l[i][j] = (m[i][j] - sum) / l[j][j];
          }
        }
        if (!ok) break;
      }
      if (ok) return l;
      scale *= 0.8;
    }
    return Array.from({ length: n }, (_, i) => Array.from({ length: n }, (__, j) => (i === j ? 1 : 0)));
  },

  computeMultiSimSuite(legs, pairwiseRhos) {
    if (!legs.length) return { consensusProb: 0, gaussianProb: 0, studentTProb: 0, paceStressProb: 0, simCount: 0, agreementScore: 1 };
    if (legs.length === 1) {
      const p = legs[0].fairTrueProb;
      return { consensusProb: p, gaussianProb: p, studentTProb: p, paceStressProb: p, simCount: 25000, agreementScore: 0.99 };
    }

    const n = legs.length;
    const corr = Array.from({ length: n }, (_, i) =>
      Array.from({ length: n }, (__, j) => {
        if (i === j) return 1.0;
        const key = i < j ? `${i}_${j}` : `${j}_${i}`;
        return clamp(pairwiseRhos[key] || 0, -0.85, 0.85);
      })
    );
    const l = this.choleskyOrDamped(corr);
    const thresholds = legs.map((leg) => OddsMath.inverseNormalCdf(leg.fairTrueProb));
    const minLegProb = Math.min(...legs.map((x) => x.fairTrueProb));

    // 1. Gaussian Copula (10K or exact bivariate)
    let gaussianProb;
    if (n === 2) {
      gaussianProb = OddsMath.bivariateNormalJointProb(legs[0].fairTrueProb, legs[1].fairTrueProb, pairwiseRhos['0_1'] || 0);
    } else {
      const rng = mulberry32(422026);
      const samples = 10000;
      let hits = 0;
      const z = new Array(n);
      for (let s = 0; s < samples; s++) {
        for (let k = 0; k < n; k++) {
          const u1 = Math.max(1e-9, rng());
          z[k] = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * rng());
        }
        let allHit = true;
        for (let i = 0; i < n; i++) {
          let sum = 0;
          for (let j = 0; j <= i; j++) sum += l[i][j] * z[j];
          if (sum > thresholds[i]) { allHit = false; break; }
        }
        if (allHit) hits++;
      }
      gaussianProb = clamp(hits / samples, 0.005, minLegProb);
    }

    // 2. Student-t Heavy Tail (df = 5, 10K)
    const df = 5;
    const tThresholds = thresholds.map((z) => {
      const z2 = z * z;
      const z3 = z2 * z;
      const z5 = z3 * z2;
      return z + (z3 + z) / (4 * df) + (5 * z5 + 16 * z3 + 3 * z) / (96 * df * df);
    });
    const rngT = mulberry32(882026);
    let tHits = 0;
    const zT = new Array(n);
    for (let s = 0; s < 10000; s++) {
      for (let k = 0; k < n; k++) {
        const u1 = Math.max(1e-9, rngT());
        zT[k] = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * rngT());
      }
      let chi2 = 0;
      for (let c = 0; c < df; c++) {
        const g = Math.sqrt(-2 * Math.log(Math.max(1e-9, rngT()))) * Math.cos(2 * Math.PI * rngT());
        chi2 += g * g;
      }
      const scale = Math.sqrt(df / Math.max(0.05, chi2));
      let allHit = true;
      for (let i = 0; i < n; i++) {
        let sum = 0;
        for (let j = 0; j <= i; j++) sum += l[i][j] * zT[j];
        if (sum * scale > tThresholds[i]) { allHit = false; break; }
      }
      if (allHit) tHits++;
    }
    const studentTProb = clamp(tHits / 10000, 0.005, minLegProb);

    // 3. Pace Regime Stress (5K)
    const rngP = mulberry32(1372026);
    let pHits = 0;
    const zP = new Array(n);
    const rw = 0.22;
    const resW = Math.sqrt(1 - rw * rw);
    for (let s = 0; s < 5000; s++) {
      const shock = Math.sqrt(-2 * Math.log(Math.max(1e-9, rngP()))) * Math.cos(2 * Math.PI * rngP());
      for (let k = 0; k < n; k++) {
        zP[k] = Math.sqrt(-2 * Math.log(Math.max(1e-9, rngP()))) * Math.cos(2 * Math.PI * rngP());
      }
      let allHit = true;
      for (let i = 0; i < n; i++) {
        let sum = 0;
        for (let j = 0; j <= i; j++) sum += l[i][j] * zP[j];
        if (resW * sum + rw * shock > thresholds[i]) { allHit = false; break; }
      }
      if (allHit) pHits++;
    }
    const paceStressProb = clamp(pHits / 5000, 0.005, minLegProb);

    const consensusProb = clamp(gaussianProb * 0.45 + studentTProb * 0.35 + paceStressProb * 0.20, 0.005, minLegProb);
    const spread = Math.max(0, Math.max(gaussianProb, studentTProb, paceStressProb) - Math.min(gaussianProb, studentTProb, paceStressProb));
    const agreementScore = clamp(1 - spread / Math.max(0.08, consensusProb), 0.55, 0.99);

    return { consensusProb, gaussianProb, studentTProb, paceStressProb, simCount: 25000, agreementScore };
  },

  assembleParlay(legs, links, pairwiseRhos, savedIds = new Set()) {
    const indepProb = clamp(legs.reduce((acc, l) => acc * l.fairTrueProb, 1.0), 0.005, 0.98);
    const suite = this.computeMultiSimSuite(legs, pairwiseRhos);
    const corrProb = suite.consensusProb;
    const probLift = corrProb - indepProb;
    const rawDec = legs.reduce((acc, l) => acc * l.offeredDecimalOdds, 1.0);
    const isSameGame = new Set(legs.map((l) => l.eventId)).size === 1;
    const avgRho = links.length ? links.reduce((a, l) => a + l.rho, 0) / links.length : 0;
    const haircut = isSameGame && avgRho > 0.25 ? 1 - avgRho * 0.09 : 1.0;
    const marketDecimal = Math.max(1.15, 1 + (rawDec - 1) * haircut);
    const marketAmerican = OddsMath.decimalToAmerican(marketDecimal);
    const corrEv = OddsMath.expectedValue(corrProb, marketDecimal);
    const indepEv = OddsMath.expectedValue(indepProb, marketDecimal);
    const kelly = OddsMath.quarterKellyPercent(corrProb, marketDecimal);
    const quantScore = corrProb * 50 + avgRho * 20 + clamp(corrEv, -0.1, 0.45) * 22 + suite.agreementScore * 8;

    let sharpGrade = 'B+ MULTI-SIM +EV';
    if (corrEv >= 0.06 && suite.agreementScore >= 0.88 && avgRho >= 0.22) sharpGrade = 'A+ QUANT LOCK';
    else if (corrEv >= 0.02 && suite.agreementScore >= 0.80) sharpGrade = 'A SHARP CONSENSUS';

    const id = legs.map((l) => l.id).sort().join('__');
    const primaryThesis = links.length
      ? [...links].sort((a, b) => b.rho - a.rho)[0].thesis
      : 'Multi-sim correlated alignment verified across Gaussian, Student-t & Pace stress trials.';

    return {
      id,
      rank: 0,
      legs,
      correlationLinks: links,
      avgRho,
      independentProb: indepProb,
      correlatedProb: corrProb,
      probabilityLift: probLift,
      marketParlayDecimalOdds: marketDecimal,
      marketParlayAmericanOdds: marketAmerican,
      correlatedFairAmericanOdds: OddsMath.probToAmerican(corrProb),
      independentEvPercent: indepEv,
      correlatedEvPercent: corrEv,
      quarterKellyPercent: kelly,
      quantScore,
      primaryThesis,
      isSameGame,
      isSaved: savedIds.has(id),
      gaussianCopulaProb: suite.gaussianProb,
      studentTCopulaProb: suite.studentTProb,
      paceStressSimProb: suite.paceStressProb,
      simConsensusCount: suite.simCount,
      simAgreementScore: suite.agreementScore,
      sharpGrade
    };
  },

  buildTopCorrelatedParlays(legs, prefs, savedIds = new Set()) {
    const enabled = new Set(prefs.enabledLeagues);
    const filtered = legs
      .filter((l) => enabled.has(l.league))
      .sort((a, b) => (b.fairTrueProb * 0.65 + b.evPercent * 1.5) - (a.fairTrueProb * 0.65 + a.evPercent * 1.5))
      .slice(0, 80);

    const seeds = [];
    const want3 = prefs.parlaySize === 'THREE_LEGS' || prefs.parlaySize === 'ALL';
    const want2 = prefs.parlaySize === 'TWO_LEGS' || prefs.parlaySize === 'ALL';

    if (want2) {
      for (let i = 0; i < filtered.length; i++) {
        for (let j = i + 1; j < filtered.length; j++) {
          const a = filtered[i], b = filtered[j];
          const ev = this.evaluatePairCorrelation(a, b);
          if (!ev || ev.rho < prefs.minRho) continue;
          const proxyProb = OddsMath.bivariateNormalJointProb(a.fairTrueProb, b.fairTrueProb, ev.rho);
          const proxyEv = OddsMath.expectedValue(proxyProb, a.offeredDecimalOdds * b.offeredDecimalOdds);
          if (proxyProb >= prefs.minProb && proxyEv > -0.02) {
            seeds.push({
              legs: [a, b],
              links: [{ fromLabel: a.selectionLabel, toLabel: b.selectionLabel, rho: ev.rho, thesis: ev.thesis }],
              pairwiseRhos: { '0_1': ev.rho },
              proxyScore: proxyProb * 55 + ev.rho * 22 + clamp(proxyEv, -0.1, 0.45) * 23
            });
          }
        }
      }
    }

    if (want3) {
      const fiftyPlus = filtered.filter((l) => l.fairTrueProb >= 0.50);
      const pool = (fiftyPlus.length >= 16 ? fiftyPlus : filtered.filter((l) => l.fairTrueProb >= 0.44)).slice(0, 42);
      for (let i = 0; i < pool.length; i++) {
        for (let j = i + 1; j < pool.length; j++) {
          const ab = this.evaluatePairCorrelation(pool[i], pool[j]);
          if (!ab || ab.rho < 0.16) continue;
          for (let k = j + 1; k < pool.length; k++) {
            const a = pool[i], b = pool[j], c = pool[k];
            const ac = this.evaluatePairCorrelation(a, c);
            const bc = this.evaluatePairCorrelation(b, c);
            if ((a.eventId === c.eventId && !ac) || (b.eventId === c.eventId && !bc)) continue;
            const rhoAB = ab.rho;
            const rhoAC = ac ? ac.rho : 0.05;
            const rhoBC = bc ? bc.rho : 0.05;
            const avgRho = (rhoAB + rhoAC + rhoBC) / 3;
            if (avgRho < prefs.minRho) continue;
            const abProb = OddsMath.bivariateNormalJointProb(a.fairTrueProb, b.fairTrueProb, rhoAB);
            const proxyProb = clamp(abProb * c.fairTrueProb * (1 + 0.18 * (rhoAC + rhoBC)), 0.02, Math.min(abProb, c.fairTrueProb));
            const proxyEv = OddsMath.expectedValue(proxyProb, a.offeredDecimalOdds * b.offeredDecimalOdds * c.offeredDecimalOdds);
            if (proxyProb >= Math.max(0.22, prefs.minProb - 0.08) && proxyEv > -0.02) {
              const links = [{ fromLabel: a.selectionLabel, toLabel: b.selectionLabel, rho: rhoAB, thesis: ab.thesis }];
              if (ac) links.push({ fromLabel: a.selectionLabel, toLabel: c.selectionLabel, rho: rhoAC, thesis: ac.thesis });
              if (bc) links.push({ fromLabel: b.selectionLabel, toLabel: c.selectionLabel, rho: rhoBC, thesis: bc.thesis });
              seeds.push({
                legs: [a, b, c],
                links,
                pairwiseRhos: { '0_1': rhoAB, '0_2': rhoAC, '1_2': rhoBC },
                proxyScore: proxyProb * 55 + avgRho * 22 + clamp(proxyEv, -0.1, 0.45) * 23
              });
            }
          }
        }
      }
    }

    // Fallback relaxation for 3-pick parlays
    if (seeds.length < 3 && filtered.length >= 3) {
      const topPool = filtered.slice(0, 18);
      for (let i = 0; i < topPool.length && seeds.length < 10; i++) {
        for (let j = i + 1; j < topPool.length && seeds.length < 10; j++) {
          const ab = this.evaluatePairCorrelation(topPool[i], topPool[j]);
          if (topPool[i].eventId === topPool[j].eventId && !ab) continue;
          for (let k = j + 1; k < topPool.length && seeds.length < 10; k++) {
            const a = topPool[i], b = topPool[j], c = topPool[k];
            const ac = this.evaluatePairCorrelation(a, c);
            const bc = this.evaluatePairCorrelation(b, c);
            if ((a.eventId === c.eventId && !ac) || (b.eventId === c.eventId && !bc)) continue;
            const rhoAB = ab ? ab.rho : 0;
            const rhoAC = ac ? ac.rho : 0;
            const rhoBC = bc ? bc.rho : 0;
            const links = [];
            if (ab) links.push({ fromLabel: a.selectionLabel, toLabel: b.selectionLabel, rho: rhoAB, thesis: ab.thesis });
            if (ac) links.push({ fromLabel: a.selectionLabel, toLabel: c.selectionLabel, rho: rhoAC, thesis: ac.thesis });
            if (bc) links.push({ fromLabel: b.selectionLabel, toLabel: c.selectionLabel, rho: rhoBC, thesis: bc.thesis });
            const proxyProb = Math.max(0.08, a.fairTrueProb * b.fairTrueProb * c.fairTrueProb);
            seeds.push({
              legs: [a, b, c],
              links,
              pairwiseRhos: { '0_1': rhoAB, '0_2': rhoAC, '1_2': rhoBC },
              proxyScore: proxyProb * 55 + ((rhoAB + rhoAC + rhoBC) / 3) * 22
            });
          }
        }
      }
    }

    const seen = new Set();
    const deduped = [];
    for (const seed of seeds.sort((a, b) => b.proxyScore - a.proxyScore)) {
      const key = seed.legs.map((l) => l.id).sort().join('|');
      if (!seen.has(key)) {
        seen.add(key);
        deduped.push(seed);
        if (deduped.length >= 20) break;
      }
    }

    const simulated = deduped
      .map((s) => this.assembleParlay(s.legs, s.links, s.pairwiseRhos, savedIds))
      .sort((a, b) => b.quantScore - a.quantScore || b.correlatedProb - a.correlatedProb)
      .slice(0, 15);

    return simulated.map((p, idx) => ({ ...p, rank: idx + 1 }));
  }
};

export const SIM_SCENARIOS = [
  { key: 'BASELINE', label: 'Baseline', mult: 1.0 },
  { key: 'SHOOTOUT', label: 'High-Pace', mult: 1.11 },
  { key: 'BLOWOUT', label: 'Blowout Risk', mult: 0.87 },
  { key: 'STAR_OUT', label: 'Usage Boost', mult: 1.16 }
];

export const PlayerPropSimEngine = {
  buildAltLinePresets(baseLine, statLabel) {
    const lower = statLabel.toLowerCase();
    const step = (lower.includes('yds') || lower.includes('yard')) ? 15 : baseLine >= 18 ? 2 : baseLine >= 8 ? 1 : 0.5;
    const set = new Set();
    for (const m of [-2, -1, 0, 1, 2]) {
      const raw = Math.max(0.5, baseLine + m * step);
      let r = Math.round(raw * 2) / 2;
      if (r % 1 === 0) r = Math.max(0.5, r - 0.5);
      set.add(r);
    }
    return [...set].sort((a, b) => a - b);
  },

  stepIncrementForStat(statLabel) {
    const lower = statLabel.toLowerCase();
    return (lower.includes('yds') || lower.includes('yard')) ? 5.0 : 0.5;
  },

  simulatePropOutcomes({ statLabel, historicalValues, lineValue, upcomingGame, playerTeamAbbr, matchupInjuries, scenarioKey = 'BASELINE', runId = 0 }) {
    const clean = historicalValues.filter((v) => v >= 0);
    const baseMean = clean.length
      ? clean.slice(-Math.min(5, clean.length)).reduce((a, b) => a + b, 0) / Math.min(5, clean.length) * 0.65 +
        (clean.reduce((a, b) => a + b, 0) / clean.length) * 0.35
      : lineValue + 0.5;

    let sampleStd = Math.max(0.85, baseMean * 0.28);
    if (clean.length >= 2) {
      const m = clean.reduce((a, b) => a + b, 0) / clean.length;
      const variance = clean.reduce((a, b) => a + (b - m) * (b - m), 0) / (clean.length - 1);
      sampleStd = Math.max(Math.max(0.65, baseMean * 0.16), Math.sqrt(variance));
    }

    let paceFactor = 1.0;
    if (upcomingGame?.marketSnapshot?.totalLine) {
      const lm = getLeagueMeta(upcomingGame.league);
      paceFactor = clamp(1 + (upcomingGame.marketSnapshot.totalLine / lm.avgTotal - 1) * 0.65, 0.88, 1.15);
    }

    const outCount = (matchupInjuries || []).filter(
      (i) => i.teamAbbr.toUpperCase() === (playerTeamAbbr || '').toUpperCase() && (i.severity === 'OUT' || i.severity === 'DOUBTFUL')
    ).length;
    const injuryFactor = outCount > 0 && scenarioKey === 'BASELINE' ? 1 + Math.min(outCount, 2) * 0.035 : 1.0;
    const scen = SIM_SCENARIOS.find((s) => s.key === scenarioKey) || SIM_SCENARIOS[0];
    const totalMult = clamp(paceFactor * injuryFactor * scen.mult, 0.75, 1.32);

    const projMean = Math.max(0.1, baseMean * totalMult);
    const adjStd = Math.max(0.55, sampleStd * Math.sqrt(totalMult));
    const trials = 10000;
    const discrete = !statLabel.toLowerCase().includes('yard') && !statLabel.toLowerCase().includes('yds');

    let seed = Math.round(lineValue * 100) ^ (runId * 104729) ^ (scen.key.charCodeAt(0) * 7919);
    for (let i = 0; i < statLabel.length; i++) seed = (seed * 31 + statLabel.charCodeAt(i)) | 0;
    const rng = mulberry32(seed);

    const draws = new Float64Array(trials);
    let moreHits = 0;
    for (let i = 0; i < trials; i++) {
      const u1 = Math.max(1e-9, rng());
      const z = Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * rng());
      const tailScale = rng() < 0.12 ? 1.28 : 0.96;
      let val = Math.max(0, projMean + z * adjStd * tailScale);
      if (discrete) val = Math.round(val);
      draws[i] = val;
      if (val > lineValue) moreHits++;
    }

    draws.sort();
    const sum = draws.reduce((a, b) => a + b, 0);
    const moreProb = clamp(moreHits / trials, 0.01, 0.99);
    const lessProb = clamp(1 - moreProb, 0.01, 0.99);

    // Build 9-bucket histogram
    const p02 = draws[Math.floor(trials * 0.02)];
    const p98 = draws[Math.floor(trials * 0.98)];
    const minBound = Math.max(0, Math.min(p02, lineValue * 0.6));
    const maxBound = Math.max(minBound + 2, Math.max(p98, lineValue * 1.4));
    const step = Math.max(discrete ? 1 : 0.5, (maxBound - minBound) / 9);
    const counts = new Array(9).fill(0);
    for (let i = 0; i < trials; i++) {
      const idx = clamp(Math.floor((draws[i] - minBound) / step), 0, 8);
      counts[idx]++;
    }
    const maxCount = Math.max(1, ...counts);
    const histogram = counts.map((c, i) => {
      const low = minBound + i * step;
      const mid = low + step / 2;
      return {
        label: step >= 4 ? `${Math.floor(low)}` : step >= 1 ? `${Math.round(low)}` : mid.toFixed(1),
        fraction: c / maxCount,
        isMoreSide: mid > lineValue
      };
    });

    return {
      statLabel,
      lineValue,
      scenarioKey,
      trialCount: trials,
      simMean: sum / trials,
      simMedian: draws[trials / 2],
      floorP10: draws[Math.floor(trials * 0.10)],
      ceilingP90: draws[Math.floor(trials * 0.90)],
      moreProb,
      lessProb,
      fairMoreOdds: OddsMath.probToAmerican(moreProb),
      fairLessOdds: OddsMath.probToAmerican(lessProb),
      paceMultiplierApplied: totalMult,
      histogram
    };
  }
};
