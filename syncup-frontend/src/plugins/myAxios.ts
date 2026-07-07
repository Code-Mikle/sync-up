import axios, {type AxiosInstance, type AxiosRequestConfig} from "axios";

const isDev = import.meta.env.DEV;
const TOKEN_STORAGE_KEY = 'syncup_token';
const TOKEN_PREFIX_STORAGE_KEY = 'syncup_token_prefix';

export type BaseResponse<T = any> = {
    code: number;
    data: T;
    message?: string;
    description?: string;
};

type BackendAxiosInstance = Omit<AxiosInstance, 'get' | 'post' | 'put' | 'delete'> & {
    get<T = any>(url: string, config?: AxiosRequestConfig): Promise<BaseResponse<T>>;
    post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<BaseResponse<T>>;
    put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<BaseResponse<T>>;
    delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<BaseResponse<T>>;
};

const myAxios = axios.create({
    baseURL: isDev ? 'http://localhost:8080/api' : '线上地址',
}) as BackendAxiosInstance;

myAxios.defaults.withCredentials = false;

// Add a request interceptor
myAxios.interceptors.request.use(function (config) {
    console.log('我要发请求啦', config)
    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    if (token) {
        const tokenPrefix = localStorage.getItem(TOKEN_PREFIX_STORAGE_KEY) || 'Bearer';
        config.headers.set('Authorization', `${tokenPrefix} ${token}`);
    }
    return config;
}, function (error) {
    // Do something with request error
    return Promise.reject(error);
});

// Add a response interceptor
myAxios.interceptors.response.use(function (response) {
    console.log('我收到你的响应啦', response)
    // 未登录则跳转到登录页
    if (response?.data?.code === 40100) {
        localStorage.removeItem(TOKEN_STORAGE_KEY);
        localStorage.removeItem(TOKEN_PREFIX_STORAGE_KEY);
        const redirectUrl = window.location.href;
        window.location.href = `/user/login?redirect=${redirectUrl}`;
    }
    // Do something with response data
    return response.data;
}, function (error) {
    // Do something with response error
    return Promise.reject(error);
});

export const setLoginToken = (token: string, tokenPrefix = 'Bearer') => {
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
    localStorage.setItem(TOKEN_PREFIX_STORAGE_KEY, tokenPrefix);
};

export const clearLoginToken = () => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(TOKEN_PREFIX_STORAGE_KEY);
};

export default myAxios;
