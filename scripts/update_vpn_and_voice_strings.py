import os
import re

BASE_DIR = r"d:\TV\app\src\main\res"

STRINGS_DATA = {
    "values": {
        "search_voice_listening": "Listening…",
        "search_voice_processing": "Recognizing…",
        "vpn_btn_connect": "Connect VPN",
        "vpn_btn_disconnect": "Disconnect VPN",
        "vpn_locations_title": "Server Location",
        "vpn_loc_warp": "⚡ Cloudflare WARP",
        "vpn_loc_us": "🇺🇸 United States",
        "vpn_loc_de": "🇩🇪 Germany",
        "vpn_loc_pl": "🇵🇱 Poland"
    },
    "values-ru": {
        "search_voice_listening": "Слушаю…",
        "search_voice_processing": "Распознавание…",
        "vpn_btn_connect": "Подключить VPN",
        "vpn_btn_disconnect": "Отключить VPN",
        "vpn_locations_title": "Локация / Сервер",
        "vpn_loc_warp": "⚡ Cloudflare WARP",
        "vpn_loc_us": "🇺🇸 США",
        "vpn_loc_de": "🇩🇪 Германия",
        "vpn_loc_pl": "🇵🇱 Польша"
    },
    "values-uk": {
        "search_voice_listening": "Слухаю…",
        "search_voice_processing": "Розпізнавання…",
        "vpn_btn_connect": "Підключити VPN",
        "vpn_btn_disconnect": "Вимкнути VPN",
        "vpn_locations_title": "Локація / Сервер",
        "vpn_loc_warp": "⚡ Cloudflare WARP",
        "vpn_loc_us": "🇺🇸 США",
        "vpn_loc_de": "🇩🇪 Німеччина",
        "vpn_loc_pl": "🇵🇱 Польща"
    },
    "values-pl": {
        "search_voice_listening": "Słucham…",
        "search_voice_processing": "Rozpoznawanie…",
        "vpn_btn_connect": "Połącz z VPN",
        "vpn_btn_disconnect": "Rozłącz VPN",
        "vpn_locations_title": "Lokalizacja serwera",
        "vpn_loc_warp": "⚡ Cloudflare WARP",
        "vpn_loc_us": "🇺🇸 Stany Zjednoczone",
        "vpn_loc_de": "🇩🇪 Niemcy",
        "vpn_loc_pl": "🇵🇱 Polska"
    },
    "values-de": {
        "search_voice_listening": "Zuhören…",
        "search_voice_processing": "Erkennen…",
        "vpn_btn_connect": "VPN verbinden",
        "vpn_btn_disconnect": "VPN trennen",
        "vpn_locations_title": "Server-Standort",
        "vpn_loc_warp": "⚡ Cloudflare WARP",
        "vpn_loc_us": "🇺🇸 USA",
        "vpn_loc_de": "🇩🇪 Deutschland",
        "vpn_loc_pl": "🇵🇱 Polen"
    },
    "values-fr": {
        "search_voice_listening": "Écoute en cours…",
        "search_voice_processing": "Reconnaissance…",
        "vpn_btn_connect": "Connecter le VPN",
        "vpn_btn_disconnect": "Déconnecter le VPN",
        "vpn_locations_title": "Emplacement du serveur",
        "vpn_loc_warp": "⚡ Cloudflare WARP",
        "vpn_loc_us": "🇺🇸 États-Unis",
        "vpn_loc_de": "🇩🇪 Allemagne",
        "vpn_loc_pl": "🇵🇱 Pologne"
    },
    "values-es": {
        "search_voice_listening": "Escuchando…",
        "search_voice_processing": "Reconociendo…",
        "vpn_btn_connect": "Conectar VPN",
        "vpn_btn_disconnect": "Desconectar VPN",
        "vpn_locations_title": "Ubicación del servidor",
        "vpn_loc_warp": "⚡ Cloudflare WARP",
        "vpn_loc_us": "🇺🇸 Estados Unidos",
        "vpn_loc_de": "🇩🇪 Alemania",
        "vpn_loc_pl": "🇵🇱 Polonia"
    },
    "values-pt": {
        "search_voice_listening": "Ouvindo…",
        "search_voice_processing": "Reconhecendo…",
        "vpn_btn_connect": "Conectar VPN",
        "vpn_btn_disconnect": "Desconectar VPN",
        "vpn_locations_title": "Localização do servidor",
        "vpn_loc_warp": "⚡ Cloudflare WARP",
        "vpn_loc_us": "🇺🇸 Estados Unidos",
        "vpn_loc_de": "🇩🇪 Alemanha",
        "vpn_loc_pl": "🇵🇱 Polônia"
    },
    "values-it": {
        "search_voice_listening": "In ascolto…",
        "search_voice_processing": "Riconoscimento…",
        "vpn_btn_connect": "Connetti VPN",
        "vpn_btn_disconnect": "Disconnetti VPN",
        "vpn_locations_title": "Posizione del server",
        "vpn_loc_warp": "⚡ Cloudflare WARP",
        "vpn_loc_us": "🇺🇸 Stati Uniti",
        "vpn_loc_de": "🇩🇪 Germania",
        "vpn_loc_pl": "🇵🇱 Polonia"
    },
    "values-tr": {
        "search_voice_listening": "Dinleniyor…",
        "search_voice_processing": "Tanınıyor…",
        "vpn_btn_connect": "VPN Bağlan",
        "vpn_btn_disconnect": "VPN Bağlantısını Kes",
        "vpn_locations_title": "Sunucu Konumu",
        "vpn_loc_warp": "⚡ Cloudflare WARP",
        "vpn_loc_us": "🇺🇸 ABD",
        "vpn_loc_de": "🇩🇪 Almanya",
        "vpn_loc_pl": "🇵🇱 Polonya"
    },
    "values-zh": {
        "search_voice_listening": "正在聆听…",
        "search_voice_processing": "正在识别…",
        "vpn_btn_connect": "连接 VPN",
        "vpn_btn_disconnect": "断开 VPN",
        "vpn_locations_title": "服务器位置",
        "vpn_loc_warp": "⚡ Cloudflare WARP",
        "vpn_loc_us": "🇺🇸 美国",
        "vpn_loc_de": "🇩🇪 德国",
        "vpn_loc_pl": "🇵🇱 波兰"
    },
    "values-ja": {
        "search_voice_listening": "聞き取り中…",
        "search_voice_processing": "認識中…",
        "vpn_btn_connect": "VPN に接続",
        "vpn_btn_disconnect": "VPN を切断",
        "vpn_locations_title": "サーバーの場所",
        "vpn_loc_warp": "⚡ Cloudflare WARP",
        "vpn_loc_us": "🇺🇸 米国",
        "vpn_loc_de": "🇩🇪 ドイツ",
        "vpn_loc_pl": "🇵🇱 ポーランド"
    },
    "values-ko": {
        "search_voice_listening": "듣는 중…",
        "search_voice_processing": "인식 중…",
        "vpn_btn_connect": "VPN 연결",
        "vpn_btn_disconnect": "VPN 연결 해제",
        "vpn_locations_title": "서버 위치",
        "vpn_loc_warp": "⚡ Cloudflare WARP",
        "vpn_loc_us": "🇺🇸 미국",
        "vpn_loc_de": "🇩🇪 독일",
        "vpn_loc_pl": "🇵🇱 폴란드"
    },
    "values-ar": {
        "search_voice_listening": "جارٍ الاستماع…",
        "search_voice_processing": "جارٍ التعرف…",
        "vpn_btn_connect": "الاتصال بالـ VPN",
        "vpn_btn_disconnect": "قطع اتصال الـ VPN",
        "vpn_locations_title": "موقع الخادم",
        "vpn_loc_warp": "⚡ Cloudflare WARP",
        "vpn_loc_us": "🇺🇸 الولايات المتحدة",
        "vpn_loc_de": "🇩🇪 ألمانيا",
        "vpn_loc_pl": "🇵🇱 بولندا"
    }
}

for folder, strings_dict in STRINGS_DATA.items():
    file_path = os.path.join(BASE_DIR, folder, "strings.xml")
    if not os.path.exists(file_path):
        continue
    
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()
    
    new_xml_lines = []
    for key, val in strings_dict.items():
        if f'name="{key}"' not in content:
            new_xml_lines.append(f'    <string name="{key}">{val}</string>')
    
    if new_xml_lines:
        insert_block = "\n" + "\n".join(new_xml_lines) + "\n</resources>"
        content = re.sub(r'\s*</resources>', insert_block, content)
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"Updated {folder}")
