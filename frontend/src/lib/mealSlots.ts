export const MEAL_SLOTS = [
  { value: "breakfast", label: "早餐", time: "上午 06:00 - 09:00" },
  { value: "lunch", label: "午餐", time: "中午 11:30 - 13:30" },
  { value: "dinner", label: "晚餐", time: "晚上 17:30 - 20:00" },
  { value: "snack", label: "點心", time: "全天輕食紀錄" },
];

export function mealSlotLabel(value: string): string {
  return MEAL_SLOTS.find((slot) => slot.value === value)?.label ?? value;
}
