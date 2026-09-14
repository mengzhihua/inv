<template>
  <CrudPage title="商品/服务" :api="basic.goods" :columns="columns" :can-write="canBasicWrite()" />
</template>

<script setup>
import CrudPage from '../../components/CrudPage.vue'
import { basic } from '../../api'
import { canBasicWrite } from '../../auth'
import { statusCol, TAX_RATES } from '../../composables/useOptions'

const columns = [
  { prop: 'code', label: '编码', required: true, readonlyOnEdit: true, width: 100 },
  { prop: 'name', label: '名称', required: true, minWidth: 150 },
  { prop: 'taxCategoryCode', label: '税收分类编码', width: 175 },
  { prop: 'taxCategoryName', label: '分类名称', width: 120 },
  { prop: 'spec', label: '规格', width: 100 },
  { prop: 'unit', label: '单位', width: 70 },
  { prop: 'price', label: '单价(不含税)', type: 'number', precision: 2, width: 110 },
  { prop: 'taxRate', label: '税率', type: 'select', required: true, filter: true, width: 90, default: 0.13, options: TAX_RATES },
  { prop: 'preferentialPolicy', label: '优惠政策', type: 'select', width: 100, default: 'NONE',
    options: [{ label: '无', value: 'NONE' }, { label: '免税', value: 'FREE' }, { label: '零税率', value: 'ZERO' }] },
  statusCol
]
</script>
