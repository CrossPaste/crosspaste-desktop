/**
 * Chrome extension-specific translations that don't exist in the desktop app.
 * These are merged with the auto-generated desktop translations at runtime.
 *
 * When a key is added to the desktop i18n files, remove it from here.
 */
export const extensionMessages: Record<string, Record<string, string>> = {
  en: {
    clipboard: "Clipboard",
    connect: "Connect",
    connecting: "Connecting…",
    connection_failed_check: "Connection failed, please check the IP and port",
    device_note: "Device Note",
    add_note_for: "Add note for %s",
    enter_note_name: "Enter note name",
    enter_pairing_code_desc:
      "Enter the 6-digit pairing code shown on the desktop app.",
    re_enter: "Re-enter",
    verification_failed_retry: "Verification failed, please try again",
  },
  de: {
    clipboard: "Zwischenablage",
    connect: "Verbinden",
    connecting: "Verbindung wird hergestellt…",
    connection_failed_check:
      "Verbindung fehlgeschlagen, bitte IP und Port überprüfen",
    device_note: "Gerätenotiz",
    add_note_for: "Notiz hinzufügen für %s",
    enter_note_name: "Notizname eingeben",
    enter_pairing_code_desc:
      "Geben Sie den 6-stelligen Kopplungscode ein, der auf der Desktop-App angezeigt wird.",
    re_enter: "Erneut eingeben",
    verification_failed_retry:
      "Überprüfung fehlgeschlagen, bitte erneut versuchen",
  },
  es: {
    clipboard: "Portapapeles",
    connect: "Conectar",
    connecting: "Conectando…",
    connection_failed_check:
      "Conexión fallida, por favor verifica la IP y el puerto",
    device_note: "Nota del dispositivo",
    add_note_for: "Agregar nota para %s",
    enter_note_name: "Ingrese nombre de nota",
    enter_pairing_code_desc:
      "Ingrese el código de emparejamiento de 6 dígitos que se muestra en la aplicación de escritorio.",
    re_enter: "Reingresar",
    verification_failed_retry:
      "Verificación fallida, por favor intente de nuevo",
  },
  fr: {
    clipboard: "Presse-papiers",
    connect: "Connecter",
    connecting: "Connexion…",
    connection_failed_check:
      "Échec de la connexion, veuillez vérifier l'IP et le port",
    device_note: "Note de l'appareil",
    add_note_for: "Ajouter une note pour %s",
    enter_note_name: "Entrez le nom de la note",
    enter_pairing_code_desc:
      "Entrez le code d'appairage à 6 chiffres affiché sur l'application de bureau.",
    re_enter: "Re-saisir",
    verification_failed_retry:
      "Vérification échouée, veuillez réessayer",
  },
  ja: {
    clipboard: "クリップボード",
    connect: "接続",
    connecting: "接続中…",
    connection_failed_check:
      "接続に失敗しました。IPとポートを確認してください",
    device_note: "デバイスメモ",
    add_note_for: "%s のメモを追加",
    enter_note_name: "メモ名を入力",
    enter_pairing_code_desc:
      "デスクトップアプリに表示されている6桁のペアリングコードを入力してください。",
    re_enter: "再入力",
    verification_failed_retry: "認証に失敗しました。もう一度お試しください",
  },
  ko: {
    clipboard: "클립보드",
    connect: "연결",
    connecting: "연결 중…",
    connection_failed_check: "연결 실패, IP와 포트를 확인하세요",
    device_note: "기기 메모",
    add_note_for: "%s 메모 추가",
    enter_note_name: "메모 이름 입력",
    enter_pairing_code_desc:
      "데스크톱 앱에 표시된 6자리 페어링 코드를 입력하세요.",
    re_enter: "다시 입력",
    verification_failed_retry: "인증 실패, 다시 시도해주세요",
  },
  zh: {
    clipboard: "剪贴板",
    connect: "连接",
    connecting: "连接中…",
    connection_failed_check: "连接失败，请检查 IP 和端口是否正确",
    device_note: "设备备注",
    add_note_for: "为 %s 添加备注名称",
    enter_note_name: "输入备注名称",
    enter_pairing_code_desc: "请输入桌面端显示的 6 位配对码。",
    re_enter: "重新输入",
    verification_failed_retry: "验证失败，请重新输入",
  },
  zh_hant: {
    clipboard: "剪貼簿",
    connect: "連線",
    connecting: "連線中…",
    connection_failed_check: "連線失敗，請確認 IP 和連接埠是否正確",
    device_note: "裝置備註",
    add_note_for: "為 %s 新增備註名稱",
    enter_note_name: "輸入備註名稱",
    enter_pairing_code_desc: "請輸入桌面端顯示的 6 位配對碼。",
    re_enter: "重新輸入",
    verification_failed_retry: "驗證失敗，請重新輸入",
  },
};
