<template>
  <div class="page">
    <el-row :gutter="12">
      <el-col :span="12">
        <div class="card">
          <div class="toolbar"><h3 style="margin:0">销项统计</h3>
            <el-select v-model="salesDim" size="small" style="width:130px" @change="loadSales">
              <el-option label="按月" value="month" /><el-option label="按主体" value="entity" /><el-option label="按客户" value="customer" /><el-option label="按票种" value="type" />
            </el-select>
            <el-date-picker v-model="salesRange" type="daterange" size="small" value-format="YYYY-MM-DD" unlink-panels start-placeholder="开始" end-placeholder="结束" style="width:240px" @change="loadRange" />
            <el-button size="small" @click="exportSales"><el-icon><Download /></el-icon>CSV</el-button>
          </div>
          <el-table :data="salesRows" size="small" border>
            <el-table-column label="维度" min-width="120"><template #default="{ row }">{{ dimLabel(g(row,'dim')) }}</template></el-table-column>
            <el-table-column label="张数" width="70" align="right"><template #default="{ row }">{{ g(row,'cnt') }}</template></el-table-column>
            <el-table-column label="不含税" width="120" align="right"><template #default="{ row }">{{ money(g(row,'amount')) }}</template></el-table-column>
            <el-table-column label="税额" width="110" align="right"><template #default="{ row }">{{ money(g(row,'tax')) }}</template></el-table-column>
            <el-table-column label="含税" width="120" align="right"><template #default="{ row }">{{ money(g(row,'withTax')) }}</template></el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="card">
          <div class="toolbar"><h3 style="margin:0">进项统计</h3>
            <el-select v-model="inputDim" size="small" style="width:130px" @change="loadInput">
              <el-option label="按供应商" value="supplier" /><el-option label="按抵扣状态" value="deductStatus" /><el-option label="按票种" value="type" />
            </el-select>
          </div>
          <el-table :data="inputRows" size="small" border>
            <el-table-column label="维度" min-width="120"><template #default="{ row }">{{ dimLabel(g(row,'dim')) }}</template></el-table-column>
            <el-table-column label="张数" width="70" align="right"><template #default="{ row }">{{ g(row,'cnt') }}</template></el-table-column>
            <el-table-column label="不含税" width="120" align="right"><template #default="{ row }">{{ money(g(row,'amount')) }}</template></el-table-column>
            <el-table-column label="税额" width="110" align="right"><template #default="{ row }">{{ money(g(row,'tax')) }}</template></el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
    <el-row :gutter="12" style="margin-top: 12px">
      <el-col :span="8">
        <div class="card">
          <h3 style="margin-top:0">红冲 / 作废统计</h3>
          <el-table :data="redRows" size="small" border>
            <el-table-column label="状态" width="110"><template #default="{ row }"><StatusTag :value="g(row,'dim')" /></template></el-table-column>
            <el-table-column label="张数" width="80" align="right"><template #default="{ row }">{{ g(row,'cnt') }}</template></el-table-column>
            <el-table-column label="含税金额" align="right"><template #default="{ row }">{{ money(g(row,'withTax')) }}</template></el-table-column>
          </el-table>
        </div>
      </el-col>
      <el-col :span="16">
        <div class="card">
          <h3 style="margin-top:0">客户开票排名 TOP10</h3>
          <el-table :data="rank" size="small" border>
            <el-table-column type="index" label="#" width="50" />
            <el-table-column label="客户" min-width="160"><template #default="{ row }">{{ g(row,'dim') }}</template></el-table-column>
            <el-table-column label="张数" width="80" align="right"><template #default="{ row }">{{ g(row,'cnt') }}</template></el-table-column>
            <el-table-column label="含税金额" width="140" align="right"><template #default="{ row }">{{ money(g(row,'withTax')) }}</template></el-table-column>
            <el-table-column label="占比"><template #default="{ row }"><el-progress :percentage="pct(g(row,'withTax'))" :stroke-width="12" /></template></el-table-column>
          </el-table>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { downloadCsv, report } from '../../api'
import { g, labelOf, money, INVOICE_TYPES } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'

const salesDim = ref('month')
const salesRange = ref(null)
const inputDim = ref('supplier')
const salesRows = ref([])
const inputRows = ref([])
const redRows = ref([])
const rank = ref([])

const maxRank = computed(() => Math.max(1, ...rank.value.map((r) => Number(g(r, 'withTax') || 0))))
const pct = (v) => Math.round((Number(v || 0) / maxRank.value) * 100)

const STATUS_LABELS = { PENDING: '待勾选', CHECKED: '已勾选', DEDUCTED: '已抵扣', NOT_DEDUCT: '不抵扣' }
function dimLabel(v) {
  return STATUS_LABELS[v] || labelOf(INVOICE_TYPES, v) || v || '（空）'
}

function rangeParams() {
  const params = {}
  if (salesRange.value && salesRange.value[0]) params.from = salesRange.value[0]
  if (salesRange.value && salesRange.value[1]) params.to = salesRange.value[1]
  return params
}
const seq = { sales: 0, red: 0, rank: 0, input: 0 }
async function latest(key, target, request) {
  const n = ++seq[key]
  const data = await request()
  if (n === seq[key]) target.value = data
}
function loadSales() { return latest('sales', salesRows, () => report.salesSummary({ dimension: salesDim.value, ...rangeParams() })) }
function loadRed() { return latest('red', redRows, () => report.redCancel(rangeParams())) }
function loadRank() { return latest('rank', rank, () => report.customerRank({ limit: 10, ...rangeParams() })) }
function loadRange() { loadSales(); loadRed(); loadRank() }
function loadInput() { return latest('input', inputRows, () => report.inputSummary({ dimension: inputDim.value })) }
function exportSales() { downloadCsv('/report/sales-summary/export', { dimension: salesDim.value, ...rangeParams() }, '销项统计.csv') }

onMounted(async () => {
  loadSales()
  loadInput()
  loadRed()
  loadRank()
})
</script>
