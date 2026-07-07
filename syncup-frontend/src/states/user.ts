import {UserType} from "../models/user";

let currentUser: UserType | null = null;

const setCurrentUserState = (user: UserType) => {
    currentUser = user;
}

const getCurrentUserState = () : UserType | null => {
    return currentUser;
}

const clearCurrentUserState = () => {
    currentUser = null;
}

export {
    setCurrentUserState,
    getCurrentUserState,
    clearCurrentUserState,
}
