<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-date-picker v-model="query.month" type="month" value-format="YYYY-MM" placeholder="归档月份" style="width:150px" @change="load" />
        <el-select v-model="query.direction" placeholder="方向" clearable style="width:110px" @change="load"><el-option label="销项" value="OUT" /><el-option label="进项" value="IN" /></el-select>
        <el-input v-model="query.invoiceNo" placeholder="发票号码" clearable style="width:150px" @keyup.enter="load" @clear="load" />
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
        <el-button @click="integration.archiveExport({ month: query.month || undefined })"><el-icon><Download /></el-icon>导出清单</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="invoiceNo" label="发票号码" width="170" />
        <el-table-column label="票种" width="110"><template #default="{ row }"><StatusTag :value="row.invoiceType" /></template></el-table-column>
        <el-table-column label="方向" width="70"><template #default="{ row }"><el-tag size="small" :type="row.direction === 'OUT' ? 'success' : 'primary'">{{ row.direction === 'OUT' ? '销项' : '进项' }}</el-tag></template></el-table-column>
        <el-table-column prop="issueMonth" label="月份" width="90" />
        <el-table-column prop="pdfUrl" label="PDF" min-width="160"><template #default="{ row }"><el-text size="small" type="info">{{ row.pdfUrl }}</el-text></template></el-table-column>
        <el-table-column prop="ofdUrl" label="OFD" min-width="160"><template #default="{ row }"><el-text size="small" type="info">{{ row.ofdUrl }}</el-text></template></el-table-column>
        <el-table-column prop="meta" label="摘要" min-width="200" show-overflow-tooltip />
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" /></div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { integration } from '../../api'
import StatusTag from '../../components/StatusTag.vue'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ current: 1, size: 20, month: '', direction: '', invoiceNo: '' })

async function load() {
  loading.value = true
  try {
    const p = await integration.archivePage(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>
