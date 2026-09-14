<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.invoiceNo" placeholder="发票号码" clearable style="width:140px" @keyup.enter="load" @clear="load" />
        <el-input v-model="query.sellerName" placeholder="销售方" clearable style="width:160px" @keyup.enter="load" @clear="load" />
        <el-select v-model="query.verifyStatus" placeholder="查验状态" clearable style="width:120px" @change="load"><el-option v-for="o in VERIFY_STATUS" :key="o.value" :label="o.label" :value="o.value" /></el-select>
        <el-select v-model="query.deductStatus" placeholder="抵扣状态" clearable style="width:120px" @change="load"><el-option v-for="o in DEDUCT_STATUS" :key="o.value" :label="o.label" :value="o.value" /></el-select>
        <el-select v-model="query.status" placeholder="发票状态" clearable style="width:110px" @change="load"><el-option label="正常" value="NORMAL" /><el-option label="异常" value="ABNORMAL" /><el-option label="已红冲" value="RED_FLUSHED" /></el-select>
        <el-select v-model="query.matchStatus" placeholder="匹配" clearable style="width:110px" @change="load"><el-option label="未匹配" value="UNMATCHED" /><el-option label="已匹配" value="MATCHED" /><el-option label="差异" value="MISMATCH" /></el-select>
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <template v-if="canWrite()">
          <el-button type="success" @click="openForm()"><el-icon><Plus /></el-icon>录入</el-button>
          <el-button @click="scanVisible = true">OCR 录入</el-button>
          <el-upload :show-file-list="false" accept=".csv" :http-request="(o) => doImport(o.file)"><el-button>CSV 导入</el-button></el-upload>
        </template>
        <el-button v-if="canApprove()" :disabled="!selected.length" @click="batchVerify">批量查验</el-button>
        <el-button @click="exportCsv"><el-icon><Download /></el-icon>导出</el-button>
        <el-button link type="primary" @click="downloadTemplate">导入模板</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small" @selection-change="(v) => (selected = v)">
        <el-table-column type="selection" width="40" />
        <el-table-column label="发票号" width="190"><template #default="{ row }">{{ row.invoiceCode ? row.invoiceCode + ' ' : '' }}{{ row.invoiceNo }}</template></el-table-column>
        <el-table-column label="票种" width="90"><template #default="{ row }"><StatusTag :value="row.invoiceType" /></template></el-table-column>
        <el-table-column prop="sellerName" label="销售方" min-width="160" show-overflow-tooltip />
        <el-table-column prop="issueDate" label="开票日期" width="100" />
        <el-table-column prop="totalWithTax" label="含税" width="110" align="right"><template #default="{ row }">{{ money(row.totalWithTax) }}</template></el-table-column>
        <el-table-column prop="totalTax" label="税额" width="90" align="right"><template #default="{ row }">{{ money(row.totalTax) }}</template></el-table-column>
        <el-table-column label="查验" width="90"><template #default="{ row }"><StatusTag :value="row.verifyStatus" /></template></el-table-column>
        <el-table-column label="抵扣" width="90"><template #default="{ row }"><StatusTag :value="row.deductStatus" /></template></el-table-column>
        <el-table-column label="匹配" width="80"><template #default="{ row }"><StatusTag :value="row.matchStatus" /></template></el-table-column>
        <el-table-column label="入账" width="80"><template #default="{ row }"><StatusTag :value="row.accountStatus" /></template></el-table-column>
        <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag size="small" :type="row.status === 'NORMAL' ? 'success' : 'danger'">{{ row.status === 'NORMAL' ? '正常' : '异常' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="detail(row)">详情</el-button>
            <template v-if="canWrite()">
              <el-button v-if="row.verifyStatus !== 'VERIFIED'" link type="primary" size="small" @click="verify(row)">查验</el-button>
            </template>
            <template v-if="canApprove()">
              <el-button v-if="row.verifyStatus === 'VERIFIED' && row.deductStatus === 'PENDING' && ['SPECIAL','E_SPECIAL','ALL_ELECTRIC'].includes(row.invoiceType)" link type="success" size="small" @click="checkRow(row)">勾选</el-button>
              <el-button v-if="row.deductStatus === 'CHECKED'" link type="warning" size="small" @click="uncheck(row)">取消勾选</el-button>
              <el-button v-if="['PENDING','CHECKED'].includes(row.deductStatus)" link type="info" size="small" @click="notDeduct(row)">不抵扣</el-button>
              <el-button v-if="row.matchStatus !== 'MATCHED'" link type="primary" size="small" @click="openMatch(row)">匹配</el-button>
              <el-button v-if="row.accountStatus === 'UNPOSTED'" link type="warning" size="small" @click="post(row)">入账</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" /></div>
    </div>

    <!-- 录入/扫描对话框 -->
    <el-dialog v-model="visible" :title="scanMode ? 'OCR 扫描录入（模拟）' : '手工录入进项发票'" width="760px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="票种" required><el-select v-model="form.invoiceType" style="width:100%"><el-option v-for="o in INVOICE_TYPES" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="发票代码"><el-input v-model="form.invoiceCode" placeholder="数电票留空" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="发票号码" required><el-input v-model="form.invoiceNo" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="开票日期" required><el-date-picker v-model="form.issueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="销售方"><el-input v-model="form.sellerName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="销售方税号"><el-input v-model="form.sellerTaxNo" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="购买方"><el-input v-model="form.buyerName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="购买方税号"><el-input v-model="form.buyerTaxNo" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="校验码"><el-input v-model="form.checkCode" placeholder="后 6 位参与查验" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="不含税" required><el-input-number v-model="form.totalAmount" :precision="2" :controls="false" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="税额" required><el-input-number v-model="form.totalTax" :precision="2" :controls="false" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="含税" required><el-input-number v-model="form.totalWithTax" :precision="2" :controls="false" style="width:100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 三单匹配 -->
    <el-dialog v-model="matchVisible" title="三单匹配" width="420px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="采购单号"><el-input v-model="matchForm.poNo" /></el-form-item>
        <el-form-item label="入库单号"><el-input v-model="matchForm.receiptNo" /></el-form-item>
        <el-form-item label="单据金额"><el-input-number v-model="matchForm.amount" :precision="2" :controls="false" style="width:100%" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="matchVisible = false">取消</el-button>
        <el-button type="primary" @click="doMatch">匹配</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawer" title="进项发票详情" size="620px">
      <template v-if="detailData">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="号码" :span="2">{{ detailData.invoice.invoiceCode || '' }} {{ detailData.invoice.invoiceNo }}</el-descriptions-item>
          <el-descriptions-item label="来源"><StatusTag :value="detailData.invoice.source" /></el-descriptions-item>
          <el-descriptions-item label="查验"><StatusTag :value="detailData.invoice.verifyStatus" /> {{ detailData.invoice.verifyMsg }}</el-descriptions-item>
          <el-descriptions-item label="销售方" :span="2">{{ detailData.invoice.sellerName }}（{{ detailData.invoice.sellerTaxNo }}）</el-descriptions-item>
          <el-descriptions-item label="购买方" :span="2">{{ detailData.invoice.buyerName }}（{{ detailData.invoice.buyerTaxNo }}）</el-descriptions-item>
          <el-descriptions-item label="抵扣"><StatusTag :value="detailData.invoice.deductStatus" /> {{ detailData.invoice.deductPeriod }}</el-descriptions-item>
          <el-descriptions-item label="入账"><StatusTag :value="detailData.invoice.accountStatus" /> {{ detailData.invoice.voucherNo }}</el-descriptions-item>
          <el-descriptions-item label="匹配"><StatusTag :value="detailData.invoice.matchStatus" /> {{ detailData.invoice.poNo || '' }} {{ detailData.invoice.receiptNo || '' }}</el-descriptions-item>
          <el-descriptions-item label="差异">{{ money(detailData.invoice.matchDiff) }}</el-descriptions-item>
        </el-descriptions>
        <el-table v-if="detailData.lines?.length" :data="detailData.lines" size="small" border style="margin-top: 12px">
          <el-table-column prop="goodsName" label="商品" min-width="110" />
          <el-table-column prop="amount" label="金额" width="100" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
          <el-table-column prop="taxAmount" label="税额" width="90" align="right"><template #default="{ row }">{{ money(row.taxAmount) }}</template></el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { purchase } from '../../api'
import { canApprove, canWrite } from '../../auth'
import { INVOICE_TYPES, VERIFY_STATUS, DEDUCT_STATUS, money } from '../../composables/useOptions'
import { downloadCsv } from '../../api'
import { fmt } from '../../utils'
import StatusTag from '../../components/StatusTag.vue'

const route = useRoute()
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const selected = ref([])
const visible = ref(false)
const scanMode = ref(false)
const scanVisible = ref(false)
const matchVisible = ref(false)
const drawer = ref(false)
const detailData = ref(null)
const form = ref({})
const matchForm = reactive({ poNo: '', receiptNo: '', amount: null })
const matchRow = ref(null)
const query = reactive({
  current: 1, size: 20,
  verifyStatus: route.query.verifyStatus || '',
  deductStatus: route.query.deductStatus || '',
  status: '', matchStatus: '', sellerName: '', invoiceNo: ''
})

async function load() {
  loading.value = true
  try {
    const p = await purchase.input.page(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

function openForm() {
  scanMode.value = false
  form.value = { invoiceType: 'SPECIAL', issueDate: new Date().toISOString().substring(0, 10) }
  visible.value = true
}

async function save() {
  saving.value = true
  try {
    if (scanMode.value) await purchase.input.scan(form.value)
    else await purchase.input.create({ invoice: form.value, source: 'MANUAL' })
    ElMessage.success('已录入')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function doImport(file) {
  const r = await purchase.input.importCsv(file)
  if (r.errors?.length) ElMessage.warning(`导入 ${r.imported} 条，${r.errors.length} 条失败：${r.errors[0]}`)
  else ElMessage.success(`导入 ${r.imported} 条`)
  load()
}

function downloadTemplate() {
  downloadCsv('/purchase/input/import-template', {}, '进项导入模板.csv')
}

async function detail(row) {
  detailData.value = await purchase.input.get(row.id)
  drawer.value = true
}

async function verify(row) {
  const r = await purchase.input.verify(row.id)
  ElMessage[r.verifyStatus === 'VERIFIED' ? 'success' : 'warning'](r.verifyMsg || r.verifyStatus)
  load()
}

async function batchVerify() {
  const rs = await purchase.input.batchVerify(selected.value.map((r) => r.id))
  ElMessage.success(`查验完成 ${rs.length} 张`)
  load()
}

async function checkRow(row) {
  const { value } = await ElMessageBox.prompt('抵扣属期 (yyyy-MM)', `勾选 ${row.invoiceNo}`, {
    inputValue: new Date().toISOString().substring(0, 7), inputPattern: /^\d{4}-\d{2}$/, inputErrorMessage: '格式 yyyy-MM'
  })
  await purchase.input.check(row.id, value)
  ElMessage.success('已勾选')
  load()
}

async function uncheck(row) {
  await purchase.input.uncheck(row.id)
  ElMessage.success('已取消勾选')
  load()
}

async function notDeduct(row) {
  const { value } = await ElMessageBox.prompt('不抵扣原因', `不抵扣 ${row.invoiceNo}`, { inputPlaceholder: '原因' })
  await purchase.input.notDeduct(row.id, value)
  ElMessage.success('已标记不抵扣')
  load()
}

function openMatch(row) {
  matchRow.value = row
  matchForm.poNo = row.poNo || ''
  matchForm.receiptNo = row.receiptNo || ''
  matchForm.amount = row.totalWithTax
  matchVisible.value = true
}

async function doMatch() {
  const r = await purchase.input.match(matchRow.value.id, { ...matchForm })
  ElMessage[r.matchStatus === 'MATCHED' ? 'success' : 'warning'](r.matchStatus === 'MATCHED' ? '匹配成功' : `存在差异 ${money(r.matchDiff)}`)
  matchVisible.value = false
  load()
}

async function post(row) {
  const r = await purchase.input.post(row.id)
  ElMessage.success(`已入账，凭证号 ${r.voucherNo}`)
  load()
}

function exportCsv() {
  downloadCsv('/purchase/input/export', { deductStatus: query.deductStatus }, '进项发票.csv')
}

// OCR 模拟录入入口
watch(scanVisible, (v) => {
  if (v) {
    scanMode.value = true
    form.value = { invoiceType: 'NORMAL', issueDate: new Date().toISOString().substring(0, 10) }
    visible.value = true
    scanVisible.value = false
  }
})

onMounted(load)
</script>
