import {TeamType} from "./team";

export type ProfileExtraction = {
    interests?: string[];
    activityTypes?: string[];
    availableTimes?: string[];
    city?: string;
    districts?: string[];
    socialPreference?: string;
    skillLevels?: string[];
    budgetPreference?: string;
    candidateTags?: string[];
    confidence?: number;
    sourceText?: string;
    modelVersion?: string;
};

export type AiProfileResponse = {
    draftId?: string;
    userId?: number;
    status?: number;
    profile?: ProfileExtraction;
    sourceText?: string;
    modelVersion?: string;
    confirmedAt?: string | Date;
    expiresAt?: string | Date;
    updateTime?: string | Date;
};

export type AiUserProfileData = {
    id: number;
    username?: string;
    avatarUrl?: string;
    gender?: number;
    tags?: string;
    profile?: string;
    structuredProfile?: ProfileExtraction;
    planetCode?: string;
    createTime?: string | Date;
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
    deleteConfirmation?: AiTeamDeleteConfirmation;
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
