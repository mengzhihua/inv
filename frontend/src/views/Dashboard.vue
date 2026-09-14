<template>
  <div class="page">
    <el-row :gutter="12">
      <el-col :span="4" v-for="s in stats" :key="s.label">
        <div class="stat">
          <div class="label">{{ s.label }}</div>
          <div class="value" :style="{ color: s.color }">{{ s.value }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="card" style="margin-top: 12px">
      <h3 style="margin: 0 0 12px">待办事项</h3>
      <el-row :gutter="12">
        <el-col :span="4" v-for="t in todos" :key="t.label">
          <div class="todo" @click="$router.push(t.to)">
            <div class="todo-value" :style="{ color: t.color }">{{ t.value ?? 0 }}</div>
            <div class="todo-label">{{ t.label }}</div>
          </div>
        </el-col>
      </el-row>
    </div>

    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="14">
        <div class="card">
          <h3 style="margin: 0 0 12px">近 12 个月开票趋势</h3>
          <div class="bars">
            <div v-for="t in trend" :key="t.month" class="bar-col" :title="`${t.month} 开票 ${t.count} 张 / ${money(t.amount)}`">
              <div class="bar" :style="{ height: barH(t.amount) + 'px' }"></div>
              <div class="bar-day">{{ String(t.month).slice(5, 7) }}月</div>
            </div>
            <el-empty v-if="!trend.length" description="暂无数据" :image-size="60" />
          </div>
          <div style="font-size: 12px; color: #909399; margin-top: 6px">含税金额趋势</div>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="card">
          <h3 style="margin: 0 0 12px">号段余量预警（余量 &lt; 50）</h3>
          <el-table :data="lowStock" size="small" border max-height="260">
            <el-table-column prop="invoiceCode" label="发票代码" width="120" />
            <el-table-column label="票种" width="100"><template #default="{ row }"><StatusTag :value="row.invoiceType" /></template></el-table-column>
            <el-table-column label="号段" min-width="130"><template #default="{ row }">{{ row.startNo }} - {{ row.endNo }}</template></el-table-column>
            <el-table-column prop="remaining" label="余量" width="70" align="right">
              <template #default="{ row }"><span style="color:#f56c6c;font-weight:600">{{ row.remaining }}</span></template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!lowStock.length" description="号段余量充足" :image-size="60" />
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { report } from '../api'
import { money } from '../composables/useOptions'
import StatusTag from '../components/StatusTag.vue'

const d = ref({})
const trend = computed(() => d.value.trend || [])
const lowStock = computed(() => d.value.lowStock || [])
const maxV = computed(() => Math.max(1, ...trend.value.map((t) => Number(t.amount || 0))))
const barH = (v) => Math.round((Number(v || 0) / maxV.value) * 130)

const stats = computed(() => [
  { label: '今日开票张数', value: d.value.todayInvoices ?? 0, color: '#409eff' },
  { label: '今日开票金额', value: money(d.value.todayAmount), color: '#67c23a' },
  { label: '本月销项税额', value: money(d.value.monthOutputTax), color: '#409eff' },
  { label: '本月进项税额', value: money(d.value.monthInputTax), color: '#e6a23c' },
  { label: '异常进项票', value: d.value.abnormalInvoices ?? 0, color: '#f56c6c' },
  { label: '费用票风险数', value: d.value.expenseRisk ?? 0, color: '#f56c6c' }
])
const todos = computed(() => [
  { label: '待审核申请', value: d.value.pendingSubmit, to: '/sales/request?status=SUBMITTED', color: '#409eff' },
  { label: '待开票', value: d.value.pendingIssue, to: '/sales/request?status=APPROVED', color: '#e6a23c' },
  { label: '待查验进项', value: d.value.pendingVerify, to: '/purchase/input?verifyStatus=UNVERIFIED', color: '#409eff' },
  { label: '待勾选抵扣', value: d.value.pendingCheck, to: '/purchase/input?deductStatus=PENDING', color: '#67c23a' }
])

onMounted(async () => { d.value = await report.dashboard() })
</script>

<style scoped>
.todo { text-align: center; padding: 10px 0; border-radius: 6px; cursor: pointer; background: #f5f7fa; margin-bottom: 8px; }
.todo:hover { background: #ecf5ff; }
.todo-value { font-size: 22px; font-weight: 600; }
.todo-label { color: #909399; font-size: 13px; }
.bars { display: flex; align-items: flex-end; gap: 6px; height: 170px; padding: 0 8px; }
.bar-col { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: flex-end; }
.bar { width: 60%; border-radius: 3px 3px 0 0; min-height: 2px; background: #409eff; }
.bar-day { font-size: 10px; color: #909399; margin-top: 4px; }
</style>
