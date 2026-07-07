import {UserType} from "../models/user";

export const parseUserTags = (tags: UserType['tags']) => {
  if (Array.isArray(tags)) {
    return tags.filter(Boolean);
  }
  if (!tags) {
    return [];
  }
  try {
    const parsedTags = JSON.parse(tags);
    return Array.isArray(parsedTags) ? parsedTags.filter(Boolean) : [];
  } catch (error) {
    return tags
        .split(/[,，]/)
        .map((tag) => tag.trim())
        .filter(Boolean);
  }
};

export const normalizeUserTags = (user: UserType): UserType => ({
  ...user,
  tags: parseUserTags(user.tags),
});

export const normalizeUserList = (users: UserType[] = []) => {
  return users.map(normalizeUserTags);
};
