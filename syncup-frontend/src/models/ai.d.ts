import {TeamType} from "./team";

export type TeamIntent = {
    sourceText?: string;
    activityType?: string;
    city?: string;
    district?: string;
    startTime?: string | Date;
    durationMinutes?: number;
    memberCount?: number;
    budgetMin?: number;
    budgetMax?: number;
    skillLevel?: string;
    tags?: string[];
    teamName?: string;
    description?: string;
    createTeamRequested?: boolean;
    teamRelated?: boolean;
    missingFields?: string[];
};

export type TeamDraft = {
    draftId: string;
    sessionId?: string;
    name?: string;
    description?: string;
    maxNum?: number;
    activityType?: string;
    city?: string;
    district?: string;
    startTime?: string | Date;
    durationMinutes?: number;
    budgetPerPerson?: number;
    skillLevel?: string;
    expiresAt?: string | Date;
};

export type AiTeamDraftConfirmResponse = {
    draftId: string;
    teamId: number;
    status: string;
};

export type AiUserRecommendation = {
    id: number;
    username?: string;
    avatarUrl?: string;
    gender?: number;
    tags?: string;
    planetCode?: string;
    createTime?: string | Date;
    reasons?: string[];
};

export type AiToolResult = {
    toolName: string;
    type: string;
    success: boolean;
    summary?: string;
    data?: unknown;
};

export type AiChatResponse = {
    sessionId: string;
    reply: string;
    intent?: TeamIntent;
    toolResults?: AiToolResult[];
    draft?: TeamDraft;
    needClarification?: boolean;
    clarificationQuestions?: string[];
};

export type SearchTeamsToolResult = AiToolResult & {
    toolName: "searchTeams";
    data?: TeamType[];
};
