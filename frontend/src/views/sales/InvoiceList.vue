<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.invoiceNo" placeholder="发票号码" clearable style="width:150px" @keyup.enter="load" @clear="load" />
        <el-input v-model="query.buyerName" placeholder="买方名称" clearable style="width:160px" @keyup.enter="load" @clear="load" />
        <el-select v-model="query.invoiceType" placeholder="票种" clearable style="width:130px" @change="load">
          <el-option v-for="o in INVOICE_TYPES" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width:120px" @change="load">
          <el-option v-for="o in INV_STATUS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开票起" end-placeholder="开票止" style="width:230px" @change="load" />
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <el-button v-if="canApprove()" :disabled="!selected.length" @click="batchDeliver">批量交付</el-button>
        <el-button @click="exportCsv"><el-icon><Download /></el-icon>导出 CSV</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small" @selection-change="(v) => (selected = v)">
        <el-table-column type="selection" width="40" :selectable="(r) => ['ISSUED','RED'].includes(r.status)" />
        <el-table-column label="发票号" width="200"><template #default="{ row }">{{ row.invoiceCode ? row.invoiceCode + ' ' : '' }}{{ row.invoiceNo }}</template></el-table-column>
        <el-table-column label="票种" width="90"><template #default="{ row }"><StatusTag :value="row.invoiceType" /></template></el-table-column>
        <el-table-column prop="buyerName" label="买方" min-width="170" show-overflow-tooltip />
        <el-table-column prop="issueDate" label="开票日期" width="100" />
        <el-table-column prop="totalWithTax" label="含税金额" width="110" align="right"><template #default="{ row }">{{ money(row.totalWithTax) }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column label="交付" width="80"><template #default="{ row }"><StatusTag :value="row.deliveryStatus" /></template></el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="detail(row)">详情</el-button>
            <template v-if="canApprove()">
              <el-button v-if="row.status === 'ISSUED' && ['SPECIAL','NORMAL'].includes(row.invoiceType) && row.deliveryStatus !== 'DELIVERED'" link type="danger" size="small" @click="cancelRow(row)">作废</el-button>
              <el-button v-if="row.status === 'ISSUED'" link type="warning" size="small" @click="openRed(row)">红冲</el-button>
              <el-button v-if="['ISSUED','RED'].includes(row.status) && row.deliveryStatus !== 'DELIVERED'" link type="success" size="small" @click="openDeliver(row)">交付</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" /></div>
    </div>

    <!-- 发票详情抽屉 -->
    <el-drawer v-model="drawer" title="发票详情" size="720px">
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="发票代码">{{ detailData.invoice.invoiceCode || '-' }}</el-descriptions-item>
          <el-descriptions-item label="发票号码">{{ detailData.invoice.invoiceNo }}</el-descriptions-item>
          <el-descriptions-item label="票种"><StatusTag :value="detailData.invoice.invoiceType" /></el-descriptions-item>
          <el-descriptions-item label="状态"><StatusTag :value="detailData.invoice.status" /></el-descriptions-item>
          <el-descriptions-item label="开票日期">{{ detailData.invoice.issueDate }}</el-descriptions-item>
          <el-descriptions-item label="校验码">{{ detailData.invoice.checkCode }}</el-descriptions-item>
          <el-descriptions-item label="销售方" :span="2">{{ detailData.invoice.sellerName }}（{{ detailData.invoice.sellerTaxNo }}）</el-descriptions-item>
          <el-descriptions-item label="购买方" :span="2">{{ detailData.invoice.buyerName }}（{{ detailData.invoice.buyerTaxNo || '-' }}）</el-descriptions-item>
          <el-descriptions-item label="不含税">{{ money(detailData.invoice.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="税额">{{ money(detailData.invoice.totalTax) }}</el-descriptions-item>
          <el-descriptions-item label="价税合计">{{ money(detailData.invoice.totalWithTax) }}</el-descriptions-item>
          <el-descriptions-item label="已红冲金额">{{ money(detailData.invoice.redAmount) }}</el-descriptions-item>
          <el-descriptions-item label="开/收/复" :span="2">{{ detailData.invoice.drawer }} / {{ detailData.invoice.payee }} / {{ detailData.invoice.reviewer }}</el-descriptions-item>
        </el-descriptions>
        <h4>明细</h4>
        <el-table :data="detailData.lines" size="small" border>
          <el-table-column prop="goodsName" label="商品" min-width="110" />
          <el-table-column prop="quantity" label="数量" width="70" align="right" />
          <el-table-column prop="amount" label="金额" width="95" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
          <el-table-column prop="taxRate" label="税率" width="65" align="right"><template #default="{ row }">{{ (row.taxRate * 100).toFixed(0) }}%</template></el-table-column>
          <el-table-column prop="taxAmount" label="税额" width="85" align="right"><template #default="{ row }">{{ money(row.taxAmount) }}</template></el-table-column>
        </el-table>
        <h4>事件轨迹</h4>
        <el-timeline>
          <el-timeline-item v-for="e in detailData.events" :key="e.id" :timestamp="fmt(e.createdAt)" :type="e.event === 'ISSUED' ? 'success' : e.event === 'CANCELLED' ? 'danger' : 'primary'">
            {{ e.event }}　{{ e.detail }}　<el-text size="small" type="info">{{ e.operator }}</el-text>
          </el-timeline-item>
        </el-timeline>
        <h4>交付记录</h4>
        <el-table :data="detailData.deliveries" size="small" border>
          <el-table-column prop="channel" label="渠道" width="80"><template #default="{ row }"><StatusTag :value="row.channel" /></template></el-table-column>
          <el-table-column prop="target" label="目标" min-width="160" />
          <el-table-column prop="status" label="结果" width="80" />
          <el-table-column prop="createdAt" label="时间" width="150"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- 交付 -->
    <el-dialog v-model="deliverVisible" title="发票交付" width="420px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="渠道"><el-radio-group v-model="deliverForm.channel"><el-radio value="EMAIL">邮箱</el-radio><el-radio value="SMS">短信</el-radio></el-radio-group></el-form-item>
        <el-form-item label="目标"><el-input v-model="deliverForm.target" :placeholder="deliverForm.channel === 'EMAIL' ? '邮箱地址' : '手机号'" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deliverVisible = false">取消</el-button>
        <el-button type="primary" @click="doDeliver">发送</el-button>
      </template>
    </el-dialog>

    <!-- 红冲：红字信息表 -->
    <el-dialog v-model="redVisible" :title="`发起红冲 — ${redInvoice?.invoiceNo || ''}`" width="720px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom: 8px"
        :title="`剩余可红：不含税 ${money(remain.amount)} / 税额 ${money(remain.tax)}；不填行则全额红冲（已部分红冲时必须选行）`" />
      <el-form label-width="80px">
        <el-form-item label="红冲原因">
          <el-select v-model="redForm.reason" style="width:200px"><el-option v-for="o in RED_REASONS" :key="o.value" :label="o.label" :value="o.value" /></el-select>
        </el-form-item>
      </el-form>
      <el-table :data="redLines" size="small" border @selection-change="(v) => (redPicked = v)">
        <el-table-column type="selection" width="40" />
        <el-table-column prop="goodsName" label="商品" min-width="120" />
        <el-table-column prop="amount" label="金额" width="110" align="right"><template #default="{ row }"><el-input-number v-model="row.amount" :precision="2" :controls="false" style="width:100%" @change="redTax(row)" /></template></el-table-column>
        <el-table-column prop="taxRate" label="税率" width="80" align="right"><template #default="{ row }">{{ (row.taxRate * 100).toFixed(0) }}%</template></el-table-column>
        <el-table-column prop="taxAmount" label="税额" width="110" align="right"><template #default="{ row }"><el-input-number v-model="row.taxAmount" :precision="2" :controls="false" style="width:100%" /></template></el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="redVisible = false">取消</el-button>
        <el-button type="primary" @click="doRed">创建红字信息表</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sales } from '../../api'
import { canApprove } from '../../auth'
import { INVOICE_TYPES, RED_REASONS, money } from '../../composables/useOptions'
import { downloadCsv } from '../../api'
import { fmt } from '../../utils'
import StatusTag from '../../components/StatusTag.vue'

const INV_STATUS = [
  { label: '已开出', value: 'ISSUED' }, { label: '已作废', value: 'CANCELLED' },
  { label: '已红冲', value: 'RED_FLUSHED' }, { label: '红字票', value: 'RED' }
]

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const selected = ref([])
const range = ref(null)
const query = reactive({ current: 1, size: 20, status: '', invoiceType: '', buyerName: '', invoiceNo: '' })

const drawer = ref(false)
const detailData = ref(null)
const deliverVisible = ref(false)
const deliverForm = reactive({ channel: 'EMAIL', target: '' })
const deliverInvoice = ref(null)
const redVisible = ref(false)
const redInvoice = ref(null)
const redLines = ref([])
const redForm = reactive({ reason: 'SELLER_ERROR' })
const redPicked = ref([])

const remain = computed(() => {
  if (!detailData.value && !redInvoice.value) return { amount: 0, tax: 0 }
  const i = redInvoice.value || {}
  return { amount: (i.totalAmount || 0) - (i.redAmount || 0), tax: (i.totalTax || 0) - (i.redTax || 0) }
})

async function load() {
  loading.value = true
  try {
    const p = await sales.invoice.page({ ...query, dateFrom: range.value?.[0], dateTo: range.value?.[1] })
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

async function detail(row) {
  detailData.value = await sales.invoice.get(row.id)
  drawer.value = true
}

async function cancelRow(row) {
  const { value } = await ElMessageBox.prompt('作废原因', `作废发票 ${row.invoiceNo}`, { inputPlaceholder: '原因（可选）' })
  await sales.invoice.cancel(row.id, value || '')
  ElMessage.success('已作废')
  load()
}

function openDeliver(row) {
  deliverInvoice.value = row
  deliverForm.channel = 'EMAIL'
  deliverForm.target = ''
  deliverVisible.value = true
}

async function doDeliver() {
  await sales.invoice.deliver(deliverInvoice.value.id, deliverForm)
  ElMessage.success('已交付')
  deliverVisible.value = false
  load()
}

async function batchDeliver() {
  const { value: ch } = await ElMessageBox.prompt('交付目标（邮箱/手机号）', '批量交付', {
    inputPlaceholder: 'target@example.com', confirmButtonText: '发送'
  })
  if (!ch) return
  const channel = ch.includes('@') ? 'EMAIL' : 'SMS'
  await sales.invoice.batchDeliver({ ids: selected.value.map((r) => r.id), channel, target: ch })
  ElMessage.success('批量交付完成')
  load()
}

async function openRed(row) {
  redInvoice.value = row
  redForm.reason = 'SELLER_ERROR'
  const d = await sales.invoice.get(row.id)
  redLines.value = d.lines.map((l) => ({ ...l, checked: false }))
  redVisible.value = true
}

function redTax(row) {
  row.taxAmount = Math.round(row.amount * row.taxRate * 100) / 100
}

async function doRed() {
  const picked = redPicked.value
  const payload = { invoiceId: redInvoice.value.id, reason: redForm.reason }
  if (picked.length) {
    payload.lines = picked.map((l) => ({ goodsName: l.goodsName, taxCategoryCode: l.taxCategoryCode, spec: l.spec, unit: l.unit, quantity: l.quantity, unitPrice: l.unitPrice, amount: l.amount, taxRate: l.taxRate, taxAmount: l.taxAmount }))
  }
  const info = await sales.redInfo.create(payload)
  ElMessage.success(`红字信息表已创建（ID ${info.id}），请到「红字信息表」确认并红冲`)
  redVisible.value = false
}

function exportCsv() {
  downloadCsv('/sales/invoice/export', { status: query.status, invoiceType: query.invoiceType }, '销项发票.csv')
}

onMounted(load)
</script>
