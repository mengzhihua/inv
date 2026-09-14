<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="状态" clearable style="width:120px" @change="load">
          <el-option v-for="o in REQUEST_STATUS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.invoiceType" placeholder="票种" clearable style="width:130px" @change="load">
          <el-option v-for="o in INVOICE_TYPES" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-select v-model="query.customerId" placeholder="客户" clearable filterable style="width:200px" @change="load">
          <el-option v-for="o in options.customer || []" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="买方名称" clearable style="width:160px" @keyup.enter="load" @clear="load" />
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <el-button v-if="canWrite()" type="success" @click="openForm()"><el-icon><Plus /></el-icon>新建申请</el-button>
        <el-button v-if="canApprove()" :disabled="!selected.length" @click="batchApprove">批量审核</el-button>
        <el-button v-if="canApprove()" :disabled="!selected.length" @click="batchIssue">批量开票</el-button>
        <el-button v-if="canWrite()" :disabled="selected.length < 2" @click="mergeSelected">合并选中</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small" @selection-change="(v) => (selected = v)">
        <el-table-column type="selection" width="40" :selectable="(r) => r.status === 'DRAFT' || r.status === 'SUBMITTED' || r.status === 'APPROVED'" />
        <el-table-column prop="requestNo" label="申请号" width="150" />
        <el-table-column prop="buyerName" label="买方" min-width="160" show-overflow-tooltip />
        <el-table-column label="票种" width="90"><template #default="{ row }"><StatusTag :value="row.invoiceType" /></template></el-table-column>
        <el-table-column prop="totalWithTax" label="含税金额" width="110" align="right"><template #default="{ row }">{{ money(row.totalWithTax) }}</template></el-table-column>
        <el-table-column prop="totalTax" label="税额" width="100" align="right"><template #default="{ row }">{{ money(row.totalTax) }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column label="清单" width="60"><template #default="{ row }"><el-tag v-if="row.withList" size="small" type="warning">清单</el-tag></template></el-table-column>
        <el-table-column prop="source" label="来源" width="80"><template #default="{ row }"><StatusTag :value="row.source" /></template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="150"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="detail(row)">详情</el-button>
            <template v-if="canWrite()">
              <el-button v-if="['DRAFT','REJECTED'].includes(row.status)" link type="primary" size="small" @click="openForm(row)">编辑</el-button>
              <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="act(row, 'submit', '提交审核')">提交</el-button>
              <el-button v-if="row.status === 'DRAFT'" link type="warning" size="small" @click="splitRow(row)">拆分</el-button>
              <el-button v-if="['DRAFT','SUBMITTED'].includes(row.status)" link type="danger" size="small" @click="act(row, 'cancel', '撤销')">撤销</el-button>
            </template>
            <template v-if="canApprove()">
              <el-button v-if="row.status === 'SUBMITTED'" link type="success" size="small" @click="act(row, 'approve', '审核通过')">审核</el-button>
              <el-button v-if="row.status === 'SUBMITTED'" link type="danger" size="small" @click="rejectRow(row)">驳回</el-button>
              <el-button v-if="row.status === 'APPROVED'" link type="primary" size="small" @click="issueRow(row)">开票</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" /></div>
    </div>

    <!-- 新建/编辑申请 -->
    <el-dialog v-model="visible" :title="(form.id ? '编辑' : '新建') + '开票申请'" width="900px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="纳税主体" required>
            <el-select v-model="form.taxEntityId" style="width:100%"><el-option v-for="o in options.taxEntity || []" :key="o.value" :label="o.label" :value="o.value" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="票种" required>
            <el-select v-model="form.invoiceType" style="width:100%"><el-option v-for="o in INVOICE_TYPES.filter((t) => t.value !== 'OTHER')" :key="o.value" :label="o.label" :value="o.value" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="客户">
            <el-select v-model="form.customerId" filterable clearable style="width:100%" @change="fillBuyer"><el-option v-for="o in options.customer || []" :key="o.value" :label="o.label" :value="o.value" /></el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="买方名称" required><el-input v-model="form.buyerName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="买方税号"><el-input v-model="form.buyerTaxNo" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="地址电话"><el-input v-model="form.buyerAddressPhone" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="开户行账号"><el-input v-model="form.buyerBank" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="含税录入"><el-switch v-model="includeTax" active-text="按含税金额录入" @change="recalc" /></el-form-item>
        <el-table :data="form.lines" size="small" border>
          <el-table-column label="商品/服务" width="200">
            <template #default="{ row }">
              <el-select v-model="row.goodsId" filterable clearable placeholder="选择商品" @change="(v) => fillGoods(row, v)">
                <el-option v-for="o in options.goods || []" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="名称" min-width="120"><template #default="{ row }"><el-input v-model="row.goodsName" /></template></el-table-column>
          <el-table-column label="数量" width="100"><template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :precision="2" :controls="false" style="width:100%" @change="lineCalc(row)" /></template></el-table-column>
          <el-table-column label="单价" width="110"><template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="4" :controls="false" style="width:100%" @change="lineCalc(row)" /></template></el-table-column>
          <el-table-column :label="includeTax ? '含税金额' : '金额'" width="120"><template #default="{ row }"><el-input-number v-model="row.amount" :min="0" :precision="2" :controls="false" style="width:100%" @change="lineCalc(row)" /></template></el-table-column>
          <el-table-column label="税率" width="100"><template #default="{ row }"><el-select v-model="row.taxRate" @change="lineCalc(row)"><el-option v-for="r in TAX_RATES" :key="r.value" :label="r.label" :value="r.value" /></el-select></template></el-table-column>
          <el-table-column label="税额(预览)" width="100" align="right"><template #default="{ row }">{{ money(row.previewTax) }}</template></el-table-column>
          <el-table-column width="50"><template #default="{ $index }"><el-button link type="danger" @click="form.lines.splice($index, 1); recalc()"><el-icon><Delete /></el-icon></el-button></template></el-table-column>
        </el-table>
        <div style="margin-top: 8px; display: flex; justify-content: space-between; align-items: center">
          <el-button size="small" @click="addLine"><el-icon><Plus /></el-icon>添加明细行</el-button>
          <div>合计：不含税 <b>{{ money(totals.amount) }}</b>　税额 <b>{{ money(totals.tax) }}</b>　含税 <b>{{ money(totals.withTax) }}</b></div>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawer" title="申请详情" size="640px">
      <el-descriptions v-if="detailData" :column="2" border size="small">
        <el-descriptions-item label="申请号">{{ detailData.request.requestNo }}</el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :value="detailData.request.status" /></el-descriptions-item>
        <el-descriptions-item label="票种"><StatusTag :value="detailData.request.invoiceType" /></el-descriptions-item>
        <el-descriptions-item label="买方">{{ detailData.request.buyerName }}</el-descriptions-item>
        <el-descriptions-item label="不含税">{{ money(detailData.request.totalAmount) }}</el-descriptions-item>
        <el-descriptions-item label="税额">{{ money(detailData.request.totalTax) }}</el-descriptions-item>
        <el-descriptions-item label="含税">{{ money(detailData.request.totalWithTax) }}</el-descriptions-item>
        <el-descriptions-item label="发票ID">{{ detailData.request.invoiceId || '-' }}</el-descriptions-item>
        <el-descriptions-item v-if="detailData.request.rejectReason" label="退回原因" :span="2">{{ detailData.request.rejectReason }}</el-descriptions-item>
      </el-descriptions>
      <el-table v-if="detailData" :data="detailData.lines" size="small" border style="margin-top: 12px">
        <el-table-column prop="goodsName" label="商品" min-width="120" />
        <el-table-column prop="quantity" label="数量" width="80" align="right" />
        <el-table-column prop="amount" label="金额" width="100" align="right"><template #default="{ row }">{{ money(row.amount) }}</template></el-table-column>
        <el-table-column prop="taxRate" label="税率" width="70" align="right"><template #default="{ row }">{{ (row.taxRate * 100).toFixed(0) }}%</template></el-table-column>
        <el-table-column prop="taxAmount" label="税额" width="90" align="right"><template #default="{ row }">{{ money(row.taxAmount) }}</template></el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import { basic, sales } from '../../api'
import { auth, canApprove, canWrite } from '../../auth'
import { INVOICE_TYPES, REQUEST_STATUS, TAX_RATES, money } from '../../composables/useOptions'
import { useOptions } from '../../composables/useOptions'
import { fmt } from '../../utils'
import StatusTag from '../../components/StatusTag.vue'

const route = useRoute()
const { options } = useOptions(['taxEntity', 'customer', 'goods'])
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const drawer = ref(false)
const detailData = ref(null)
const selected = ref([])
const includeTax = ref(false)
const form = ref({ lines: [] })
const query = reactive({
  current: 1, size: 20,
  status: route.query.status || '',
  invoiceType: '', customerId: null, keyword: ''
})

const totals = computed(() => {
  let amount = 0, tax = 0
  for (const l of form.value.lines || []) {
    const r = lineCompute(l)
    amount += r.amount
    tax += r.tax
  }
  return { amount, tax, withTax: amount + tax }
})

function lineCompute(l) {
  const rate = Number(l.taxRate || 0)
  const amt = Number(l.amount || 0)
  if (includeTax.value) {
    const net = Math.round((amt / (1 + rate)) * 100) / 100
    return { amount: net, tax: Math.round((amt - net) * 100) / 100 }
  }
  return { amount: amt, tax: Math.round(amt * rate * 100) / 100 }
}

function lineCalc(row) {
  if (!includeTax.value && row.amount == null && row.quantity != null && row.unitPrice != null) {
    row.amount = Math.round(row.quantity * row.unitPrice * 100) / 100
  }
  row.previewTax = lineCompute(row).tax
}

function recalc() {
  ;(form.value.lines || []).forEach(lineCalc)
}

function fillBuyer(id) {
  const o = (options.value.customer || []).find((x) => x.value === id)
  if (o) {
    form.value.buyerName = o.label
    form.value.buyerTaxNo = o.taxNo
  }
}

function fillGoods(row, id) {
  const o = (options.value.goods || []).find((x) => x.value === id)
  if (o && o.goods) {
    row.goodsName = o.goods.name
    row.taxCategoryCode = o.goods.taxCategoryCode
    row.spec = o.goods.spec
    row.unit = o.goods.unit
    row.unitPrice = o.goods.price
    row.taxRate = Number(o.goods.taxRate)
    lineCalc(row)
  }
}

function addLine() {
  form.value.lines.push({ goodsName: '', taxRate: 0.13 })
}

async function load() {
  loading.value = true
  try {
    const p = await sales.request.page(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  includeTax.value = false
  if (row) {
    sales.request.get(row.id).then((d) => {
      form.value = { ...d.request, lines: d.lines.map((l) => ({ ...l })) }
      recalc()
      visible.value = true
    })
  } else {
    form.value = { invoiceType: 'E_NORMAL', lines: [{ goodsName: '', taxRate: 0.13 }] }
    visible.value = true
  }
}

async function save() {
  const req = { ...form.value }
  delete req.lines
  saving.value = true
  try {
    const payload = { request: req, lines: form.value.lines.map((l) => ({ ...l, priceIncludeTax: includeTax.value ? 1 : 0 })) }
    if (req.id) await sales.request.update(req.id, payload)
    else await sales.request.create(payload)
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function detail(row) {
  detailData.value = await sales.request.get(row.id)
  drawer.value = true
}

async function act(row, action, label) {
  await ElMessageBox.confirm(`确认${label}申请 ${row.requestNo}?`, '确认', { type: 'warning' })
  await sales.request[action](row.id)
  ElMessage.success(`${label}成功`)
  load()
}

async function rejectRow(row) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因', `驳回 ${row.requestNo}`, { inputPlaceholder: '原因' })
  await sales.request.reject(row.id, value)
  ElMessage.success('已驳回')
  load()
}

async function splitRow(row) {
  await ElMessageBox.confirm(`按开票限额自动拆分申请 ${row.requestNo}?（原申请将取消）`, '拆分确认', { type: 'warning' })
  const parts = await sales.request.split(row.id)
  ElMessage.success(`已拆分为 ${parts.length} 张申请`)
  load()
}

async function issueRow(row) {
  await ElMessageBox.confirm(`确认对申请 ${row.requestNo} 开具发票?`, '开票', { type: 'warning' })
  const inv = await sales.invoice.issue(row.id)
  ElMessage.success(`已开票：${inv.invoiceNo}`)
  load()
}

async function batchApprove() {
  const ids = selected.value.filter((r) => r.status === 'SUBMITTED').map((r) => r.id)
  if (!ids.length) return ElMessage.warning('选中的申请须为「已提交」状态')
  await sales.request.batchApprove(ids)
  ElMessage.success(`已审核 ${ids.length} 张`)
  load()
}

async function batchIssue() {
  const ids = selected.value.filter((r) => r.status === 'APPROVED').map((r) => r.id)
  if (!ids.length) return ElMessage.warning('选中的申请须为「已审核」状态')
  const invs = await sales.invoice.batchIssue(ids)
  ElMessage.success(`已开票 ${invs.length} 张`)
  load()
}

async function mergeSelected() {
  const ids = selected.value.filter((r) => r.status === 'DRAFT').map((r) => r.id)
  if (ids.length < 2) return ElMessage.warning('合并需选中至少 2 张「草稿」申请')
  const merged = await sales.request.merge(ids)
  ElMessage.success(`已合并为 ${merged.requestNo}`)
  load()
}

onMounted(load)
</script>
