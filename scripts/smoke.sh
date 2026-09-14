#!/usr/bin/env bash
# INV 端到端冒烟：登录 -> 建客户/商品 -> 开票申请 -> 提交 -> 审核 -> 开票 -> 交付 -> 红冲
#   -> 进项录入 -> 查验 -> 勾选 -> 确认抵扣 -> 费用票上传 -> 合规检查 -> 增值税预填
# 用法: scripts/smoke.sh [BASE_URL] ; 需设置 INV_ADMIN_PASSWORD / INV_OPEN_API_KEY
set -euo pipefail
BASE="${1:-http://localhost:8080}/api"
J='Content-Type: application/json'
PASS="${INV_ADMIN_PASSWORD:?需设置 INV_ADMIN_PASSWORD}"
API_KEY="${INV_OPEN_API_KEY:?需设置 INV_OPEN_API_KEY}"
need(){ command -v "$1" >/dev/null || { echo "missing $1"; exit 1; }; }
need curl; need jq; need python3
TOKEN=""
call(){ local out; out=$(curl -s -X "$1" "$BASE$2" -H "$J" -H "Authorization: Bearer $TOKEN" ${3:+-d "$3"}); [ "$(echo "$out"|jq -r .code)" = "0" ] || { echo "FAIL $1 $2 -> $out"; exit 1; }; echo "$out"|jq -c .data; }
open(){ local out; out=$(curl -s -X "$1" "$BASE/open$2" -H "$J" -H "X-Api-Key: $API_KEY" ${3:+-d "$3"}); [ "$(echo "$out"|jq -r .code)" = "0" ] || { echo "FAIL OPEN $1 $2 -> $out"; exit 1; }; echo "$out"|jq -c .data; }
TS=$(date +%s); TODAY=$(date +%F); PERIOD=$(date +%Y-%m)

echo "== 1 auth"
test "$(curl -s "$BASE/report/dashboard" | jq -r .code)" = "401"
TOKEN=$(call POST /auth/login "{\"username\":\"admin\",\"password\":\"$PASS\"}" | jq -r .token); test -n "$TOKEN"
test "$(call GET /auth/me | jq -r .role)" = "ADMIN"

echo "== 2 master data"
ENTITY=$(call GET '/basic/tax-entity/list?taxNo=91310000780000001A' | jq -r '.[0].id')
CUST=$(call POST /basic/partner "{\"type\":\"CUSTOMER\",\"name\":\"冒烟客户$TS\",\"taxNo\":\"91310000SMOKE00${TS: -2}X\",\"email\":\"smoke$TS@example.com\"}")
CID=$(echo "$CUST"|jq -r .id)
G=$(call POST /basic/goods "{\"code\":\"SMK$TS\",\"name\":\"冒烟商品\",\"taxCategoryCode\":\"1090511010000000000\",\"taxRate\":0.13,\"price\":100}")
GID=$(echo "$G"|jq -r .id)

echo "== 3 sales: request -> submit -> approve -> issue -> deliver"
REQ=$(call POST /sales/request "{\"request\":{\"taxEntityId\":$ENTITY,\"customerId\":$CID,\"invoiceType\":\"E_NORMAL\",\"buyerName\":\"冒烟客户$TS\",\"buyerTaxNo\":\"91310000SMOKE00${TS: -2}X\"},\"lines\":[{\"goodsId\":$GID,\"goodsName\":\"冒烟商品\",\"quantity\":2,\"unitPrice\":500,\"taxRate\":0.13}]}")
RID=$(echo "$REQ"|jq -r .id); test "$(echo "$REQ"|jq -r .status)" = "DRAFT"
test "$(echo "$REQ"|jq -r .totalWithTax)" = "1130"
call POST "/sales/request/$RID/submit" >/dev/null
call POST "/sales/request/$RID/approve" >/dev/null
INV=$(call POST /sales/invoice/issue "{\"requestId\":$RID}")
INVID=$(echo "$INV"|jq -r .id); INVNO=$(echo "$INV"|jq -r .invoiceNo)
test "$(echo "$INV"|jq -r .status)" = "ISSUED"
test "$(echo "$INV"|jq -r .deliveryStatus)" = "UNDELIVERED"
call POST "/sales/invoice/$INVID/deliver" "{\"channel\":\"EMAIL\",\"target\":\"smoke$TS@example.com\"}" >/dev/null
test "$(call GET "/sales/invoice/$INVID" | jq -r .invoice.deliveryStatus)" = "DELIVERED"

echo "== 4 red flush (full)"
RED=$(call POST /sales/red-info "{\"invoiceId\":$INVID,\"reason\":\"RETURN\"}")
REDID=$(echo "$RED"|jq -r .id)
call POST "/sales/red-info/$REDID/confirm" | jq -r .redInfoNo | grep -qE '^[0-9]{16}$'
REDINV=$(call POST "/sales/red-info/$REDID/red-flush")
test "$(echo "$REDINV"|jq -r .status)" = "RED"; test "$(echo "$REDINV"|jq -r .totalWithTax)" = "-1130"
test "$(call GET "/sales/invoice/$INVID" | jq -r .invoice.status)" = "RED_FLUSHED"

echo "== 5 open api idempotent push"
test "$(curl -s -X POST "$BASE/open/invoice-requests" -H "$J" -H 'X-Api-Key: wrong' -d '{}' | jq -r .code)" = "401"
P=$(open POST /invoice-requests "{\"request\":{\"source\":\"OMS\",\"extRef\":\"SO$TS\",\"taxEntityId\":$ENTITY,\"invoiceType\":\"E_NORMAL\",\"buyerName\":\"冒烟客户$TS\",\"buyerTaxNo\":\"91310000SMOKE00${TS: -2}X\"},\"lines\":[{\"goodsName\":\"冒烟商品\",\"amount\":100,\"taxRate\":0.13}]}")
REQNO=$(echo "$P"|jq -r .requestNo)
test "$(open POST /invoice-requests "{\"request\":{\"source\":\"OMS\",\"extRef\":\"SO$TS\",\"taxEntityId\":$ENTITY,\"invoiceType\":\"E_NORMAL\",\"buyerName\":\"x\"},\"lines\":[{\"goodsName\":\"y\",\"amount\":1,\"taxRate\":0.13}]}" | jq -r .requestNo)" = "$REQNO"
test "$(open GET "/invoice-requests/OMS/SO$TS" | jq -r .status)" = "DRAFT"

echo "== 6 purchase: input -> verify -> check -> confirm deduction"
CC=$(python3 -c "
import hashlib
seed='03'+'%010d'%int('$TS')+'|IN$TS|$TODAY|1000.00'
h=hashlib.sha256(seed.encode()).digest()
d=''.join(c for b in h for c in format(b,'x') if c.isdigit())
print(d[:20])")
IN=$(call POST /purchase/input "{\"invoice\":{\"invoiceType\":\"E_SPECIAL\",\"invoiceCode\":\"03${TS: -10}\",\"invoiceNo\":\"IN$TS\",\"issueDate\":\"$TODAY\",\"sellerName\":\"恒信电子元件有限公司\",\"sellerTaxNo\":\"91320000MA5K00005X\",\"buyerName\":\"云途科技股份有限公司\",\"buyerTaxNo\":\"91310000780000001A\",\"totalAmount\":1000.00,\"totalTax\":130.00,\"totalWithTax\":1130.00,\"checkCode\":\"$CC\"},\"source\":\"MANUAL\"}")
INID=$(echo "$IN"|jq -r .id)
test "$(echo "$IN"|jq -r .taxEntityId)" = "$ENTITY"
test "$(call POST "/purchase/input/$INID/verify" | jq -r .verifyStatus)" = "VERIFIED"
# 重复录入被查重拦截
DUP=$(curl -s -X POST "$BASE/purchase/input" -H "$J" -H "Authorization: Bearer $TOKEN" -d "{\"invoice\":{\"invoiceType\":\"E_SPECIAL\",\"invoiceCode\":\"03${TS: -10}\",\"invoiceNo\":\"IN$TS\",\"issueDate\":\"$TODAY\",\"totalAmount\":1,\"totalTax\":0,\"totalWithTax\":1}}")
test "$(echo "$DUP"|jq -r .code)" = "400"; echo "$DUP"|jq -r .msg | grep -q DUPLICATE
test "$(call POST "/purchase/input/$INID/check" "{\"period\":\"$PERIOD\"}" | jq -r .deductStatus)" = "CHECKED"
BAT=$(call POST /purchase/deduction/confirm "{\"taxEntityId\":$ENTITY,\"period\":\"$PERIOD\"}")
test "$(echo "$BAT"|jq -r .totalTax)" = "130"
call POST "/purchase/input/$INID/match" "{\"poNo\":\"PO$TS\",\"receiptNo\":\"RC$TS\",\"amount\":1130.00}" | jq -r '.matchStatus' | grep -q MATCHED
call POST "/purchase/input/$INID/post" | jq -r .accountStatus | grep -q POSTED

echo "== 7 expense: upload -> compliance -> reimburse"
EXP=$(call POST /expense "{\"employeeName\":\"冒烟员工\",\"employeeNo\":\"E$TS\",\"department\":\"测试部\",\"invoiceType\":\"NORMAL\",\"invoiceNo\":\"EX$TS\",\"issueDate\":\"$TODAY\",\"sellerName\":\"某商户\",\"buyerTaxNo\":\"91310000780000001A\",\"totalAmount\":100,\"totalTax\":13,\"totalWithTax\":113}")
EXID=$(echo "$EXP"|jq -r .id)
test "$(call POST "/expense/$EXID/compliance-check" | jq -r .status)" = "COMPLIANT"
test "$(call POST /expense/reimburse "{\"ids\":[$EXID],\"reimburseNo\":\"RB$TS\"}" | jq -r '.[0].status')" = "REIMBURSED"

echo "== 8 tax vat-return"
VAT=$(call GET "/tax/vat-return?entity=$ENTITY&period=$PERIOD")
test "$(echo "$VAT"|jq -r .inputTax)" = "130"
test "$(echo "$VAT"|jq -r .period)" = "$PERIOD"
call GET "/tax/vat-return/preview?entity=$ENTITY&period=$PERIOD&additionalInputTax=10" | jq -r .burdenRate >/dev/null

echo "== 9 report & archive"
test "$(call GET /report/dashboard | jq -r .todayInvoices)" -ge 1
test "$(call GET '/report/sales-summary?dimension=type' | jq 'length')" -ge 1
test "$(call GET '/integration/archive/page?size=5' | jq '.records|length')" -ge 1
test "$(call GET '/integration/log/page?size=5' | jq '.records|length')" -ge 1

echo "SMOKE OK: request=$REQNO invoice=$INVNO red=$REDINV input=$INID batch=$(echo "$BAT"|jq -r .batchNo)"
