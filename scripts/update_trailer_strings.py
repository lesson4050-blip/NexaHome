import os
import re

BASE_DIR = r"d:\TV\app\src\main\res"

DATA = {
    "values": {
        "corrections": {},
        "additions": {
            "trailer_title_format": "%1$s — Trailer",
            "trailer_btn_open_yt": "Open in YouTube",
            "trailer_loading": "Loading trailer…"
        }
    },
    "values-ru": {
        "corrections": {},
        "additions": {
            "trailer_title_format": "%1$s — Трейлер",
            "trailer_btn_open_yt": "Открыть в YouTube",
            "trailer_loading": "Загрузка трейлера…"
        }
    },
    "values-uk": {
        "corrections": {},
        "additions": {
            "trailer_title_format": "%1$s — Трейлер",
            "trailer_btn_open_yt": "Відкрити в YouTube",
            "trailer_loading": "Завантаження трейлера…"
        }
    },
    "values-pl": {
        "corrections": {
            "media_btn_trailer": "Zwiastun"
        },
        "additions": {
            "trailer_title_format": "%1$s — Zwiastun",
            "trailer_btn_open_yt": "Otwórz w YouTube",
            "trailer_loading": "Ładowanie zwiastuna…"
        }
    },
    "values-de": {
        "corrections": {
            "media_btn_trailer": "Trailer"
        },
        "additions": {
            "trailer_title_format": "%1$s — Trailer",
            "trailer_btn_open_yt": "In YouTube öffnen",
            "trailer_loading": "Trailer wird geladen…"
        }
    },
    "values-fr": {
        "corrections": {},
        "additions": {
            "trailer_title_format": "%1$s — Bande-annonce",
            "trailer_btn_open_yt": "Ouvrir dans YouTube",
            "trailer_loading": "Chargement de la bande-annonce…"
        }
    },
    "values-es": {
        "corrections": {},
        "additions": {
            "trailer_title_format": "%1$s — Tráiler",
            "trailer_btn_open_yt": "Abrir en YouTube",
            "trailer_loading": "Cargando tráiler…"
        }
    },
    "values-pt": {
        "corrections": {
            "media_btn_trailer": "Trailer"
        },
        "additions": {
            "trailer_title_format": "%1$s — Trailer",
            "trailer_btn_open_yt": "Abrir no YouTube",
            "trailer_loading": "Carregando trailer…"
        }
    },
    "values-it": {
        "corrections": {
            "media_btn_trailer": "Trailer"
        },
        "additions": {
            "trailer_title_format": "%1$s — Trailer",
            "trailer_btn_open_yt": "Apri in YouTube",
            "trailer_loading": "Caricamento trailer…"
        }
    },
    "values-tr": {
        "corrections": {},
        "additions": {
            "trailer_title_format": "%1$s — Fragman",
            "trailer_btn_open_yt": "YouTube\\'da Aç",
            "trailer_loading": "Fragman yükleniyor…"
        }
    },
    "values-zh": {
        "corrections": {
            "media_btn_trailer": "预告片"
        },
        "additions": {
            "trailer_title_format": "%1$s — 预告片",
            "trailer_btn_open_yt": "在 YouTube 中打开",
            "trailer_loading": "正在加载预告片…"
        }
    },
    "values-ja": {
        "corrections": {
            "media_btn_trailer": "予告編"
        },
        "additions": {
            "trailer_title_format": "%1$s — 予告編",
            "trailer_btn_open_yt": "YouTubeで開く",
            "trailer_loading": "予告編を読み込み中…"
        }
    },
    "values-ko": {
        "corrections": {
            "media_btn_trailer": "예고편"
        },
        "additions": {
            "trailer_title_format": "%1$s — 예고편",
            "trailer_btn_open_yt": "YouTube에서 열기",
            "trailer_loading": "예고편 로드 중…"
        }
    },
    "values-ar": {
        "corrections": {},
        "additions": {
            "trailer_title_format": "%1$s — مقطع دعائي",
            "trailer_btn_open_yt": "فتح في YouTube",
            "trailer_loading": "جارٍ تحميل المقطع الدعائي…"
        }
    }
}

for folder, cfg in DATA.items():
    file_path = os.path.join(BASE_DIR, folder, "strings.xml")
    if not os.path.exists(file_path):
        continue
    
    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()
    
    # 1. Apply corrections
    for key, val in cfg["corrections"].items():
        pattern = re.compile(rf'<string name="{key}">.*?</string>')
        replacement = f'<string name="{key}">{val}</string>'
        content = pattern.sub(replacement, content)
    
    # 2. Add new strings if not present
    new_xml_lines = []
    for key, val in cfg["additions"].items():
        if f'name="{key}"' not in content:
            new_xml_lines.append(f'    <string name="{key}">{val}</string>')
    
    if new_xml_lines:
        insert_block = "\n" + "\n".join(new_xml_lines) + "\n</resources>"
        content = re.sub(r'\s*</resources>', insert_block, content)
        
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Updated {folder}")
