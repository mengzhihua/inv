import { ref } from 'vue'
import { basic } from '../api'

/** 页面级一次性加载基础数据下拉项 */
export function useOptions(kinds) {
  const options = ref({})
  const loaders = {
    taxEntity: async () => (await basic.taxEntity.list({ status: 1 })).map((e) => ({ label: `${e.code} ${e.name}`, value: e.id, taxNo: e.taxNo })),
    customer: async () => (await basic.partner.list({ status: 1 })).filter((p) => p.type !== 'SUPPLIER').map((p) => ({ label: p.name, value: p.id, taxNo: p.taxNo })),
    supplier: async () => (await basic.partner.list({ status: 1 })).filter((p) => p.type !== 'CUSTOMER').map((p) => ({ label: p.name, value: p.id, taxNo: p.taxNo })),
    partner: async () => (await basic.partner.list({ status: 1 })).map((p) => ({ label: p.name, value: p.id })),
    goods: async () => (await basic.goods.list({ status: 1 })).map((g) => ({ label: `${g.name}(${g.code})`, value: g.id, goods: g }))
  }
  async function reload() {
    const out = {}
    await Promise.all(kinds.map(async (k) => { out[k] = await loaders[k]() }))
    options.value = out
  }
  reload()
  return { options, reload }
}

export const statusCol = { prop: 'status', label: '状态', type: 'status', width: 80, default: 1 }

export const INVOICE_TYPES = [
  { label: '专用发票', value: 'SPECIAL' }, { label: '普通发票', value: 'NORMAL' },
  { label: '电子普票', value: 'E_NORMAL' }, { label: '电子专票', value: 'E_SPECIAL' },
  { label: '数电票', value: 'ALL_ELECTRIC' }, { label: '其他', value: 'OTHER' }
]
export const TAX_RATES = [0, 0.01, 0.03, 0.05, 0.06, 0.09, 0.13].map((v) => ({ label: `${v * 100}%`, value: v }))
export const REQUEST_STATUS = [
  { label: '草稿', value: 'DRAFT' }, { label: '已提交', value: 'SUBMITTED' }, { label: '已审核', value: 'APPROVED' },
  { label: '已开票', value: 'ISSUED' }, { label: '已退回', value: 'REJECTED' }, { label: '已取消', value: 'CANCELLED' }
]
export const VERIFY_STATUS = [
  { label: '未查验', value: 'UNVERIFIED' }, { label: '查验通过', value: 'VERIFIED' }, { label: '查验失败', value: 'FAILED' }
]
export const DEDUCT_STATUS = [
  { label: '待勾选', value: 'PENDING' }, { label: '已勾选', value: 'CHECKED' },
  { label: '已抵扣', value: 'DEDUCTED' }, { label: '不抵扣', value: 'NOT_DEDUCT' }
]
export const RED_REASONS = [
  { label: '买方拒收', value: 'BUYER_REJECT' }, { label: '开票有误', value: 'SELLER_ERROR' },
  { label: '服务中止', value: 'SERVICE_STOP' }, { label: '销售退回', value: 'RETURN' }
]

export const labelOf = (list, v) => list.find((o) => o.value === v)?.label ?? v
export const money = (v) => (v === null || v === undefined ? '' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }))
/** 兼容 H2(大写列名) / MySQL(原样) 的 Map 结果取值 */
export const g = (row, k) => (row ? row[k] ?? row[k.toUpperCase()] ?? row[k.toLowerCase()] : undefined)
