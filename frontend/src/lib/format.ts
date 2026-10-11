export function formatDuration(minutes?: number | null) {
  if (!minutes) return "Time not set";
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours && rest) return `${hours}h ${rest}m`;
  if (hours) return `${hours}h`;
  return `${rest} min`;
}

export function formatWhen(value?: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

export function progressLabel(percent?: number | null) {
  if (percent == null) return "Not started";
  return `${percent}% from saved lessons`;
}

export function difficultyLabel(value?: string) {
  if (!value) return "";
  return value.charAt(0) + value.slice(1).toLowerCase();
}
