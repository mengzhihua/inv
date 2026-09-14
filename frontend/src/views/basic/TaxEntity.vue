<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="编码/名称/税号" clearable @keyup.enter="load" @clear="load" />
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <el-button v-if="canEditMaster()" type="success" @click="openForm()"><el-icon><Plus /></el-icon>新增主体</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="code" label="编码" width="80" />
        <el-table-column prop="name" label="主体名称" min-width="180" />
        <el-table-column prop="taxNo" label="纳税人识别号" width="190" />
        <el-table-column label="类型" width="110"><template #default="{ row }"><StatusTag :value="row.taxpayerType" /></template></el-table-column>
        <el-table-column label="开票/收款/复核" min-width="160"><template #default="{ row }">{{ row.drawer }} / {{ row.payee }} / {{ row.reviewer }}</template></el-table-column>
        <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">{{ row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">编辑</el-button>
            <el-button link type="warning" size="small" @click="openLimits(row)">票种限额</el-button>
            <el-popconfirm v-if="canEditMaster()" title="确认删除?" @confirm="remove(row)">
              <template #reference><el-button link type="danger" size="small">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" /></div>
    </div>

    <el-dialog v-model="visible" :title="(form.id ? '编辑' : '新增') + '纳税主体'" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="编码" prop="code" :rules="[{ required: true, message: '编码不能为空' }]"><el-input v-model="form.code" :disabled="!!form.id" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="名称" prop="name" :rules="[{ required: true, message: '名称不能为空' }]"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="纳税人识别号" prop="taxNo" :rules="[{ required: true, message: '税号不能为空' }]"><el-input v-model="form.taxNo" placeholder="15/17/18/20 位" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="纳税人类型"><el-select v-model="form.taxpayerType" style="width:100%"><el-option label="一般纳税人" value="GENERAL" /><el-option label="小规模" value="SMALL" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="地址"><el-input v-model="form.address" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="开户行"><el-input v-model="form.bankName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="银行账号"><el-input v-model="form.bankAccount" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="开票人"><el-input v-model="form.drawer" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="收款人"><el-input v-model="form.payee" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="复核人"><el-input v-model="form.reviewer" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="状态"><el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="limitsVisible" :title="`票种开票限额 — ${limitEntity?.name || ''}`" width="560px" destroy-on-close>
      <el-table :data="limits" size="small" border>
        <el-table-column label="票种"><template #default="{ row }"><StatusTag :value="row.invoiceType" /></template></el-table-column>
        <el-table-column prop="maxAmount" label="单张开票限额" align="right"><template #default="{ row }">{{ money(row.maxAmount) }}</template></el-table-column>
        <el-table-column width="70"><template #default="{ row }">
          <el-popconfirm v-if="canEditMaster()" title="删除该限额?" @confirm="removeLimit(row)"><template #reference><el-button link type="danger" size="small">删除</el-button></template></el-popconfirm>
        </template></el-table-column>
      </el-table>
      <el-form v-if="canEditMaster()" inline style="margin-top: 12px">
        <el-form-item label="票种"><el-select v-model="newLimit.invoiceType" style="width: 130px"><el-option v-for="t in INVOICE_TYPES" :key="t.value" :label="t.label" :value="t.value" /></el-select></el-form-item>
        <el-form-item label="限额"><el-input-number v-model="newLimit.maxAmount" :min="0.01" :precision="2" /></el-form-item>
        <el-button type="primary" @click="addLimit">添加</el-button>
      </el-form>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { basic } from '../../api'
import { canEditMaster } from '../../auth'
import { INVOICE_TYPES, money } from '../../composables/useOptions'
import StatusTag from '../../components/StatusTag.vue'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const visible = ref(false)
const formRef = ref()
const form = ref({})
const query = reactive({ current: 1, size: 20, keyword: '' })

const limitsVisible = ref(false)
const limits = ref([])
const limitEntity = ref(null)
const newLimit = reactive({ invoiceType: 'SPECIAL', maxAmount: 100000 })

async function load() {
  loading.value = true
  try {
    const p = await basic.taxEntity.page(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  form.value = row ? { ...row } : { status: 1, taxpayerType: 'GENERAL' }
  visible.value = true
}

async function save() {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.value.id) await basic.taxEntity.update(form.value.id, form.value)
    else await basic.taxEntity.create(form.value)
    ElMessage.success('保存成功')
    visible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await basic.taxEntity.remove(row.id)
  ElMessage.success('已删除')
  load()
}

async function openLimits(row) {
  limitEntity.value = row
  limits.value = await basic.taxEntityLimits(row.id)
  limitsVisible.value = true
}

async function addLimit() {
  await basic.saveLimit(limitEntity.value.id, { ...newLimit })
  limits.value = await basic.taxEntityLimits(limitEntity.value.id)
  ElMessage.success('已添加')
}

async function removeLimit(row) {
  await basic.removeLimit(row.id)
  limits.value = await basic.taxEntityLimits(limitEntity.value.id)
}

onMounted(load)
</script>
