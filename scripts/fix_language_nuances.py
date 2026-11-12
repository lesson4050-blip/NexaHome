import os
import re
import xml.etree.ElementTree as ET

BASE_DIR = r"d:\TV\app\src\main\res"

CORRECTIONS = {
    "values-pl": {
        "title_movies": "Filmy",
        "title_series": "Seriale",
        "badge_movie": "FILM",
        "badge_series": "SERIAL",
        "nav_media": "Start",
        "nav_home_apps": "Aplikacje",
        "nav_iptv": "Kanały TV",
        "nav_games": "Gry",
        "nav_optimizer": "Optymalizacja",
        "nav_cast": "Przesyłanie ekranu",
        "nav_power": "Zasilanie",
        "tab_all": "Wszystkie",
        "tab_favorites": "Ulubione",
        "tab_hidden": "Ukryte",
        "power_dialog_title": "Zasilanie TV",
        "genre_action": "Akcja",
        "genre_adventure": "Przygodowy",
        "genre_scifi": "Sci-Fi",
        "genre_thriller": "Thriller",
        "genre_horror": "Horror"
    },
    "values-es": {
        "title_movies": "Películas",
        "title_series": "Series",
        "badge_movie": "PELÍCULA",
        "badge_series": "SERIE TV",
        "nav_media": "Inicio",
        "nav_home_apps": "Aplicaciones",
        "nav_iptv": "Canales TV",
        "nav_games": "Juegos",
        "nav_optimizer": "Optimización",
        "nav_cast": "Transmitir pantalla",
        "nav_power": "Encendido / Apagado",
        "tab_all": "Todas",
        "tab_favorites": "Favoritos",
        "tab_hidden": "Ocultas",
        "power_dialog_title": "Control de energía",
        "genre_action": "Acción",
        "genre_adventure": "Aventura",
        "genre_scifi": "Ciencia ficción",
        "genre_thriller": "Suspense",
        "genre_feature": "Película"
    },
    "values-fr": {
        "title_movies": "Films",
        "title_series": "Séries",
        "badge_movie": "FILM",
        "badge_series": "SÉRIE TV",
        "nav_media": "Accueil",
        "nav_home_apps": "Applications",
        "nav_iptv": "Chaînes TV",
        "nav_games": "Jeux",
        "nav_optimizer": "Optimisation",
        "nav_cast": "Diffusion d\'écran",
        "nav_power": "Alimentation",
        "tab_all": "Toutes",
        "tab_favorites": "Favoris",
        "tab_hidden": "Masquées",
        "power_dialog_title": "Alimentation TV",
        "genre_action": "Action",
        "genre_adventure": "Aventure",
        "genre_scifi": "Science-fiction",
        "genre_thriller": "Thriller",
        "genre_feature": "Long métrage"
    },
    "values-de": {
        "title_movies": "Filme",
        "title_series": "Serien",
        "badge_movie": "FILM",
        "badge_series": "TV-SERIE",
        "nav_media": "Start",
        "nav_home_apps": "Apps",
        "nav_iptv": "TV-Kanäle",
        "nav_games": "Spiele",
        "nav_optimizer": "Optimierung",
        "nav_cast": "Bildschirmübertragung",
        "nav_power": "Power-Menü",
        "tab_all": "Alle Apps",
        "tab_favorites": "Favoriten",
        "tab_hidden": "Ausgeblendet",
        "power_dialog_title": "Energie-Optionen",
        "genre_action": "Action",
        "genre_adventure": "Abenteuer",
        "genre_scifi": "Science-Fiction",
        "genre_thriller": "Thriller",
        "genre_feature": "Spielfilm"
    },
    "values-pt": {
        "title_movies": "Filmes",
        "title_series": "Séries",
        "badge_movie": "FILME",
        "badge_series": "SÉRIE TV",
        "nav_media": "Início",
        "nav_home_apps": "Aplicações",
        "nav_iptv": "Canais de TV",
        "nav_games": "Jogos",
        "nav_optimizer": "Otimização",
        "nav_cast": "Transmitir ecrã",
        "nav_power": "Energia",
        "tab_all": "Todas",
        "tab_favorites": "Favoritos",
        "tab_hidden": "Ocultos",
        "power_dialog_title": "Controlo de energia",
        "genre_action": "Ação",
        "genre_adventure": "Aventura",
        "genre_scifi": "Ficção científica",
        "genre_feature": "Longa-metragem"
    },
    "values-it": {
        "title_movies": "Film",
        "title_series": "Serie TV",
        "badge_movie": "FILM",
        "badge_series": "SERIE TV",
        "nav_media": "Home",
        "nav_home_apps": "App",
        "nav_iptv": "Canali TV",
        "nav_games": "Giochi",
        "nav_optimizer": "Ottimizzazione",
        "nav_cast": "Trasmissione schermo",
        "nav_power": "Alimentazione",
        "tab_all": "Tutte le app",
        "tab_favorites": "Preferiti",
        "tab_hidden": "Nascosti",
        "power_dialog_title": "Alimentazione TV"
    },
    "values-tr": {
        "title_movies": "Filmler",
        "title_series": "Diziler",
        "badge_movie": "FİLM",
        "badge_series": "DİZİ",
        "nav_media": "Ana Sayfa",
        "nav_home_apps": "Uygulamalar",
        "nav_iptv": "TV Kanalları",
        "nav_games": "Oyunlar",
        "nav_optimizer": "Optimizasyon",
        "nav_cast": "Ekran Yansıtma",
        "nav_power": "Güç Menüsü",
        "tab_all": "Tüm Uygulamalar",
        "tab_favorites": "Favoriler",
        "tab_hidden": "Gizli",
        "power_dialog_title": "Güç Yönetimi"
    },
    "values-zh": {
        "title_movies": "电影",
        "title_series": "电视剧",
        "badge_movie": "电影",
        "badge_series": "剧集",
        "nav_media": "主页",
        "nav_home_apps": "应用",
        "nav_iptv": "电视频道",
        "nav_games": "游戏",
        "nav_optimizer": "系统优化",
        "nav_cast": "无线投屏",
        "nav_power": "电源控制",
        "tab_all": "所有应用",
        "tab_favorites": "收藏",
        "tab_hidden": "隐藏应用",
        "power_dialog_title": "电源管理"
    },
    "values-ja": {
        "title_movies": "映画",
        "title_series": "ドラマ・シリーズ",
        "badge_movie": "映画",
        "badge_series": "ドラマ",
        "nav_media": "ホーム",
        "nav_home_apps": "アプリ",
        "nav_iptv": "TVチャンネル",
        "nav_games": "ゲーム",
        "nav_optimizer": "最適化",
        "nav_cast": "画面ミラーリング",
        "nav_power": "電源メニュー",
        "tab_all": "すべてのアプリ",
        "tab_favorites": "お気に入り",
        "tab_hidden": "非表示",
        "power_dialog_title": "電源管理"
    },
    "values-ko": {
        "title_movies": "영화",
        "title_series": "시리즈",
        "badge_movie": "영화",
        "badge_series": "TV 시리즈",
        "nav_media": "홈",
        "nav_home_apps": "앱",
        "nav_iptv": "TV 채널",
        "nav_games": "게임",
        "nav_optimizer": "최적화",
        "nav_cast": "화면 전송",
        "nav_power": "전원 관리",
        "tab_all": "모든 앱",
        "tab_favorites": "즐겨찾기",
        "tab_hidden": "숨김",
        "power_dialog_title": "전원 옵션"
    },
    "values-ar": {
        "title_movies": "أفلام",
        "title_series": "مسلسلات",
        "badge_movie": "فيلم",
        "badge_series": "مسلسل",
        "nav_media": "الرئيسية",
        "nav_home_apps": "التطبيقات",
        "nav_iptv": "قنوات التلفاز",
        "nav_games": "الألعاب",
        "nav_optimizer": "تحسين النظام",
        "nav_cast": "بث الشاشة",
        "nav_power": "إدارة الطاقة",
        "tab_all": "جميع التطبيقات",
        "tab_favorites": "المفضلة",
        "tab_hidden": "المخفية",
        "power_dialog_title": "إدارة الطاقة"
    }
}

for folder, fixes in CORRECTIONS.items():
    file_path = os.path.join(BASE_DIR, folder, "strings.xml")
    if not os.path.exists(file_path):
        continue
    
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    for key, correct_val in fixes.items():
        # Escape for XML
        escaped_val = correct_val.replace("'", "\\'").replace('"', '\\"') if "'" in correct_val and "\\'" not in correct_val else correct_val
        # Match <string name="key">...</string>
        pattern = rf'(<string\s+name="{key}">)(.*?)(</string>)'
        replacement = rf'\g<1>{escaped_val}\g<3>'
        content = re.sub(pattern, replacement, content)

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"Patched {folder} with {len(fixes)} refined translations.")
