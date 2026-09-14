import http from './request'

export const crud = (base) => ({
  page: (params) => http.get(`${base}/page`, { params }),
  list: (params) => http.get(`${base}/list`, { params }),
  get: (id) => http.get(`${base}/${id}`),
  create: (data) => http.post(base, data),
  update: (id, data) => http.put(`${base}/${id}`, data),
  remove: (id) => http.delete(`${base}/${id}`)
})

export const basic = {
  taxEntity: crud('/basic/tax-entity'),
  taxEntityLimits: (id) => http.get(`/basic/tax-entity/${id}/limits`),
  saveLimit: (id, data) => http.post(`/basic/tax-entity/${id}/limits`, data),
  removeLimit: (limitId) => http.delete(`/basic/tax-entity/limits/${limitId}`),
  partner: crud('/basic/partner'),
  goods: crud('/basic/goods'),
  stock: crud('/basic/invoice-stock')
}

export const sales = {
  request: {
    page: (params) => http.get('/sales/request/page', { params }),
    get: (id) => http.get(`/sales/request/${id}`),
    create: (data) => http.post('/sales/request', data),
    update: (id, data) => http.put(`/sales/request/${id}`, data),
    submit: (id) => http.post(`/sales/request/${id}/submit`),
    approve: (id) => http.post(`/sales/request/${id}/approve`),
    reject: (id, reason) => http.post(`/sales/request/${id}/reject`, { reason }),
    cancel: (id) => http.post(`/sales/request/${id}/cancel`),
    split: (id) => http.post(`/sales/request/${id}/split`),
    merge: (ids) => http.post('/sales/request/merge', { ids }),
    batchApprove: (ids) => http.post('/sales/request/batch-approve', { ids })
  },
  invoice: {
    page: (params) => http.get('/sales/invoice/page', { params }),
    get: (id) => http.get(`/sales/invoice/${id}`),
    issue: (requestId) => http.post('/sales/invoice/issue', { requestId }),
    batchIssue: (requestIds) => http.post('/sales/invoice/batch-issue', { requestIds }),
    cancel: (id, reason) => http.post(`/sales/invoice/${id}/cancel`, { reason }),
    deliver: (id, data) => http.post(`/sales/invoice/${id}/deliver`, data),
    batchDeliver: (data) => http.post('/sales/invoice/batch-deliver', data)
  },
  redInfo: {
    page: (params) => http.get('/sales/red-info/page', { params }),
    create: (data) => http.post('/sales/red-info', data),
    confirm: (id) => http.post(`/sales/red-info/${id}/confirm`),
    redFlush: (id) => http.post(`/sales/red-info/${id}/red-flush`)
  }
}

export const purchase = {
  input: {
    page: (params) => http.get('/purchase/input/page', { params }),
    get: (id) => http.get(`/purchase/input/${id}`),
    create: (data) => http.post('/purchase/input', data),
    scan: (data) => http.post('/purchase/input/scan', data),
    importCsv: (file) => {
      const fd = new FormData()
      fd.append('file', file)
      return http.post('/purchase/input/import', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    },
    verify: (id) => http.post(`/purchase/input/${id}/verify`),
    batchVerify: (ids) => http.post('/purchase/input/batch-verify', { ids }),
    check: (id, period) => http.post(`/purchase/input/${id}/check`, { period }),
    uncheck: (id) => http.post(`/purchase/input/${id}/uncheck`),
    notDeduct: (id, reason) => http.post(`/purchase/input/${id}/not-deduct`, { reason }),
    match: (id, data) => http.post(`/purchase/input/${id}/match`, data),
    post: (id) => http.post(`/purchase/input/${id}/post`)
  },
  deduction: {
    page: (params) => http.get('/purchase/deduction/page', { params }),
    confirm: (data) => http.post('/purchase/deduction/confirm', data)
  }
}

export const expense = {
  page: (params) => http.get('/expense/page', { params }),
  get: (id) => http.get(`/expense/${id}`),
  upload: (data) => http.post('/expense', data),
  check: (id) => http.post(`/expense/${id}/compliance-check`),
  checkAll: (ids) => http.post('/expense/compliance-check', ids ? { ids } : null),
  reimburse: (data) => http.post('/expense/reimburse', data),
  reject: (id, reason) => http.post(`/expense/${id}/reject`, { reason })
}

export const tax = {
  periodPage: (params) => http.get('/tax/period/page', { params }),
  close: (data) => http.post('/tax/period/close', data),
  open: (data) => http.post('/tax/period/open', data),
  vatReturn: (entity, period) => http.get('/tax/vat-return', { params: { entity, period } }),
  preview: (params) => http.get('/tax/vat-return/preview', { params })
}

export const integration = {
  logPage: (params) => http.get('/integration/log/page', { params }),
  archivePage: (params) => http.get('/integration/archive/page', { params }),
  archiveExport: (params) => downloadCsv('/integration/archive/export', params, 'archive.csv')
}

export const report = {
  dashboard: () => http.get('/report/dashboard'),
  salesSummary: (params) => http.get('/report/sales-summary', { params }),
  inputSummary: (params) => http.get('/report/input-summary', { params }),
  redCancel: () => http.get('/report/red-cancel-summary'),
  customerRank: (params) => http.get('/report/customer-rank', { params })
}

export const system = {
  user: crud('/system/user'),
  oplogPage: (params) => http.get('/system/oplog/page', { params })
}

export const authApi = {
  login: (data) => http.post('/auth/login', data),
  me: () => http.get('/auth/me'),
  logout: () => http.post('/auth/logout'),
  changePassword: (data) => http.post('/auth/password', data)
}

/** 带 token 下载后端 CSV（使用 blob，避开 URL 中传 token） */
export async function downloadCsv(url, params, filename) {
  const blob = await http.get(url, { params, responseType: 'blob' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = filename
  a.click()
  URL.revokeObjectURL(a.href)
}
