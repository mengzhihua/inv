<template>
  <div class="page">
    <div class="card">
      <h3 style="margin-top:0">开放接口说明（X-Api-Key 鉴权）</h3>
      <el-alert type="info" :closable="false" title="请求头携带 X-Api-Key: $INV_OPEN_API_KEY；所有接口幂等，source+extRef 重复推送返回原单" />
      <h4>1. 推送开票申请（OMS/BMS）</h4>
      <pre>curl -X POST http://localhost:8080/api/open/invoice-requests \
  -H 'Content-Type: application/json' -H "X-Api-Key: $INV_OPEN_API_KEY" \
  -d '{
    "request": {"source":"OMS","extRef":"SO-001","taxEntityId":1,"invoiceType":"E_NORMAL",
      "buyerName":"买方公司","buyerTaxNo":"91XXXXXXXXXXXXXXXX"},
    "lines": [{"goodsName":"软件开发服务","amount":10000.00,"taxRate":0.06}]
  }'
# 响应 {"requestNo":"REQ...","status":"DRAFT"}</pre>
      <h4>2. 查询申请状态</h4>
      <pre>curl http://localhost:8080/api/open/invoice-requests/OMS/SO-001 -H "X-Api-Key: $INV_OPEN_API_KEY"
# {"requestNo":"REQ...","status":"ISSUED","invoiceNo":"00003001"}</pre>
      <h4>3. 推送进项发票（SRM）</h4>
      <pre>curl -X POST http://localhost:8080/api/open/input-invoices \
  -H 'Content-Type: application/json' -H "X-Api-Key: $INV_OPEN_API_KEY" \
  -d '{"invoiceType":"SPECIAL","invoiceCode":"032002300001","invoiceNo":"10001001",
       "issueDate":"2026-09-14","sellerName":"供应商","sellerTaxNo":"91...",
       "buyerTaxNo":"91310000780000001A","totalAmount":1000,"totalTax":130,"totalWithTax":1130}'</pre>
      <h4>4. 电子档案查询</h4>
      <pre>curl 'http://localhost:8080/api/open/archive?month=2026-09&direction=OUT' -H "X-Api-Key: $INV_OPEN_API_KEY"</pre>
      <h4>票种枚举</h4>
      <el-table :data="types" size="small" border style="max-width: 500px">
        <el-table-column prop="v" label="值" width="140" /><el-table-column prop="l" label="含义" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
const types = [
  { v: 'SPECIAL', l: '增值税专用发票（纸质）' }, { v: 'NORMAL', l: '增值税普通发票（纸质）' },
  { v: 'E_NORMAL', l: '电子普通发票' }, { v: 'E_SPECIAL', l: '电子专用发票' },
  { v: 'ALL_ELECTRIC', l: '数电票（20 位号码，无代码）' }, { v: 'OTHER', l: '其他（火车票/出租车票等，仅费用）' }
]
</script>

<style scoped>
pre { background: #f5f7fa; padding: 12px; border-radius: 6px; font-size: 12px; overflow-x: auto; }
</style>
