export function todayLocalISO(): string {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function daysAgoLocalISO(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

function toLocalISO(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/** Monday (local) of the week `weekOffset` weeks from this week (0 = this week, -1 = last week). */
export function mondayOfWeekLocalISO(weekOffset = 0): string {
  const d = new Date();
  const daysSinceMonday = (d.getDay() + 6) % 7; // getDay(): 0=Sun..6=Sat
  d.setDate(d.getDate() - daysSinceMonday + weekOffset * 7);
  return toLocalISO(d);
}
