<script lang="ts">
  import type { Difficulty } from '../api/dto';
  import { t, bandName } from '../i18n';
  let { report }: { report: Difficulty } = $props();
</script>

<dl class="debug-grid">
  <div>
    <dt>{$t('settings.difficulty')}</dt>
    <dd>{bandName($t, report.band)}</dd>
  </div>
  <div>
    <dt>{$t('debug.model')}</dt>
    <dd>{report.modelVersion}</dd>
  </div>
  <div>
    <dt>{$t('debug.score')}</dt>
    <dd>{report.score}</dd>
  </div>
  <div>
    <dt>{$t('debug.deductions')}</dt>
    <dd>{report.totalDeductionCount}</dd>
  </div>
  <div>
    <dt>{$t('debug.hardestTier')}</dt>
    <dd>{report.hardestRuleTier}</dd>
  </div>
  <div>
    <dt>{$t('debug.proofDepth')}</dt>
    <dd>{report.maximumProofDepth}</dd>
  </div>
  <div>
    <dt>{$t('debug.hypotheses')}</dt>
    <dd>{report.hypothesisSteps}</dd>
  </div>
  <div>
    <dt>{$t('debug.weightedCost')}</dt>
    <dd>{report.weightedRuleCost}</dd>
  </div>
  <div>
    <dt>{$t('debug.averageAvailable')}</dt>
    <dd>{report.averageAvailableDeductions.toFixed(2)}</dd>
  </div>
  <div>
    <dt>{$t('debug.minimumAvailable')}</dt>
    <dd>{report.minimumAvailableDeductions}</dd>
  </div>
</dl>

{#if report.profile}
  <details class="profile-details">
    <summary>{$t('debug.profile')}</summary>
    <p>{$t('debug.profileNote')}</p>
    <dl class="debug-grid">
      <div>
        <dt>{$t('debug.events')}</dt>
        <dd>{report.profile.eventCount}</dd>
      </div>
      <div>
        <dt>{$t('debug.information')}</dt>
        <dd>{report.profile.totalInformation.toFixed(2)}</dd>
      </div>
      <div>
        <dt>{$t('debug.effort')} P50 / P75 / P90</dt>
        <dd>
          {report.profile.medianEffort.toFixed(1)} / {report.profile.p75Effort.toFixed(1)} / {report.profile.p90Effort.toFixed(
            1,
          )}
        </dd>
      </div>
      <div>
        <dt>{$t('debug.nontrivial')}</dt>
        <dd>{Math.round(report.profile.nontrivialProgressFraction * 100)}%</dd>
      </div>
      <div>
        <dt>{$t('debug.trivialStretch')}</dt>
        <dd>{Math.round(report.profile.longestTrivialProgressFraction * 100)}%</dd>
      </div>
      <div>
        <dt>{$t('debug.spikeShare')}</dt>
        <dd>{Math.round(report.profile.largestEventEffortShare * 100)}%</dd>
      </div>
      {#if report.profile.remainingCheapCoreFraction !== undefined}
        <div>
          <dt>{$t('debug.cheapOpening')}</dt>
          <dd>{Math.round((report.profile.initialCheapClosureProgressFraction ?? 0) * 100)}%</dd>
        </div>
        <div>
          <dt>{$t('debug.cheapCore')}</dt>
          <dd>{Math.round(report.profile.remainingCheapCoreFraction * 100)}%</dd>
        </div>
        <div>
          <dt>{$t('debug.unlocks')}</dt>
          <dd>{report.profile.nonCheapUnlocks}</dd>
        </div>
        <div>
          <dt>{$t('debug.cascadeShare')}</dt>
          <dd>{Math.round((report.profile.largestCheapCascadeShare ?? 0) * 100)}%</dd>
        </div>
      {/if}
      <div>
        <dt>{$t('debug.regions')}</dt>
        <dd>{report.profile.substantialEventsByRegion.join(' · ')}</dd>
      </div>
    </dl>
    <div class="profile-table">
      <table>
        <thead
          ><tr
            ><th>{$t('debug.progress')}</th><th>{$t('debug.effort')}</th><th>{$t('debug.rule')}</th
            ><th>{$t('debug.cascade')}</th><th>{$t('debug.rootBits')}</th></tr
          ></thead
        >
        <tbody
          >{#each report.profile.timeline as event}<tr
              ><td
                >{Math.round(event.progressStart * 100)}–{Math.round(event.progressEnd * 100)}%</td
              ><td>{event.effort.toFixed(1)}</td><td><code>{event.ruleId}</code></td><td
                >{event.cascadeFacts}</td
              ><td
                >{event.rootInformation?.toFixed(2) ?? '—'} / {event.cascadeInformation?.toFixed(
                  2,
                ) ?? '—'}</td
              ></tr
            >{/each}</tbody
        >
      </table>
    </div>
  </details>
{/if}
