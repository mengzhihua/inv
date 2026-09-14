<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="状态" clearable style="width:120px" @change="load">
          <el-option label="草稿" value="DRAFT" /><el-option label="已确认" value="CONFIRMED" /><el-option label="已使用" value="USED" />
        </el-select>
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="redInfoNo" label="红字信息表编号" width="180"><template #default="{ row }">{{ row.redInfoNo || '-' }}</template></el-table-column>
        <el-table-column prop="invoiceId" label="原票ID" width="90" />
        <el-table-column label="原因" width="100"><template #default="{ row }"><StatusTag :value="row.reason" /></template></el-table-column>
        <el-table-column prop="totalAmount" label="不含税" width="110" align="right"><template #default="{ row }">{{ money(row.totalAmount) }}</template></el-table-column>
        <el-table-column prop="totalTax" label="税额" width="100" align="right"><template #default="{ row }">{{ money(row.totalTax) }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><StatusTag :value="row.status" /></template></el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="150"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <template v-if="canApprove()">
              <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="confirm(row)">确认</el-button>
              <el-button v-if="row.status === 'CONFIRMED'" link type="danger" size="small" @click="flush(row)">红冲开票</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" /></div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sales } from '../../api'
import { canApprove } from '../../auth'
import { money } from '../../composables/useOptions'
import { fmt } from '../../utils'
import StatusTag from '../../components/StatusTag.vue'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 20, status: '' })

async function load() {
  loading.value = true
  try {
    const p = await sales.redInfo.page(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

async function confirm(row) {
  await ElMessageBox.confirm('确认红字信息表（模拟税局审核）?', '确认', { type: 'warning' })
  const r = await sales.redInfo.confirm(row.id)
  ElMessage.success(`已确认，编号 ${r.redInfoNo}`)
  load()
}

async function flush(row) {
  await ElMessageBox.confirm('确认执行红冲，生成负数红字发票?', '红冲', { type: 'warning' })
  const inv = await sales.redInfo.redFlush(row.id)
  ElMessage.success(`红字发票已开具：${inv.invoiceNo}`)
  load()
}

onMounted(load)
</script>
