export class ApiError extends Error {
  constructor(message, status) { super(message); this.status = status }
}

function authHeaders(token, admin = false) {
  return token ? { [admin ? 'token' : 'authentication']: token } : {}
}

export function queryString(params = {}) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value))
  }
  const value = query.toString()
  return value ? `?${value}` : ''
}

export async function request(path, { method = 'GET', body, token, admin = false } = {}) {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), 12000)
  try {
    const response = await fetch(`/api${path}`, { method, signal: controller.signal, headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...authHeaders(token, admin) }, ...(body ? { body: JSON.stringify(body) } : {}) })
    if (!response.ok) throw new ApiError(response.status === 401 ? '登录已过期，请重新连接' : `服务暂时不可用（${response.status}）`, response.status)
    if (response.status === 204) return null
    const result = await response.json()
    if (result.code !== 1) throw new ApiError(result.msg || '操作失败，请稍后重试', response.status)
    return result.data
  } catch (error) {
    if (error.name === 'AbortError') throw new ApiError('请求超时，请检查后端服务', 0)
    if (error instanceof TypeError) throw new ApiError('无法连接后端，请检查服务与代理地址', 0)
    throw error
  } finally { clearTimeout(timeout) }
}

export async function upload(path, file, { token, admin = false } = {}) {
  const form = new FormData()
  form.append('file', file)
  const response = await fetch(`/api${path}`, { method: 'POST', headers: authHeaders(token, admin), body: form })
  if (!response.ok) throw new ApiError(`上传失败（${response.status}）`, response.status)
  const result = await response.json()
  if (result.code !== 1) throw new ApiError(result.msg || '上传失败，请检查对象存储配置')
  return result.data
}

export async function download(path, { token, admin = false } = {}) {
  const response = await fetch(`/api${path}`, { headers: authHeaders(token, admin) })
  if (!response.ok) throw new ApiError(`导出失败（${response.status}）`, response.status)
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    const result = await response.json()
    throw new ApiError(result.msg || '导出失败')
  }
  return { blob: await response.blob(), disposition: response.headers.get('content-disposition') || '' }
}
