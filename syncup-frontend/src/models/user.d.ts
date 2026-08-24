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
    tagIds?: number[];
    tagNames?: string[];
    createTime: Date;
    lastActiveTime?: Date | string;
};
