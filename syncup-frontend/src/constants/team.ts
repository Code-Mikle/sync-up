export const teamStatusEnum: Record<number, string> = {
    0: '公开',
    1: '私有',
    2: '加密',
};

export const teamActivityCategoryOptions = [
    {code: 1, name: '运动健身', description: '侧重室内运动、健身房、游泳、瑜伽、球类等'},
    {code: 2, name: '户外出行', description: '侧重徒步、露营、骑行、飞盘、滑雪等本地或短途户外活动'},
    {code: 3, name: '游戏电竞', description: '侧重手游、端游、主机游戏组队'},
    {code: 4, name: '桌游剧本', description: '侧重线下剧本杀、狼人杀、棋牌、桌游等'},
    {code: 5, name: '休闲娱乐', description: '侧重看电影、K歌、看展、逛街、摄影等'},
    {code: 6, name: '美食探店', description: '侧重吃饭、探店、咖啡、火锅、夜宵等'},
    {code: 7, name: '学习成长', description: '侧重考研、考证、语言交换、读书会、自习、刷题等'},
    {code: 8, name: '旅行出游', description: '侧重跨城市长途旅行、拼车、自驾游、结伴游等'},
    {code: 9, name: '其他', description: '无法归入以上类别的活动'},
];

export const teamActivityCategoryNameMap: Record<number, string> = teamActivityCategoryOptions.reduce(
    (map, item) => ({
        ...map,
        [item.code]: item.name,
    }),
    {} as Record<number, string>,
);

export const getTeamActivityCategoryName = (code?: number) => {
    if (code === undefined || code === null) {
        return '';
    }
    return teamActivityCategoryNameMap[code] ?? '';
};
