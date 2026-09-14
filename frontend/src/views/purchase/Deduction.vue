<template>
  <div class="page">
    <div class="card">
      <h3 style="margin-top: 0">抵扣确认</h3>
      <el-form inline>
        <el-form-item label="纳税主体">
          <el-select v-model="entityId" style="width:240px"><el-option v-for="o in entities" :key="o.value" :label="o.label" :value="o.value" /></el-select>
        </el-form-item>
        <el-form-item label="属期">
          <el-date-picker v-model="period" type="month" value-format="YYYY-MM" style="width:150px" />
        </el-form-item>
        <el-form-item v-if="canApprove()"><el-button type="primary" @click="confirm">确认抵扣本期已勾选发票</el-button></el-form-item>
      </el-form>
      <el-alert v-if="result" type="success" :closable="false" style="margin-bottom: 12px"
        :title="`已生成抵扣批次 ${result.batchNo}：${result.invoiceCount} 张，不含税 ${money(result.totalAmount)}，税额 ${money(result.totalTax)}`" />
      <el-table :data="batches" v-loading="loading" border stripe size="small">
        <el-table-column prop="batchNo" label="批次号" width="160" />
        <el-table-column prop="period" label="属期" width="90" />
        <el-table-column prop="invoiceCount" label="张数" width="80" align="right" />
        <el-table-column prop="totalAmount" label="不含税合计" width="130" align="right"><template #default="{ row }">{{ money(row.totalAmount) }}</template></el-table-column>
        <el-table-column prop="totalTax" label="税额合计" width="120" align="right"><template #default="{ row }">{{ money(row.totalTax) }}</template></el-table-column>
        <el-table-column prop="createdAt" label="时间" width="160"><template #default="{ row }">{{ fmt(row.createdAt) }}</template></el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" @change="load" /></div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { basic, purchase } from '../../api'
import { canApprove } from '../../auth'
import { money } from '../../composables/useOptions'
import { fmt } from '../../utils'

const entities = ref([])
const entityId = ref(null)
const period = ref(new Date().toISOString().substring(0, 7))
const batches = ref([])
const total = ref(0)
const loading = ref(false)
const result = ref(null)
const query = reactive({ current: 1, size: 20 })

async function load() {
  loading.value = true
  try {
    const p = await purchase.deduction.page(query)
    batches.value = p.records
    total.value = p.total
  } finally {
    loading.value = false
  }
}

async function confirm() {
  if (!entityId.value) return ElMessage.warning('请选择纳税主体')
  result.value = await purchase.deduction.confirm({ taxEntityId: entityId.value, period: period.value })
  ElMessage.success('抵扣确认完成')
  load()
}

onMounted(async () => {
  entities.value = (await basic.taxEntity.list()).map((e) => ({ label: `${e.code} ${e.name}`, value: e.id }))
  entityId.value = entities.value[0]?.value
  load()
})
</script>
