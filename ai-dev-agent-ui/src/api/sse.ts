import { fetchEventSource } from '@microsoft/fetch-event-source'
import { BASE_URL } from './request'

// SSE 流式请求的通用处理参数
export interface SseHandlers {
  // 收到一条数据（done=true 表示流结束 [DONE] 标记）
  onData: (data: string, done: boolean) => void
  onError?: (error: any) => void
  onClose?: () => void
}

/**
 * 发起 SSE 流式 POST 请求（fetchEventSource 实现，支持 POST + 自定义 header）
 * 登录后携带 Bearer token，供后端鉴权拦截器放行。
 *
 * @param url    接口地址（相对 BASE_URL）
 * @param body   请求体对象
 * @param handlers 回调
 * @param ctrl   中断控制器（可选，用于停止生成）
 */
export const fetchSseStream = async (
  url: string,
  body: object,
  handlers: SseHandlers,
  ctrl?: AbortController
) => {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json'
  }
  const token = localStorage.getItem('cf-token')
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  return fetchEventSource(`${BASE_URL}${url}`, {
    method: 'post',
    headers,
    openWhenHidden: true, // 浏览器标签页切后台时保持连接，避免 SSE 被断开
    body: JSON.stringify(body),
    // 校验响应：后端异常时（如参数校验失败）会返回 JSON 而非 SSE 流
    async onopen(response) {
      const contentType = response.headers.get('content-type') || ''
      if (response.ok && contentType.includes('text/event-stream')) {
        return // 正常的 SSE 流
      }
      // 读取后端返回的错误信息，抛出后由 onerror 接收展示
      let errorMsg = `HTTP ${response.status}`
      try {
        const bodyText = await response.text()
        try {
          const json = JSON.parse(bodyText)
          errorMsg += `：${json.msg || bodyText}`
        } catch {
          errorMsg += `：${bodyText}`
        }
      } catch {
        // 读取失败则仅展示状态码
      }
      throw new Error(errorMsg)
    },
    onmessage: (ev) => {
      // [DONE] 结束标记不是 JSON，直接通知流结束
      if (ev.data === '[DONE]') {
        handlers.onData('', true)
        return
      }
      handlers.onData(ev.data, false)
    },
    onerror: (err) => {
      handlers.onError?.(err)
      // 抛出异常停止自动重连，避免请求被重复发送
      throw err
    },
    onclose: () => {
      handlers.onClose?.()
    },
    signal: ctrl?.signal
  })
}
