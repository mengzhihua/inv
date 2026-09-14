<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="状态" clearable style="width:130px" @change="load">
          <el-option v-for="o in EXP_STATUS" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input v-model="query.department" placeholder="部门" clearable style="width:140px" @keyup.enter="load" @clear="load" />
        <el-input v-model="query.keyword" placeholder="员工姓名" clearable style="width:140px" @keyup.enter="load" @clear="load" />
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <el-button v-if="canWrite()" type="success" @click="openForm"><el-icon><Plus /></el-icon>上传费用票</el-button>
        <el-button v-if="canWrite()" @click="checkAll">批量合规检查</el-button>
        <el-button v-if="canApprove()" :disabled="!compliantSelected.length" @click="reimburseSelected">报销选中</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small" @selection-change="(v) => (selected = v)">
        <el-table-column type="selection" width="40" :selectable="(r) => r.status === 'COMPLIANT'" />
        <el-table-column prop="employeeName" label="员工" width="90" />
        <el-table-column prop="department" label="部门" width="100" />
        <el-table-column label="发票号" width="150"><template #default="{ row }">{{ row.invoiceCode ? row.invoiceCode + ' ' : '' }}{{ row.invoiceNo }}</template></el-table-column>
        <el-table-column label="票种" width="90"><template #default="{ row }"><StatusTag :value="row.invoiceType" /></template></el-table-column>
        <el-table-column prop="issueDate" label="开票日期" width="100" />
        <el-table-column prop="totalWithTax" label="含税" width="100" align="right"><template #default="{ row }">{{ money(row.totalWithTax) }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column label="风险项" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="r in risks(row)" :key="r" size="small" type="danger" style="margin-right:4px">{{ RISK_LABEL[r] || r }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reimburseNo" label="报销单号" width="130" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="canWrite()">
              <el-button v-if="row.status === 'UPLOADED' || row.status === 'RISK'" link type="primary" size="small" @click="check(row)">合规检查</el-button>
            </template>
            <template v-if="canApprove()">
              <el-button v-if="!['REIMBURSED','REJECTED'].includes(row.status)" link type="danger" size="small" @click="rejectRow(row)">驳回</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" /></div>
    </div>

    <el-dialog v-model="visible" title="上传费用发票" width="700px" destroy-on-close>
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="8"><el-form-item label="员工姓名" required><el-input v-model="form.employeeName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="工号"><el-input v-model="form.employeeNo" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="部门"><el-input v-model="form.department" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="票种"><el-select v-model="form.invoiceType" style="width:100%"><el-option v-for="o in INVOICE_TYPES" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="发票代码"><el-input v-model="form.invoiceCode" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="发票号码" required><el-input v-model="form.invoiceNo" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="开票日期" required><el-date-picker v-model="form.issueDate" type="date" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="销售方"><el-input v-model="form.sellerName" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="买方税号"><el-input v-model="form.buyerTaxNo" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="不含税"><el-input-number v-model="form.totalAmount" :precision="2" :controls="false" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="税额"><el-input-number v-model="form.totalTax" :precision="2" :controls="false" style="width:100%" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="含税" required><el-input-number v-model="form.totalWithTax" :precision="2" :controls="false" style="width:100%" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item label="校验码"><el-input v-model="form.checkCode" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { expense } from '../../api'
import { canApprove, canWrite } from '../../auth'
import { INVOICE_TYPES, money } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'

const EXP_STATUS = [
  { label: '已上传', value: 'UPLOADED' }, { label: '合规', value: 'COMPLIANT' }, { label: '风险', value: 'RISK' },
  { label: '已报销', value: 'REIMBURSED' }, { label: '已驳回', value: 'REJECTED' }
]
const RISK_LABEL = {
  DUPLICATE: '重复', TITLE_MISMATCH: '抬头不符', EXPIRED: '超期',
  VERIFY_FAILED: '查验失败', AMOUNT_LIMIT: '超限额', WEEKEND: '周末', HOLIDAY: '节假日'
}

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const selected = ref([])
const form = ref({})
const query = reactive({ current: 1, size: 20, status: '', department: '', keyword: '' })

const compliantSelected = computed(() => selected.value.filter((r) => r.status === 'COMPLIANT'))

const risks = (row) => { try { return JSON.parse(row.riskItems || '[]') } catch { return [] } }

async function load() {
  loading.value = true
  try {
    const p = await expense.page(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

function openForm() {
  form.value = { invoiceType: 'NORMAL', issueDate: new Date().toISOString().substring(0, 10) }
  visible.value = true
}

async function save() {
  saving.value = true
  try {
    await expense.upload(form.value)
    ElMessage.success('已上传')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function check(row) {
  const r = await expense.check(row.id)
  ElMessage[r.status === 'COMPLIANT' ? 'success' : 'warning'](r.status === 'COMPLIANT' ? '合规检查通过' : '存在风险项')
  load()
}

async function checkAll() {
  const rs = await expense.checkAll()
  ElMessage.success(`检查完成 ${rs.length} 张`)
  load()
}

async function reimburseSelected() {
  const { value } = await ElMessageBox.prompt('报销单号', `报销 ${compliantSelected.value.length} 张费用票`, { inputPlaceholder: 'RB-2026-001' })
  if (!value) return
  await expense.reimburse({ ids: compliantSelected.value.map((r) => r.id), reimburseNo: value })
  ElMessage.success('已报销')
  load()
}

async function rejectRow(row) {
  const { value } = await ElMessageBox.prompt('驳回原因', `驳回费用票 ${row.invoiceNo}`, { inputPlaceholder: '原因' })
  await expense.reject(row.id, value)
  ElMessage.success('已驳回')
  load()
}

onMounted(load)
</script>
