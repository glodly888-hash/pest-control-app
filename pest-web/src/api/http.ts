import axios from 'axios'

/**
 * 请求层骨架。
 * 后端 54 项接口统一走 /api/v1，鉴权用 Bearer accessToken，
 * 刷新令牌只存摘要并绑定设备会话（后端架构第 9 节）。
 */
const http = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (resp) => resp,
  async (error) => {
    // 428 = 缺少 If-Match 版本；412 = 版本冲突。统一交给调用方处理，不静默重试。
    if (error.response?.status === 401) {
      localStorage.removeItem('access_token')
      // 401 说明 token 失效/未登录，跳回登录页（避免停留在无鉴权页面）
      if (location.pathname !== '/login') {
        location.replace('/login')
      }
    }
    return Promise.reject(error)
  },
)

export default http
