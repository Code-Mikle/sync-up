/**
 * 用户类别
 */
export type UserType = {
    id: number;
    username: string;
    userAccount: string;
    avatarUrl?: string;
    profile?: string;
    gender:number;
    phone: string;
    email: string;
    city?: string;
    userStatus: number;
    userRole: number;
    planetCode: string;
    tags: string | string[];
    createTime: Date;
    lastActiveTime?: Date | string;
};
