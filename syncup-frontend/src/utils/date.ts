export const toDateValue = (value: unknown, fallback?: Date) => {
  if (value instanceof Date && !Number.isNaN(value.getTime())) {
    return value;
  }
  if (typeof value === 'string' || typeof value === 'number') {
    const date = new Date(value);
    if (!Number.isNaN(date.getTime())) {
      return date;
    }
  }
  return fallback;
};

export const formatDateTime = (value: unknown) => {
  const date = toDateValue(value);
  if (!date) {
    return '';
  }

  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ].join('-') + ` ${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

const pad = (num: number) => String(num).padStart(2, '0');

export const toDatePickerValue = (value: unknown, fallback = new Date()) => {
  const date = toDateValue(value, fallback) ?? fallback;
  return [
    String(date.getFullYear()),
    pad(date.getMonth() + 1),
    pad(date.getDate()),
  ];
};

export const toTimePickerValue = (value: unknown, fallback = new Date()) => {
  const date = toDateValue(value, fallback) ?? fallback;
  return [
    pad(date.getHours()),
    pad(date.getMinutes()),
  ];
};

export const composeDateTime = (dateValue: string[], timeValue: string[]) => {
  const [year, month, day] = dateValue.map(Number);
  const [hour = 0, minute = 0] = timeValue.map(Number);
  return new Date(year, month - 1, day, hour, minute);
};
