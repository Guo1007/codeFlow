import { post } from '../request'

/**
 * 登录成功返回（token + 用户信息）
 */
export interface LoginRespVO {
  token: string
  userId: number
  username: string
  nickname: string
}

/**
 * 用户注册（成功后返回 token，自动登录）
 */
export async function register(data: { username: string; password: string; nickname?: string }): Promise<LoginRespVO> {
  return post<LoginRespVO>('/auth/register', data)
}

/**
 * 用户登录
 */
export async function login(data: { username: string; password: string }): Promise<LoginRespVO> {
  return post<LoginRespVO>('/auth/login', data)
}

export default { register, login }