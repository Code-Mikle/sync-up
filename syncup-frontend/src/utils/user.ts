import {UserType} from "../models/user";

export const normalizeUserTagNames = (user: UserType): UserType => ({
  ...user,
  tagNames: (user.tagNames ?? []).filter(Boolean),
});

export const normalizeUserList = (users: UserType[] = []) => {
  return users.map(normalizeUserTagNames);
};
