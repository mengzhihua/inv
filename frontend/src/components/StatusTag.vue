<template>
  <el-tag :type="meta.type" size="small" effect="light">{{ meta.text }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'

const DICT = {
  // 通用
  DRAFT: ['草稿', 'info'],
  CANCELLED: ['已取消', 'danger'],
  // 申请
  SUBMITTED: ['已提交', 'primary'],
  APPROVED: ['已审核', 'warning'],
  REJECTED: ['已退回', 'danger'],
  // 发票
  ISSUED: ['已开出', 'success'],
  RED_FLUSHED: ['已红冲', 'danger'],
  RED: ['红字票', 'danger'],
  // 交付
  UNDELIVERED: ['未交付', 'info'],
  DELIVERED: ['已交付', 'success'],
  // 票种
  SPECIAL: ['专票', 'primary'],
  NORMAL: ['普票', 'info'],
  E_NORMAL: ['电子普票', 'success'],
  E_SPECIAL: ['电子专票', 'primary'],
  ALL_ELECTRIC: ['数电票', 'warning'],
  OTHER: ['其他', 'info'],
  // 查验
  UNVERIFIED: ['未查验', 'info'],
  VERIFIED: ['查验通过', 'success'],
  FAILED: ['查验失败', 'danger'],
  // 发票状态
  ABNORMAL: ['异常', 'danger'],
  // 抵扣
  PENDING: ['待勾选', 'info'],
  CHECKED: ['已勾选', 'primary'],
  DEDUCTED: ['已抵扣', 'success'],
  NOT_DEDUCT: ['不抵扣', 'warning'],
  // 匹配
  UNMATCHED: ['未匹配', 'info'],
  MATCHED: ['已匹配', 'success'],
  MISMATCH: ['差异', 'danger'],
  // 入账
  UNPOSTED: ['未入账', 'info'],
  POSTED: ['已入账', 'success'],
  // 费用
  UPLOADED: ['已上传', 'info'],
  COMPLIANT: ['合规', 'success'],
  RISK: ['风险', 'danger'],
  REIMBURSED: ['已报销', 'success'],
  // 属期
  OPEN: ['开账', 'success'],
  CLOSED: ['关账', 'danger'],
  // 红冲原因/红字表
  BUYER_REJECT: ['买方拒收', 'warning'],
  SELLER_ERROR: ['开票有误', 'danger'],
  SERVICE_STOP: ['服务中止', 'info'],
  RETURN: ['销售退回', 'primary'],
  CONFIRMED: ['已确认', 'primary'],
  USED: ['已使用', 'success'],
  // 来源
  MANUAL: ['手工', 'info'],
  SCAN: ['扫描', 'warning'],
  IMPORT: ['导入', 'primary'],
  EMAIL: ['邮箱', 'success'],
  API: ['API', 'primary'],
  OMS: ['OMS', 'success'],
  BMS: ['BMS', 'warning'],
  SMS: ['短信', 'warning'],
  // 单位类型
  CUSTOMER: ['客户', 'success'],
  SUPPLIER: ['供应商', 'primary'],
  BOTH: ['双向', 'warning'],
  GENERAL: ['一般纳税人', 'primary'],
  SMALL: ['小规模', 'info'],
  // 集成
  IN: ['入站', 'success'],
  OUT: ['出站', 'primary']
}

const props = defineProps({ value: String })
const meta = computed(() => {
  const d = DICT[props.value]
  return d ? { text: d[0], type: d[1] } : { text: props.value ?? '-', type: 'info' }
})
</script>
