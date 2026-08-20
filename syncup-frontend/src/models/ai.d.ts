import {TeamType} from "./team";

export type AiUserProfileData = {
    id: number;
    username?: string;
    avatarUrl?: string;
    gender?: number;
    city?: string;
    tags?: string;
    profile?: string;
    createTime?: string | Date;
    lastActiveTime?: string | Date;
    degraded?: boolean;
};

export type TeamIntent = {
    sourceText?: string;
    teamId?: number;
    teamPassword?: string;
    activityCategory?: number;
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
    activityCategory?: number;
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

export type AiTeamDeleteConfirmation = {
    teamId: number;
    name?: string;
    description?: string;
    activityCategory?: number;
    activityType?: string;
    city?: string;
    district?: string;
    startTime?: string | Date;
    maxNum?: number;
    hasJoinNum?: number;
    warning?: string;
};

export type AiUserRecommendation = {
    id: number;
    username?: string;
    avatarUrl?: string;
    gender?: number;
    tags?: string;
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

export type AiUiBlockType =
    | "team_list"
    | "user_recommendations"
    | "profile_card"
    | "team_draft_confirmation"
    | "team_delete_confirmation";

export type AiUiBlock = {
    type: AiUiBlockType;
    variant?: "search" | "joined" | "created" | string;
    data?: unknown;
};

export type AiChatResponse = {
    sessionId: string;
    reply: string;
    intent?: TeamIntent;
    uiBlocks?: AiUiBlock[];
    needClarification?: boolean;
    clarificationQuestions?: string[];
};

export type AiChatMessage = {
    id?: number;
    sessionId?: string;
    role: "user" | "assistant" | "event";
    content?: string;
    response?: AiChatResponse;
    eventType?: string;
    relatedTeamId?: number;
    relatedDraftId?: string;
    visible?: number;
    createTime?: string | Date;
};

export type AiChatHistory = {
    sessionId?: string;
    messages?: AiChatMessage[];
};

export type SearchTeamsToolResult = AiToolResult & {
    toolName: "searchTeams";
    data?: TeamType[];
};
