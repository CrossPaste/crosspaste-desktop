/**
 * Chrome extension-specific translations that don't exist in the desktop app.
 * These are merged with the auto-generated desktop translations at runtime.
 *
 * When a key is added to the desktop i18n files, remove it from here.
 */
export const extensionMessages: Record<string, Record<string, string>> = {
  en: {
    clipboard: "Clipboard",
    desktop_app_active: "Desktop app is running — extension paused",
    sync_status_paused: "Paused",
    sync_status_connecting: "Connecting",
    sync_status_unverified: "Needs re-pair",
    sync_status_unmatched: "Keys mismatch",
    sync_status_incompatible: "Version mismatch",
    repair_device: "Re-pair",
    connect: "Connect",
    connect_code: "Connection Code",
    connecting: "Connecting…",
    connection_failed_check: "Connection failed, please check the connection code",
    device_note: "Device Note",
    add_note_for: "Add note for %s",
    enter_note_name: "Enter note name",
    enter_pairing_code_desc:
      "Enter the 6-digit pairing code shown on the desktop app.",
    re_enter: "Re-enter",
    verification_failed_retry: "Verification failed, please try again",
    time: "Time",
    ip_address: "IP Address",
    devices_guide_title: "Manual Connection Only",
    devices_guide_desc:
      "The Chrome extension runs in a browser sandbox and cannot broadcast on the local network, so other CrossPaste clients cannot automatically discover it.",
    devices_guide_step1:
      "Find your device's IP and port in the desktop app: Settings → Network Settings",
    devices_guide_step2:
      "Click \"Add Device\" below and enter the connection info",
  },
  de: {
    clipboard: "Zwischenablage",
    desktop_app_active: "Desktop-App läuft — Erweiterung pausiert",
    sync_status_paused: "Pausiert",
    sync_status_connecting: "Verbindung läuft",
    sync_status_unverified: "Erneut koppeln",
    sync_status_unmatched: "Schlüssel stimmen nicht",
    sync_status_incompatible: "Version inkompatibel",
    repair_device: "Erneut koppeln",
    connect: "Verbinden",
    connect_code: "Verbindungscode",
    connecting: "Verbindung wird hergestellt…",
    connection_failed_check:
      "Verbindung fehlgeschlagen, bitte Verbindungscode überprüfen",
    device_note: "Gerätenotiz",
    add_note_for: "Notiz hinzufügen für %s",
    enter_note_name: "Notizname eingeben",
    enter_pairing_code_desc:
      "Geben Sie den 6-stelligen Kopplungscode ein, der auf der Desktop-App angezeigt wird.",
    re_enter: "Erneut eingeben",
    verification_failed_retry:
      "Überprüfung fehlgeschlagen, bitte erneut versuchen",
    time: "Zeit",
    ip_address: "IP-Adresse",
    devices_guide_title: "Nur manuelle Verbindung",
    devices_guide_desc:
      "Die Chrome-Erweiterung läuft in einer Browser-Sandbox und kann nicht im lokalen Netzwerk senden, daher können andere CrossPaste-Clients sie nicht automatisch erkennen.",
    devices_guide_step1:
      "IP-Adresse und Port des Geräts finden: Desktop-App → Einstellungen → Netzwerkeinstellungen",
    devices_guide_step2:
      'Klicken Sie unten auf „Gerät hinzufügen" und geben Sie die Verbindungsdaten ein',
  },
  es: {
    clipboard: "Portapapeles",
    desktop_app_active: "La app de escritorio está activa — extensión en pausa",
    sync_status_paused: "Pausado",
    sync_status_connecting: "Conectando",
    sync_status_unverified: "Volver a emparejar",
    sync_status_unmatched: "Claves no coinciden",
    sync_status_incompatible: "Versión incompatible",
    repair_device: "Volver a emparejar",
    connect: "Conectar",
    connect_code: "Código de conexión",
    connecting: "Conectando…",
    connection_failed_check:
      "Conexión fallida, por favor verifica el código de conexión",
    device_note: "Nota del dispositivo",
    add_note_for: "Agregar nota para %s",
    enter_note_name: "Ingrese nombre de nota",
    enter_pairing_code_desc:
      "Ingrese el código de emparejamiento de 6 dígitos que se muestra en la aplicación de escritorio.",
    re_enter: "Reingresar",
    verification_failed_retry:
      "Verificación fallida, por favor intente de nuevo",
    time: "Hora",
    ip_address: "Dirección IP",
    devices_guide_title: "Solo conexión manual",
    devices_guide_desc:
      "La extensión de Chrome se ejecuta en un entorno aislado del navegador y no puede transmitir en la red local, por lo que otros clientes de CrossPaste no pueden descubrirla automáticamente.",
    devices_guide_step1:
      "Encuentre la IP y el puerto del dispositivo en la app de escritorio: Ajustes → Configuración de red",
    devices_guide_step2:
      "Haga clic en \"Agregar dispositivo\" abajo e ingrese la información de conexión",
  },
  fr: {
    clipboard: "Presse-papiers",
    desktop_app_active: "L'application de bureau est active — extension en pause",
    sync_status_paused: "En pause",
    sync_status_connecting: "Connexion",
    sync_status_unverified: "Ré-appariement requis",
    sync_status_unmatched: "Clés incompatibles",
    sync_status_incompatible: "Version incompatible",
    repair_device: "Ré-apparier",
    connect: "Connecter",
    connect_code: "Code de connexion",
    connecting: "Connexion…",
    connection_failed_check:
      "Échec de la connexion, veuillez vérifier le code de connexion",
    device_note: "Note de l'appareil",
    add_note_for: "Ajouter une note pour %s",
    enter_note_name: "Entrez le nom de la note",
    enter_pairing_code_desc:
      "Entrez le code d'appairage à 6 chiffres affiché sur l'application de bureau.",
    re_enter: "Re-saisir",
    verification_failed_retry:
      "Vérification échouée, veuillez réessayer",
    time: "Heure",
    ip_address: "Adresse IP",
    devices_guide_title: "Connexion manuelle uniquement",
    devices_guide_desc:
      "L'extension Chrome fonctionne dans un bac à sable du navigateur et ne peut pas diffuser sur le réseau local. Les autres clients CrossPaste ne peuvent donc pas la découvrir automatiquement.",
    devices_guide_step1:
      "Trouvez l'IP et le port de l'appareil dans l'application de bureau : Paramètres → Paramètres réseau",
    devices_guide_step2:
      "Cliquez sur « Ajouter un appareil » ci-dessous et entrez les informations de connexion",
  },
  ja: {
    clipboard: "クリップボード",
    desktop_app_active: "デスクトップアプリが実行中 — 拡張機能は一時停止",
    sync_status_paused: "一時停止",
    sync_status_connecting: "接続中",
    sync_status_unverified: "再ペアリング必要",
    sync_status_unmatched: "鍵が不一致",
    sync_status_incompatible: "バージョン非互換",
    repair_device: "再ペアリング",
    connect: "接続",
    connect_code: "接続コード",
    connecting: "接続中…",
    connection_failed_check:
      "接続に失敗しました。接続コードを確認してください",
    device_note: "デバイスメモ",
    add_note_for: "%s のメモを追加",
    enter_note_name: "メモ名を入力",
    enter_pairing_code_desc:
      "デスクトップアプリに表示されている6桁のペアリングコードを入力してください。",
    re_enter: "再入力",
    verification_failed_retry: "認証に失敗しました。もう一度お試しください",
    time: "時間",
    ip_address: "IPアドレス",
    devices_guide_title: "手動接続のみ",
    devices_guide_desc:
      "Chrome 拡張機能はブラウザのサンドボックス内で動作し、ローカルネットワークでブロードキャストできないため、他の CrossPaste クライアントが自動検出することはできません。",
    devices_guide_step1:
      "デスクトップアプリでデバイスの IP とポートを確認：設定 → ネットワーク設定",
    devices_guide_step2:
      "下の「デバイスを追加」をクリックし、接続情報を入力してください",
  },
  ko: {
    clipboard: "클립보드",
    desktop_app_active: "데스크톱 앱 실행 중 — 확장 프로그램 일시 중지",
    sync_status_paused: "일시 중지",
    sync_status_connecting: "연결 중",
    sync_status_unverified: "재페어링 필요",
    sync_status_unmatched: "키 불일치",
    sync_status_incompatible: "버전 호환 안됨",
    repair_device: "다시 페어링",
    connect: "연결",
    connect_code: "연결 코드",
    connecting: "연결 중…",
    connection_failed_check: "연결 실패, 연결 코드를 확인하세요",
    device_note: "기기 메모",
    add_note_for: "%s 메모 추가",
    enter_note_name: "메모 이름 입력",
    enter_pairing_code_desc:
      "데스크톱 앱에 표시된 6자리 페어링 코드를 입력하세요.",
    re_enter: "다시 입력",
    verification_failed_retry: "인증 실패, 다시 시도해주세요",
    time: "시간",
    ip_address: "IP 주소",
    devices_guide_title: "수동 연결만 가능",
    devices_guide_desc:
      "Chrome 확장 프로그램은 브라우저 샌드박스에서 실행되어 로컬 네트워크에서 브로드캐스트할 수 없으므로, 다른 CrossPaste 클라이언트가 자동으로 검색할 수 없습니다.",
    devices_guide_step1:
      "데스크톱 앱에서 기기의 IP와 포트를 확인하세요: 설정 → 네트워크 설정",
    devices_guide_step2:
      "아래의 \"기기 추가\"를 클릭하고 연결 정보를 입력하세요",
  },
  zh: {
    clipboard: "剪贴板",
    desktop_app_active: "桌面应用已启动 — 扩展已暂停",
    sync_status_paused: "已暂停",
    sync_status_connecting: "连接中",
    sync_status_unverified: "需要重新配对",
    sync_status_unmatched: "密钥不匹配",
    sync_status_incompatible: "版本不兼容",
    repair_device: "重新配对",
    connect: "连接",
    connect_code: "连接码",
    connecting: "连接中…",
    connection_failed_check: "连接失败，请检查连接码是否正确",
    device_note: "设备备注",
    add_note_for: "为 %s 添加备注名称",
    enter_note_name: "输入备注名称",
    enter_pairing_code_desc: "请输入桌面端显示的 6 位配对码。",
    re_enter: "重新输入",
    verification_failed_retry: "验证失败，请重新输入",
    time: "时间",
    ip_address: "IP 地址",
    devices_guide_title: "仅支持手动连接",
    devices_guide_desc:
      "Chrome 扩展运行在浏览器沙箱中，无法在局域网中广播，因此其他 CrossPaste 客户端无法自动发现本扩展。",
    devices_guide_step1:
      "在桌面端查找设备的 IP 和端口：设置 → 网络设置",
    devices_guide_step2:
      "点击下方「添加设备」按钮，输入连接信息",
  },
  zh_hant: {
    clipboard: "剪貼簿",
    desktop_app_active: "桌面應用已啟動 — 擴充功能已暫停",
    sync_status_paused: "已暫停",
    sync_status_connecting: "連線中",
    sync_status_unverified: "需要重新配對",
    sync_status_unmatched: "密鑰不匹配",
    sync_status_incompatible: "版本不相容",
    repair_device: "重新配對",
    connect: "連線",
    connect_code: "連線碼",
    connecting: "連線中…",
    connection_failed_check: "連線失敗，請確認連線碼是否正確",
    device_note: "裝置備註",
    add_note_for: "為 %s 新增備註名稱",
    enter_note_name: "輸入備註名稱",
    enter_pairing_code_desc: "請輸入桌面端顯示的 6 位配對碼。",
    re_enter: "重新輸入",
    verification_failed_retry: "驗證失敗，請重新輸入",
    time: "時間",
    ip_address: "IP 位址",
    devices_guide_title: "僅支援手動連線",
    devices_guide_desc:
      "Chrome 擴充功能運行在瀏覽器沙箱中，無法在區域網路中廣播，因此其他 CrossPaste 用戶端無法自動發現本擴充功能。",
    devices_guide_step1:
      "在桌面端查看裝置的 IP 和連接埠：設定 → 網路設定",
    devices_guide_step2:
      "點擊下方「新增裝置」按鈕，輸入連線資訊",
  },
};
