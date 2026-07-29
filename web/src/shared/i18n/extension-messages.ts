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
    enter_sas_code_desc:
      "Enter the 6-digit verification code shown on the desktop app. It must match on both devices.",
    enter_pin_desc:
      "Open the pairing screen on the desktop app and enter the 6-digit PIN shown on the Chrome Extension card.",
    pairing_disabled_hint:
      "Pairing is not accepting requests. Open the pairing screen on the desktop app, then try again.",
    sas_mismatch:
      "Verification code mismatch — pairing aborted for safety. Please try again.",
    pin_expired_retry:
      "The PIN expired. Enter the new PIN shown on the desktop app.",
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
    paste_not_synced_title: "CrossPaste: paste from %s not synced",
    paste_oversize_file: "\"%s\" (%s) exceeds the %s per-file limit",
    paste_oversize_total: "Total size %s exceeds the %s limit",
    install_desktop_client: "Install the desktop client for full functionality",
    get_native_app: "Get the native app",
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
    enter_sas_code_desc:
      "Geben Sie den 6-stelligen Bestätigungscode ein, der auf der Desktop-App angezeigt wird. Er muss auf beiden Geräten übereinstimmen.",
    enter_pin_desc:
      "Öffnen Sie den Kopplungsbildschirm in der Desktop-App und geben Sie die 6-stellige PIN der Chrome-Erweiterungs-Karte ein.",
    pairing_disabled_hint:
      "Kopplung nimmt derzeit keine Anfragen an. Öffnen Sie den Kopplungsbildschirm in der Desktop-App und versuchen Sie es erneut.",
    sas_mismatch:
      "Bestätigungscode stimmt nicht überein — Kopplung aus Sicherheitsgründen abgebrochen. Bitte erneut versuchen.",
    pin_expired_retry:
      "Die PIN ist abgelaufen. Geben Sie die neue PIN aus der Desktop-App ein.",
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
    paste_not_synced_title: "CrossPaste: Einfügung von %s nicht synchronisiert",
    paste_oversize_file: "„%s\" (%s) überschreitet das Limit von %s pro Datei",
    paste_oversize_total: "Gesamtgröße %s überschreitet das Limit von %s",
    install_desktop_client: "Desktop-Client für alle Funktionen installieren",
    get_native_app: "Native App laden",
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
    enter_sas_code_desc:
      "Ingrese el código de verificación de 6 dígitos que muestra la aplicación de escritorio. Debe coincidir en ambos dispositivos.",
    enter_pin_desc:
      "Abra la pantalla de emparejamiento en la aplicación de escritorio e ingrese el PIN de 6 dígitos de la tarjeta de la extensión de Chrome.",
    pairing_disabled_hint:
      "El emparejamiento no acepta solicitudes. Abra la pantalla de emparejamiento en la aplicación de escritorio y vuelva a intentarlo.",
    sas_mismatch:
      "El código de verificación no coincide — emparejamiento cancelado por seguridad. Inténtelo de nuevo.",
    pin_expired_retry:
      "El PIN ha caducado. Ingrese el nuevo PIN que muestra la aplicación de escritorio.",
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
    paste_not_synced_title: "CrossPaste: pegado de %s no sincronizado",
    paste_oversize_file: "\"%s\" (%s) supera el límite de %s por archivo",
    paste_oversize_total: "El tamaño total %s supera el límite de %s",
    install_desktop_client: "Instala el cliente de escritorio para todas las funciones",
    get_native_app: "Obtener la app nativa",
  },
  fa: {
    clipboard: "کلیپ‌بورد",
    desktop_app_active: "اپ دسکتاپ در حال اجراست — افزونه متوقف شد",
    sync_status_paused: "متوقف",
    sync_status_connecting: "در حال اتصال",
    sync_status_unverified: "نیاز به جفت‌سازی مجدد",
    sync_status_unmatched: "عدم تطابق کلید",
    sync_status_incompatible: "ناسازگاری نسخه",
    repair_device: "جفت‌سازی مجدد",
    connect: "اتصال",
    connect_code: "کد اتصال",
    connecting: "در حال اتصال…",
    connection_failed_check:
      "اتصال ناموفق بود، لطفاً کد اتصال را بررسی کنید",
    device_note: "یادداشت دستگاه",
    add_note_for: "افزودن یادداشت برای %s",
    enter_note_name: "نام یادداشت را وارد کنید",
    enter_pairing_code_desc:
      "کد جفت‌سازی ۶ رقمی نمایش‌داده‌شده در اپ دسکتاپ را وارد کنید.",
    enter_sas_code_desc:
      "کد تأیید ۶ رقمی نمایش‌داده‌شده در اپ دسکتاپ را وارد کنید. باید در هر دو دستگاه یکسان باشد.",
    enter_pin_desc:
      "صفحه جفت‌سازی را در اپ دسکتاپ باز کنید و پین ۶ رقمی کارت افزونه کروم را وارد کنید.",
    pairing_disabled_hint:
      "جفت‌سازی درخواست نمی‌پذیرد. صفحه جفت‌سازی را در اپ دسکتاپ باز کنید و دوباره تلاش کنید.",
    sas_mismatch:
      "کد تأیید مطابقت ندارد — جفت‌سازی برای ایمنی لغو شد. دوباره تلاش کنید.",
    pin_expired_retry:
      "پین منقضی شده است. پین جدید نمایش‌داده‌شده در اپ دسکتاپ را وارد کنید.",
    re_enter: "وارد کردن مجدد",
    verification_failed_retry: "تأیید ناموفق بود، لطفاً دوباره تلاش کنید",
    time: "زمان",
    ip_address: "آدرس IP",
    devices_guide_title: "فقط اتصال دستی",
    devices_guide_desc:
      "افزونه کروم در ساندباکس مرورگر اجرا می‌شود و نمی‌تواند در شبکه محلی پخش کند، بنابراین سایر کلاینت‌های CrossPaste نمی‌توانند آن را به‌طور خودکار کشف کنند.",
    devices_guide_step1:
      "آدرس IP و پورت دستگاه را در اپ دسکتاپ پیدا کنید: تنظیمات ← تنظیمات شبکه",
    devices_guide_step2:
      "روی «افزودن دستگاه» در پایین کلیک کنید و اطلاعات اتصال را وارد کنید",
    paste_not_synced_title: "CrossPaste: چسباندن از %s همگام‌سازی نشد",
    paste_oversize_file: "«%s» (%s) از محدودیت %s در هر فایل فراتر می‌رود",
    paste_oversize_total: "اندازه کل %s از محدودیت %s فراتر می‌رود",
    install_desktop_client:
      "برای دسترسی به امکانات کامل، کلاینت دسکتاپ را نصب کنید",
    get_native_app: "دریافت اپ بومی",
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
    enter_sas_code_desc:
      "Entrez le code de vérification à 6 chiffres affiché sur l'application de bureau. Il doit être identique sur les deux appareils.",
    enter_pin_desc:
      "Ouvrez l'écran d'appairage sur l'application de bureau et saisissez le PIN à 6 chiffres de la carte de l'extension Chrome.",
    pairing_disabled_hint:
      "L'appairage n'accepte pas de demandes. Ouvrez l'écran d'appairage sur l'application de bureau, puis réessayez.",
    sas_mismatch:
      "Le code de vérification ne correspond pas — appairage interrompu par sécurité. Veuillez réessayer.",
    pin_expired_retry:
      "Le PIN a expiré. Saisissez le nouveau PIN affiché sur l'application de bureau.",
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
    paste_not_synced_title: "CrossPaste : collage depuis %s non synchronisé",
    paste_oversize_file: "« %s » (%s) dépasse la limite de %s par fichier",
    paste_oversize_total: "La taille totale %s dépasse la limite de %s",
    install_desktop_client: "Installer le client de bureau pour toutes les fonctionnalités",
    get_native_app: "Obtenir l'application native",
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
    enter_sas_code_desc:
      "デスクトップアプリに表示された6桁の確認コードを入力してください。両方のデバイスで一致している必要があります。",
    enter_pin_desc:
      "デスクトップアプリでペアリング画面を開き、Chrome拡張機能カードに表示された6桁のPINを入力してください。",
    pairing_disabled_hint:
      "ペアリングがリクエストを受け付けていません。デスクトップアプリでペアリング画面を開いてから、もう一度お試しください。",
    sas_mismatch:
      "確認コードが一致しません — 安全のためペアリングを中止しました。もう一度お試しください。",
    pin_expired_retry:
      "PINの有効期限が切れました。デスクトップアプリに表示された新しいPINを入力してください。",
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
    paste_not_synced_title: "CrossPaste: %s からのペーストは同期されませんでした",
    paste_oversize_file: "「%s」(%s) が1ファイルあたりの上限 %s を超えています",
    paste_oversize_total: "合計サイズ %s が上限 %s を超えています",
    install_desktop_client: "すべての機能を利用するためにデスクトップクライアントをインストール",
    get_native_app: "ネイティブアプリを入手",
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
    enter_sas_code_desc:
      "데스크톱 앱에 표시된 6자리 확인 코드를 입력하세요. 두 기기에서 일치해야 합니다.",
    enter_pin_desc:
      "데스크톱 앱에서 페어링 화면을 열고 Chrome 확장 프로그램 카드에 표시된 6자리 PIN을 입력하세요.",
    pairing_disabled_hint:
      "페어링이 요청을 받지 않고 있습니다. 데스크톱 앱에서 페어링 화면을 연 후 다시 시도하세요.",
    sas_mismatch:
      "확인 코드가 일치하지 않습니다 — 안전을 위해 페어링을 중단했습니다. 다시 시도하세요.",
    pin_expired_retry:
      "PIN이 만료되었습니다. 데스크톱 앱에 표시된 새 PIN을 입력하세요.",
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
    paste_not_synced_title: "CrossPaste: %s 의 붙여넣기가 동기화되지 않았습니다",
    paste_oversize_file: "\"%s\"(%s)이(가) 파일당 한도 %s 을(를) 초과합니다",
    paste_oversize_total: "총 크기 %s 이(가) 한도 %s 을(를) 초과합니다",
    install_desktop_client: "전체 기능을 위해 데스크톱 클라이언트 설치",
    get_native_app: "네이티브 앱 받기",
  },
  pt: {
    clipboard: "Área de transferência",
    desktop_app_active: "App de desktop em execução — extensão pausada",
    sync_status_paused: "Pausado",
    sync_status_connecting: "Conectando",
    sync_status_unverified: "Reemparelhar",
    sync_status_unmatched: "Chaves não coincidem",
    sync_status_incompatible: "Versão incompatível",
    repair_device: "Reemparelhar",
    connect: "Conectar",
    connect_code: "Código de conexão",
    connecting: "Conectando…",
    connection_failed_check:
      "Falha na conexão, verifique o código de conexão",
    device_note: "Nota do dispositivo",
    add_note_for: "Adicionar nota para %s",
    enter_note_name: "Insira o nome da nota",
    enter_pairing_code_desc:
      "Insira o código de emparelhamento de 6 dígitos exibido no app de desktop.",
    enter_sas_code_desc:
      "Insira o código de verificação de 6 dígitos exibido no app de desktop. Ele deve coincidir nos dois dispositivos.",
    enter_pin_desc:
      "Abra a tela de emparelhamento no app de desktop e insira o PIN de 6 dígitos do cartão da extensão do Chrome.",
    pairing_disabled_hint:
      "O emparelhamento não está aceitando solicitações. Abra a tela de emparelhamento no app de desktop e tente novamente.",
    sas_mismatch:
      "O código de verificação não confere — emparelhamento cancelado por segurança. Tente novamente.",
    pin_expired_retry:
      "O PIN expirou. Insira o novo PIN exibido no app de desktop.",
    re_enter: "Reinserir",
    verification_failed_retry: "Falha na verificação, tente novamente",
    time: "Hora",
    ip_address: "Endereço IP",
    devices_guide_title: "Apenas conexão manual",
    devices_guide_desc:
      "A extensão do Chrome é executada em uma sandbox do navegador e não pode transmitir na rede local, portanto outros clientes do CrossPaste não conseguem descobri-la automaticamente.",
    devices_guide_step1:
      "Encontre o IP e a porta do dispositivo no app de desktop: Configurações → Configurações de rede",
    devices_guide_step2:
      "Clique em \"Adicionar dispositivo\" abaixo e insira as informações de conexão",
    paste_not_synced_title: "CrossPaste: colagem de %s não sincronizada",
    paste_oversize_file: "\"%s\" (%s) excede o limite de %s por arquivo",
    paste_oversize_total: "Tamanho total %s excede o limite de %s",
    install_desktop_client:
      "Instale o cliente de desktop para a funcionalidade completa",
    get_native_app: "Obter o app nativo",
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
    enter_sas_code_desc: "请输入桌面端显示的 6 位校验码，两端必须一致。",
    enter_pin_desc:
      "请在桌面端打开配对界面，输入 Chrome 扩展卡片上显示的 6 位 PIN 码。",
    pairing_disabled_hint: "配对请求未被接受，请在桌面端打开配对界面后重试。",
    sas_mismatch: "校验码不一致——为安全起见已中止配对，请重试。",
    pin_expired_retry: "PIN 已过期，请输入桌面端显示的新 PIN。",
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
    paste_not_synced_title: "CrossPaste：来自 %s 的剪贴板未同步",
    paste_oversize_file: "文件「%s」大小为 %s，超过单文件上限 %s",
    paste_oversize_total: "总大小 %s 超过 %s 上限",
    install_desktop_client: "安装桌面客户端以支持完整功能",
    get_native_app: "获取原生应用",
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
    enter_sas_code_desc: "請輸入桌面端顯示的 6 位校驗碼，兩端必須一致。",
    enter_pin_desc:
      "請在桌面端開啟配對介面，輸入 Chrome 擴充功能卡片上顯示的 6 位 PIN 碼。",
    pairing_disabled_hint: "配對請求未被接受，請在桌面端開啟配對介面後重試。",
    sas_mismatch: "校驗碼不一致——為安全起見已中止配對，請重試。",
    pin_expired_retry: "PIN 已過期，請輸入桌面端顯示的新 PIN。",
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
    paste_not_synced_title: "CrossPaste：來自 %s 的剪貼簿未同步",
    paste_oversize_file: "檔案「%s」大小為 %s，超過單檔上限 %s",
    paste_oversize_total: "總大小 %s 超過 %s 上限",
    install_desktop_client: "安裝桌面客戶端以支援完整功能",
    get_native_app: "取得原生應用",
  },
};
