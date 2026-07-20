import {UserType} from "./user";

/**
 * 队伍类别
 */
export type TeamType = {
    id: number;
    userId?: number;
    name: string;
    description: string;
    expireTime?: Date;
    activityCategory?: number;
    activityCategoryName?: string;
    activityType?: string;
    city?: string;
    district?: string;
    startTime?: Date;
    durationMinutes?: number;
    budgetPerPerson?: number;
    skillLevel?: string;
    maxNum: number;
    password?: string,
    // todo 定义枚举值类型，更规范
    status: number;
    createTime: Date;
    updateTime: Date;
    createUser?: UserType;
    hasJoinNum?: number;
    hasJoin?: boolean;
};
