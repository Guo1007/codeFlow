import axios from 'axios'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

/**
 * 统一返回结构（与后端 common.ApiResponse 约定）
 */
export interface ApiResponse<T = any> {
  code: number // 0 = 成功
  data: T
  msg: string
}

/** 基础地址：经 Vite 代理转发到后端（见 vite.config.ts 的 /api 代理） */
export const BASE_URL = '/api'

/** 登录态：token 是否存在（localStorage key: cf-token）驱动，供 App.vue 切换登录/主界面 */
export const authState = ref<boolean>(!!localStorage.getItem('cf-token'))

// 裸 axios 实例（自 yudao config/axios 封装改写，仅保留 token 鉴权）
const service = axios.create({
  baseURL: BASE_URL,
  timeout: 60000
})

// 请求拦截器：注入登录 token
service.interceptors.request.use((config) => {
  const token = localStorage.getItem('cf-token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一解包 ApiResponse；401 时清 token 并切回登录态
service.interceptors.response.use(
  (response) => {
    if (response.status === 401) {
      handleUnauthorized()
      return Promise.reject(new Error(response.data?.msg || '未登录'))
    }
    const res = response.data as ApiResponse
    // 流式 / 文件等非标准结构直接放行
    if (res === null || res === undefined || typeof res.code !== 'number') {
      return response.data
    }
    if (res.code !== 0) {
      ElMessage.error(res.msg || '系统异常')
      return Promise.reject(new Error(res.msg || '系统异常'))
    }
    return res.data
  },
  (error) => {
    if (error?.response?.status === 401) {
      handleUnauthorized()
    }
    const msg = error?.response?.data?.msg || error.message || '网络异常'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

function handleUnauthorized() {
  localStorage.removeItem('cf-token')
  authState.value = false
}

/** GET 请求（泛型返回解包后的 data） */
export const get = async <T = any>(url: string, params?: object): Promise<T> => {
  return (await service.get(url, { params })) as T
}

/** POST 请求 */
export const post = async <T = any>(url: string, data?: object): Promise<T> => {
  return (await service.post(url, data)) as T
}

/** PUT 请求 */
export const put = async <T = any>(url: string, data?: object): Promise<T> => {
  return (await service.put(url, data)) as T
}

/** DELETE 请求 */
export const del = async <T = any>(url: string, params?: object): Promise<T> => {
  return (await service.delete(url, { params })) as T
}

export default service
