export type Lang = "zh-TW" | "en";

export const LANGS: { value: Lang; label: string }[] = [
  { value: "zh-TW", label: "繁體中文" },
  { value: "en", label: "English" },
];

type Dict = Record<string, string>;

// Flat key dictionaries. Keys are namespaced by area (nav.*, shell.*, settings.*).
// Traditional Chinese is the source of truth; English mirrors it. Pages not yet
// migrated keep their inline strings and are translated incrementally.
const zhTW: Dict = {
  "nav.dashboard": "儀表板",
  "nav.workouts": "運動菜單",
  "nav.meals": "飲食追蹤",
  "nav.profile": "生理指標",
  "nav.settings": "系統設定",

  "shell.logout": "帳號登出",
  "shell.logoutShort": "登出",
  "shell.aiOnline": "AI 引擎已連線",
  "shell.userFallback": "健康行者",
  "shell.greeting.morning": "早安，開啟美好活力的一天",
  "shell.greeting.afternoon": "午安，保持專注與健康節奏",
  "shell.greeting.evening": "傍晚好，享受健康的放鬆時刻",
  "shell.greeting.night": "夜深了，讓身體好好充電休息",
  "shell.greetingWithName": "{name}，{greeting}",

  "settings.theme.title": "外觀視覺主題",
  "settings.theme.desc": "選擇符合您當前環境與視覺偏好的色彩模式",
  "settings.theme.light": "清新淺色",
  "settings.theme.lightDesc": "如晨光般乾淨自然",
  "settings.theme.dark": "深沉靜夜",
  "settings.theme.darkDesc": "護眼低光睡眠友善",
  "settings.theme.system": "跟隨系統",
  "settings.theme.systemDesc": "隨日出日落自動調節",

  "settings.font.title": "字體大小",
  "settings.font.desc": "調整整個介面（含 AI 小助手對話）的文字大小",
  "settings.font.small": "小",
  "settings.font.normal": "標準",
  "settings.font.large": "大",
  "settings.font.xlarge": "特大",

  "settings.lang.title": "語言 / Language",
  "settings.lang.desc": "選擇介面顯示語言。部分頁面仍在陸續翻譯中。",

  "settings.avatar.title": "AI 小幫手人偶",
  "settings.avatar.desc": "選擇你想聊天的小幫手形象，預設依你的性別，隨時可自由更換。",
  "settings.avatar.male": "活力男孩",
  "settings.avatar.maleDesc": "陽光開朗的健身夥伴",
  "settings.avatar.female": "元氣女孩",
  "settings.avatar.femaleDesc": "溫暖貼心的健身夥伴",

  "settings.system.title": "系統狀態",
  "settings.system.desc": "檢查後端、資料庫、AI 與限流服務是否正常。",
  "settings.system.refresh": "重新檢查",
  "settings.system.loading": "檢查中",
  "settings.system.checkedAt": "更新於",
  "settings.system.error": "無法取得系統狀態，請確認後端服務是否啟動。",

  "settings.danger.title": "危險操作區",
  "settings.danger.desc": "刪除帳號將永久清除您的所有資料，包含運動紀錄、飲食紀錄、體重趨勢與個人檔案，且無法復原。",
  "settings.danger.deleteBtn": "刪除我的帳號",
  "settings.danger.deleteFailed": "刪除失敗",
  "settings.danger.confirmQuestion": "確定要永久刪除帳號嗎？此動作會立即登出並清除所有資料，無法復原。",
  "settings.danger.confirmBtn": "確認永久刪除",
  "settings.danger.deleting": "刪除中...",
  "settings.danger.cancel": "取消",
};

const en: Dict = {
  "nav.dashboard": "Dashboard",
  "nav.workouts": "Workouts",
  "nav.meals": "Meals",
  "nav.profile": "Body Metrics",
  "nav.settings": "Settings",

  "shell.logout": "Sign out",
  "shell.logoutShort": "Sign out",
  "shell.aiOnline": "AI engine connected",
  "shell.userFallback": "Health Walker",
  "shell.greeting.morning": "Good morning — start the day strong",
  "shell.greeting.afternoon": "Good afternoon — keep a healthy rhythm",
  "shell.greeting.evening": "Good evening — enjoy a healthy wind-down",
  "shell.greeting.night": "It's late — let your body recharge",
  "shell.greetingWithName": "{name}, {greeting}",

  "settings.theme.title": "Appearance",
  "settings.theme.desc": "Pick the colour mode that fits your environment and taste",
  "settings.theme.light": "Light",
  "settings.theme.lightDesc": "Clean and natural like morning light",
  "settings.theme.dark": "Dark",
  "settings.theme.darkDesc": "Low-light, easy on the eyes",
  "settings.theme.system": "System",
  "settings.theme.systemDesc": "Follows your device automatically",

  "settings.font.title": "Font size",
  "settings.font.desc": "Adjust the text size across the whole app (including the AI assistant)",
  "settings.font.small": "S",
  "settings.font.normal": "M",
  "settings.font.large": "L",
  "settings.font.xlarge": "XL",

  "settings.lang.title": "Language / 語言",
  "settings.lang.desc": "Choose the interface language. Some pages are still being translated.",

  "settings.avatar.title": "AI assistant character",
  "settings.avatar.desc": "Pick the assistant you chat with. Defaults to your gender; change it anytime.",
  "settings.avatar.male": "Energetic Guy",
  "settings.avatar.maleDesc": "A sunny, upbeat fitness buddy",
  "settings.avatar.female": "Lively Girl",
  "settings.avatar.femaleDesc": "A warm, caring fitness buddy",

  "settings.system.title": "System status",
  "settings.system.desc": "Checks backend, database, AI and rate-limit services.",
  "settings.system.refresh": "Refresh",
  "settings.system.loading": "Checking",
  "settings.system.checkedAt": "Updated",
  "settings.system.error": "Unable to load system status. Check that the backend is running.",

  "settings.danger.title": "Danger zone",
  "settings.danger.desc": "Deleting your account permanently erases all your data — workouts, meals, weight trends and profile — and cannot be undone.",
  "settings.danger.deleteBtn": "Delete my account",
  "settings.danger.deleteFailed": "Deletion failed",
  "settings.danger.confirmQuestion": "Permanently delete your account? You'll be signed out immediately and all data erased. This cannot be undone.",
  "settings.danger.confirmBtn": "Confirm permanent deletion",
  "settings.danger.deleting": "Deleting...",
  "settings.danger.cancel": "Cancel",
};

export const dictionaries: Record<Lang, Dict> = { "zh-TW": zhTW, en };
