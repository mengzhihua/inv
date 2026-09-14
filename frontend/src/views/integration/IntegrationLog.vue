<template>
  <div class="page">
    <div class="card">
      <div class="toolbar">
        <el-select v-model="query.target" placeholder="对接系统" clearable @change="load" style="width: 130px">
          <el-option label="WMS" value="WMS" /><el-option label="TMS" value="TMS" /><el-option label="渠道" value="CHANNEL" />
        </el-select>
        <el-select v-model="query.direction" placeholder="方向" clearable @change="load" style="width: 110px">
          <el-option label="入站(IN)" value="IN" /><el-option label="出站(OUT)" value="OUT" />
        </el-select>
        <el-select v-model="query.success" placeholder="结果" clearable @change="load" style="width: 110px">
          <el-option label="成功" :value="1" /><el-option label="失败" :value="0" />
        </el-select>
        <el-input v-model="query.action" placeholder="动作" clearable @keyup.enter="load" @clear="load" style="width: 150px" />
        <el-input v-model="query.refNo" placeholder="关联单号" clearable @keyup.enter="load" @clear="load" style="width: 170px" />
        <el-button type="primary" @click="load"><el-icon><Search /></el-icon>查询</el-button>
      </div>
      <el-table :data="rows" v-loading="loading" border stripe size="small">
        <el-table-column prop="createdAt" label="时间" width="160"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
        <el-table-column prop="direction" label="方向" width="70"><template #default="{ row }"><StatusTag :value="row.direction" /></template></el-table-column>
        <el-table-column prop="target" label="系统" width="80"><template #default="{ row }"><StatusTag :value="row.target" /></template></el-table-column>
        <el-table-column prop="action" label="动作" width="150" />
        <el-table-column prop="refNo" label="关联单号" width="170" />
        <el-table-column prop="success" label="结果" width="70"><template #default="{ row }"><el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag></template></el-table-column>
        <el-table-column prop="errorMsg" label="错误" min-width="160" show-overflow-tooltip />
        <el-table-column label="报文" width="80"><template #default="{ row }"><el-button link type="primary" size="small" @click="detail = row">查看</el-button></template></el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
      </div>
    </div>
    <el-dialog :model-value="!!detail" title="接口报文" width="760px" @close="detail = null" destroy-on-close>
      <template v-if="detail">
        <h4>请求</h4><pre class="json">{{ pretty(detail.requestBody) }}</pre>
        <h4>响应</h4><pre class="json">{{ pretty(detail.responseBody) }}</pre>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { integration } from '../../api'
import StatusTag from '../../components/StatusTag.vue'
import { fmt } from '../../utils'

const rows = ref([])
const total = ref(0)
const loading = ref(false)
const detail = ref(null)
const query = reactive({ current: 1, size: 20, target: '', direction: '', success: undefined, action: '', refNo: '' })
const pretty = (s) => { if (!s) return '-'; try { return JSON.stringify(JSON.parse(s), null, 2) } catch { return s } }

async function load() {
  loading.value = true
  try {
    const p = await integration.logPage(query)
    rows.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.json { background: #f5f7fa; padding: 10px; border-radius: 4px; max-height: 260px; overflow: auto; font-size: 12px; }
h4 { margin: 8px 0; }
</style>
