<template>
  <CrudPage title="发票号段" :api="basic.stock" :columns="columns" :option-sources="{ taxEntity: entityOptions }" :can-write="canBasicWrite()" />
</template>

<script setup>
import { onMounted, ref } from 'vue'
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { canBasicWrite } from '../../auth'
import { statusCol, INVOICE_TYPES } from '../../composables/useOptions'

const entityOptions = ref([])
onMounted(async () => {
  entityOptions.value = (await basic.taxEntity.list()).map((e) => ({ label: `${e.code} ${e.name}`, value: e.id }))
})

const columns = [
  { prop: 'taxEntityId', label: '纳税主体', type: 'select', required: true, filter: true, options: 'taxEntity', width: 160 },
  { prop: 'invoiceType', label: '票种', type: 'select', required: true, filter: true, width: 100, options: INVOICE_TYPES.filter((t) => t.value !== 'ALL_ELECTRIC' && t.value !== 'OTHER') },
  { prop: 'invoiceCode', label: '发票代码', width: 120 },
  { prop: 'startNo', label: '起始号', width: 100 },
  { prop: 'endNo', label: '截止号', width: 100 },
  { prop: 'currentNo', label: '当前号', hideInForm: true, width: 100 },
  { prop: 'remaining', label: '剩余', hideInForm: true, width: 80 },
  statusCol
]
</script>
