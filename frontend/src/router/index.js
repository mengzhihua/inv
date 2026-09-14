import { createRouter, createWebHistory } from 'vue-router'
import Layout from '../layout/Layout.vue'
import { auth, isAdmin } from '../auth'

export const menus = [
  { path: '/dashboard', name: '工作台', icon: 'Odometer', component: () => import('../views/Dashboard.vue') },
  {
    path: '/sales', name: '销项管理', icon: 'Document',
    children: [
      { path: 'request', name: '开票申请', component: () => import('../views/sales/RequestList.vue') },
      { path: 'invoice', name: '发票查询', component: () => import('../views/sales/InvoiceList.vue') },
      { path: 'red-info', name: '红字信息表', component: () => import('../views/sales/RedInfoList.vue') }
    ]
  },
  {
    path: '/purchase', name: '进项管理', icon: 'FolderOpened',
    children: [
      { path: 'input', name: '进项发票', component: () => import('../views/purchase/InputList.vue') },
      { path: 'deduction', name: '抵扣确认', component: () => import('../views/purchase/Deduction.vue') }
    ]
  },
  { path: '/expense', name: '费用发票', icon: 'Wallet', component: () => import('../views/expense/ExpenseList.vue') },
  { path: '/tax', name: '税务管理', icon: 'Scale', component: () => import('../views/tax/Tax.vue') },
  {
    path: '/integration', name: '系统集成', icon: 'Connection',
    children: [
      { path: 'archive', name: '电子档案', component: () => import('../views/integration/Archive.vue') },
      { path: 'log', name: '集成日志', component: () => import('../views/integration/IntegrationLog.vue') },
      { path: 'openapi', name: '开放接口说明', component: () => import('../views/integration/OpenApiDoc.vue') }
    ]
  },
  {
    path: '/basic', name: '基础数据', icon: 'Setting',
    children: [
      { path: 'tax-entity', name: '纳税主体', component: () => import('../views/basic/TaxEntity.vue') },
      { path: 'partner', name: '往来单位', component: () => import('../views/basic/Partner.vue') },
      { path: 'goods', name: '商品/服务', component: () => import('../views/basic/Goods.vue') },
      { path: 'stock', name: '发票号段', component: () => import('../views/basic/InvoiceStock.vue') }
    ]
  },
  { path: '/report', name: '报表分析', icon: 'DataAnalysis', component: () => import('../views/report/Report.vue') },
  {
    path: '/system', name: '系统管理', icon: 'Tools', adminOnly: true,
    children: [
      { path: 'user', name: '用户管理', component: () => import('../views/system/User.vue') },
      { path: 'oplog', name: '操作日志', component: () => import('../views/system/OpLog.vue') }
    ]
  }
]

/** 当前用户可见菜单（adminOnly 菜单仅管理员可见；后端同样做了鉴权） */
export const visibleMenus = () => menus.filter((m) => !m.adminOnly || isAdmin())

const routes = [
  { path: '/login', name: '登录', component: () => import('../views/Login.vue') },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      ...menus.flatMap((m) =>
        m.children
          ? m.children.map((c) => ({ path: `${m.path}/${c.path}`, name: c.name, component: c.component }))
          : [{ path: m.path, name: m.name, component: m.component }]
      )
    ]
  }
]

const router = createRouter({ history: createWebHistory(), routes })

router.beforeEach((to) => {
  if (to.path === '/login') return auth.token ? '/dashboard' : true
  if (!auth.token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.path.startsWith('/system') && !isAdmin()) return '/dashboard'
  return true
})

export default router
