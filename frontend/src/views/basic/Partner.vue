<template>
  <CrudPage title="往来单位" :api="basic.partner" :columns="columns" :can-write="canBasicWrite()" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { canBasicWrite } from '../../auth'
import { statusCol } from '../../composables/useOptions'

const columns = [
  { prop: 'name', label: '名称', required: true, minWidth: 180 },
  { prop: 'type', label: '类型', type: 'select', required: true, filter: true, width: 100, default: 'CUSTOMER',
    options: [{ label: '客户', value: 'CUSTOMER' }, { label: '供应商', value: 'SUPPLIER' }, { label: '双向', value: 'BOTH' }] },
  { prop: 'taxNo', label: '纳税人识别号', width: 190 },
  { prop: 'address', label: '地址', hideInTable: true, span: 24 },
  { prop: 'phone', label: '电话', width: 140 },
  { prop: 'bankName', label: '开户行', hideInTable: true },
  { prop: 'bankAccount', label: '银行账号', hideInTable: true },
  { prop: 'email', label: '邮箱（交付用）', width: 170 },
  { prop: 'mobile', label: '手机（交付用）', width: 130 },
  statusCol
]
</script>
