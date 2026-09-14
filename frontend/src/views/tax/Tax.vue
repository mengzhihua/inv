<template>
  <div class="page">
    <el-tabs v-model="tab">
      <el-tab-pane label="增值税申报预填" name="vat">
        <div class="card">
          <el-form inline>
            <el-form-item label="纳税主体"><el-select v-model="entityId" style="width:240px"><el-option v-for="o in entities" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
            <el-form-item label="属期"><el-date-picker v-model="period" type="month" value-format="YYYY-MM" style="width:140px" /></el-form-item>
            <el-button type="primary" @click="loadVat">计算预填</el-button>
          </el-form>
          <template v-if="vat">
            <h4>销项（按税率分组，净额 = 正常票 − 红字票，不含作废）</h4>
            <el-table :data="vat.outputByRate" size="small" border style="max-width:760px">
              <el-table-column label="税率" width="100"><template #default="{ row }">{{ (Number(g(row,'taxRate')) * 100).toFixed(0) }}%</template></el-table-column>
              <el-table-column label="张数" width="90" align="right"><template #default="{ row }">{{ g(row,'invoiceCount') }}</template></el-table-column>
              <el-table-column label="不含税" width="140" align="right"><template #default="{ row }">{{ money(g(row,'amount')) }}</template></el-table-column>
              <el-table-column label="税额" width="140" align="right"><template #default="{ row }">{{ money(g(row,'tax')) }}</template></el-table-column>
            </el-table>
            <el-row :gutter="12" style="margin-top: 16px">
              <el-col :span="6"><div class="stat"><div class="label">销项税额合计</div><div class="value">{{ money(vat.outputTax) }}</div></div></el-col>
              <el-col :span="6"><div class="stat"><div class="label">进项已抵扣税额</div><div class="value">{{ money(vat.inputTax) }}（{{ vat.inputCount }} 张）</div></div></el-col>
              <el-col :span="6"><div class="stat"><div class="label">应纳税额 / 留抵</div><div class="value" :style="{color: vat.payable < 0 ? '#e6a23c' : '#f56c6c'}">{{ money(vat.payable) }}<span v-if="vat.credit > 0" style="font-size:14px">（留抵 {{ money(vat.credit) }}）</span></div></div></el-col>
              <el-col :span="6"><div class="stat"><div class="label">税负率</div><div class="value" :style="{color: vat.burdenWarned ? '#f56c6c' : '#67c23a'}">{{ (Number(vat.burdenRate) * 100).toFixed(2) }}%<el-tag v-if="vat.burdenWarned" type="danger" size="small" style="margin-left:6px">低于预警线 {{ (Number(vat.burdenWarn)*100).toFixed(0) }}%</el-tag></div></div></el-col>
            </el-row>
            <el-form inline style="margin-top: 16px">
              <el-form-item label="预演：若再勾选进项税">
                <el-input-number v-model="extraTax" :min="0" :precision="2" style="width:160px" /> 元
              </el-form-item>
              <el-button @click="doPreview">预览税负变化</el-button>
              <span v-if="preview" style="margin-left: 12px">
                → 应纳税额 <b>{{ money(preview.payable) }}</b>，税负率
                <b :style="{color: preview.burdenWarned ? '#f56c6c' : '#67c23a'}">{{ (Number(preview.burdenRate) * 100).toFixed(2) }}%</b>
              </span>
            </el-form>
          </template>
        </div>
      </el-tab-pane>
      <el-tab-pane label="属期管理" name="period">
        <div class="card">
          <div class="toolbar">
            <el-select v-model="pEntityId" placeholder="纳税主体" clearable style="width:220px" @change="loadPeriod"><el-option v-for="o in entities" :key="o.value" :label="o.label" :value="o.value" /></el-select>
            <el-button @click="loadPeriod"><el-icon><Refresh /></el-icon>刷新</el-button>
            <el-button v-if="canApprove()" type="primary" @click="openPeriod">开新属期</el-button>
          </div>
          <el-table :data="periods" border stripe size="small">
            <el-table-column prop="taxEntityId" label="主体ID" width="90" />
            <el-table-column prop="period" label="属期" width="100" />
            <el-table-column label="状态" width="100"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" width="160"><template #default="{ row }">{{ fmt(row.updatedAt) }}</template></el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <template v-if="canApprove()">
                  <el-button v-if="row.status === 'OPEN'" link type="danger" size="small" @click="closePeriod(row)">关账</el-button>
                  <el-button v-else link type="success" size="small" @click="reopenPeriod(row)">开账</el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager"><el-pagination v-model:current-page="pQuery.current" v-model:page-size="pQuery.size" :total="pTotal" layout="total, prev, pager, next" @change="loadPeriod" /></div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { basic, tax } from '../../api'
import { canApprove } from '../../auth'
import { g, money } from '../../composables/useOptions'
import { fmt } from '../../utils'
import StatusTag from '../../components/StatusTag.vue'

const tab = ref('vat')
const entities = ref([])
const entityId = ref(null)
const pEntityId = ref(null)
const period = ref(new Date().toISOString().substring(0, 7))
const vat = ref(null)
const extraTax = ref(1000)
const preview = ref(null)
const periods = ref([])
const pTotal = ref(0)
const pQuery = reactive({ current: 1, size: 20 })

async function loadVat() {
  if (!entityId.value) return ElMessage.warning('请选择纳税主体')
  vat.value = await tax.vatReturn(entityId.value, period.value)
  preview.value = null
}

async function doPreview() {
  preview.value = await tax.preview({ entity: entityId.value, period: period.value, additionalInputTax: extraTax.value })
}

async function loadPeriod() {
  const p = await tax.periodPage({ ...pQuery, taxEntityId: pEntityId.value || undefined })
  periods.value = p.records
  pTotal.value = p.total
}

async function openPeriod() {
  if (!pEntityId.value) return ElMessage.warning('请选择纳税主体')
  const { value } = await ElMessageBox.prompt('属期 (yyyy-MM)', '开新属期', { inputValue: new Date().toISOString().substring(0, 7), inputPattern: /^\d{4}-\d{2}$/, inputErrorMessage: '格式 yyyy-MM' })
  await tax.open({ taxEntityId: pEntityId.value, period: value })
  ElMessage.success('已开账')
  loadPeriod()
}

async function closePeriod(row) {
  await ElMessageBox.confirm(`关闭属期 ${row.period}？关账后该期进项不可再勾选、销项不可作废`, '关账', { type: 'warning' })
  await tax.close({ taxEntityId: row.taxEntityId, period: row.period })
  ElMessage.success('已关账')
  loadPeriod()
}

async function reopenPeriod(row) {
  await tax.open({ taxEntityId: row.taxEntityId, period: row.period })
  ElMessage.success('已开账')
  loadPeriod()
}

onMounted(async () => {
  entities.value = (await basic.taxEntity.list()).map((e) => ({ label: `${e.code} ${e.name}`, value: e.id }))
  entityId.value = entities.value[0]?.value
  loadVat()
  loadPeriod()
})
</script>
