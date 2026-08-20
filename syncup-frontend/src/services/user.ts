import myAxios from "../plugins/myAxios";
import {clearLoginToken} from "../plugins/myAxios";
import { clearCurrentUserState, setCurrentUserState } from "../states/user";
import type {UserType} from "../models/user";

export const getCurrentUser = async () => {
    // const currentUser = getCurrentUserState();
    // if (currentUser) {
    //     return currentUser;
    // }
    // 不存在则从远程获取
    const res = await myAxios.get('/user/current');
    if (res.code === 0) {
        setCurrentUserState(res.data);
        return res.data;
    }
    return null;
}

export const logout = async () => {
    try {
        await myAxios.post('/user/logout');
    } finally {
        clearLoginToken();
        clearCurrentUserState();
    }
}

export const getPublicUserById = async (id: number) => {
    const res = await myAxios.get<UserType>(`/user/${id}`);
    return res.code === 0 ? res.data : null;
}
