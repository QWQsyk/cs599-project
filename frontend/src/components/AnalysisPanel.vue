<template>
  <aside class="analysis-panel">
    <div class="pane-header">
      <strong>案件分析</strong>
      <el-tooltip content="生成报告">
        <el-button :icon="DocumentChecked" size="small" @click="$emit('report')" />
      </el-tooltip>
    </div>
    <div v-if="analysis" class="analysis-content">
      <div class="analysis-summary">
        <el-tag type="success">{{ analysis.caseType }}</el-tag>
        <el-tag v-if="analysis.subType" type="info">{{ analysis.subType }}</el-tag>
        <span v-if="typeof analysis.completenessScore === 'number'">完整度 {{ Math.round(analysis.completenessScore * 100) }}%</span>
        <span v-if="analysis.conclusionLevel">{{ conclusionLabel(analysis.conclusionLevel) }}</span>
      </div>
      <section v-if="readArray(analysis.claimGoalsJson).length">
        <h3>诉求目标</h3>
        <ul>
          <li v-for="item in readArray(analysis.claimGoalsJson)" :key="item">{{ item }}</li>
        </ul>
      </section>
      <section v-if="readClaimReadiness(analysis.factsJson).length">
        <h3>请求项成熟度</h3>
        <div class="readiness-list">
          <article v-for="item in readClaimReadiness(analysis.factsJson)" :key="item.claim" class="readiness-item">
            <strong>{{ item.claim }}</strong>
            <el-tag size="small" :type="readinessType(item.status)">{{ readinessLabel(item.status) }}</el-tag>
            <p>{{ item.reason }}</p>
            <small>{{ item.nextEvidence }}</small>
          </article>
        </div>
      </section>
      <section>
        <h3>缺失信息</h3>
        <ul>
          <li v-for="item in readArray(analysis.missingQuestionsJson)" :key="item">{{ item }}</li>
          <li v-if="!readArray(analysis.missingQuestionsJson).length">暂无</li>
        </ul>
      </section>
      <section>
        <h3>争点分析</h3>
        <div v-if="readIssues(analysis.issuesJson).length" class="issue-list">
          <article v-for="item in readIssues(analysis.issuesJson)" :key="item.issue" class="issue-item">
            <strong>{{ item.issue }}</strong>
            <p>{{ item.application }} {{ item.conclusion }}</p>
            <small>证据强度：{{ strengthLabel(item.evidenceStrength) }}</small>
            <small>已知：{{ item.knownFacts?.length ? item.knownFacts.join('、') : '暂无' }}</small>
            <small>缺失：{{ item.missingFacts?.length ? item.missingFacts.join('、') : '暂无' }}</small>
            <ul v-if="item.evidenceGaps?.length" class="gap-list">
              <li v-for="gap in item.evidenceGaps" :key="`${item.issue}-${gap.fact}`">
                <el-tag size="small" :type="gap.priority === 'high' ? 'danger' : 'warning'">{{ gap.priority === 'high' ? '高优先' : '中优先' }}</el-tag>
                <span>{{ gap.fact }}：{{ gap.suggestion }}</span>
              </li>
            </ul>
          </article>
        </div>
        <ul v-else>
          <li v-for="item in readArray(analysis.liabilityJson)" :key="item">{{ item }}</li>
        </ul>
      </section>
      <section>
        <h3>证据清单</h3>
        <ul>
          <li v-for="item in readArray(analysis.evidenceJson)" :key="item">{{ item }}</li>
        </ul>
      </section>
      <section>
        <h3>证据强度</h3>
        <div v-if="readEvidence(analysis.evidenceAssessmentsJson).length" class="evidence-grid">
          <div v-for="item in readEvidence(analysis.evidenceAssessmentsJson)" :key="item.name" class="evidence-item">
            <strong>{{ item.name }}</strong>
            <span>{{ item.provided ? '已出现线索' : '建议补充' }} / {{ strengthLabel(item.strength) }}</span>
          </div>
        </div>
      </section>
      <section>
        <h3>维权路径</h3>
        <ol>
          <li v-for="item in readArray(analysis.actionPathJson)" :key="item">{{ item }}</li>
        </ol>
      </section>
      <section>
        <h3>风险提示</h3>
        <ul>
          <li v-for="item in readArray(analysis.risksJson)" :key="item">{{ item }}</li>
        </ul>
      </section>
      <section v-if="readCitations(analysis.citationsJson).length">
        <h3>参考依据</h3>
        <ul>
          <li v-for="item in readCitations(analysis.citationsJson)" :key="`${item.type}-${item.title}`">
            <a :href="item.sourceUrl" target="_blank" rel="noreferrer">{{ item.title }}</a>
          </li>
        </ul>
      </section>
    </div>
    <div v-else class="empty-panel">发送案件描述后显示结构化分析</div>
  </aside>
</template>

<script setup lang="ts">
import { DocumentChecked } from '@element-plus/icons-vue'
import type { CaseAnalysis } from '../api/types'

defineProps<{ analysis: CaseAnalysis | null }>()
defineEmits<{ report: [] }>()

interface IssueItem {
  issue: string
  knownFacts: string[]
  missingFacts: string[]
  evidenceGaps?: EvidenceGap[]
  evidenceStrength: string
  application: string
  conclusion: string
}

interface EvidenceGap {
  fact: string
  priority: string
  suggestion: string
}

interface EvidenceItem {
  name: string
  purpose: string
  strength: string
  provided: boolean
}

interface CitationItem {
  type: string
  title: string
  sourceUrl: string
}

interface ClaimReadinessItem {
  claim: string
  status: string
  reason: string
  nextEvidence: string
}

function readArray(raw: string): string[] {
  try {
    const value = JSON.parse(raw)
    return Array.isArray(value) ? value.map(String) : []
  } catch {
    return []
  }
}

function readIssues(raw?: string): IssueItem[] {
  try {
    const value = JSON.parse(raw || '[]')
    return Array.isArray(value) ? value as IssueItem[] : []
  } catch {
    return []
  }
}

function readEvidence(raw?: string): EvidenceItem[] {
  try {
    const value = JSON.parse(raw || '[]')
    return Array.isArray(value) ? value as EvidenceItem[] : []
  } catch {
    return []
  }
}

function readCitations(raw?: string): CitationItem[] {
  try {
    const value = JSON.parse(raw || '[]')
    return Array.isArray(value) ? value as CitationItem[] : []
  } catch {
    return []
  }
}

function readClaimReadiness(raw?: string): ClaimReadinessItem[] {
  try {
    const facts = JSON.parse(raw || '{}')
    return Array.isArray(facts.claimReadiness) ? facts.claimReadiness as ClaimReadinessItem[] : []
  } catch {
    return []
  }
}

function readinessLabel(value: string) {
  if (value === 'ready') return '可先主张'
  if (value === 'high_risk') return '高风险'
  return '需补证据'
}

function readinessType(value: string) {
  if (value === 'ready') return 'success'
  if (value === 'high_risk') return 'danger'
  return 'warning'
}

function strengthLabel(value: string) {
  if (value === 'strong') return '较强'
  if (value === 'medium') return '中等'
  return '偏弱'
}

function conclusionLabel(value: string) {
  if (value === 'preliminary_supported') return '初步较有支撑'
  if (value === 'preliminary_possible') return '存在主张方向'
  return '需要补充事实'
}
</script>
