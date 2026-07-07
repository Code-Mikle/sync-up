export const genderTextMap: Record<number, string> = {
    0: '男',
    1: '女',
    2: '保密',
};

export const genderOptions = [
    {text: '男', value: '0'},
    {text: '女', value: '1'},
    {text: '保密', value: '2'},
];

export const getGenderText = (gender: unknown) => {
    const genderValue = Number(gender);
    return Number.isInteger(genderValue) && genderValue in genderTextMap
        ? genderTextMap[genderValue]
        : '保密';
};
