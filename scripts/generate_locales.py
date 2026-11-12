import os
import re
import time
import json
import urllib.request
import urllib.parse
import xml.etree.ElementTree as ET

BASE_DIR = r"d:\TV\app\src\main\res"
SOURCE_XML = os.path.join(BASE_DIR, "values", "strings.xml")

# Languages to generate: (folder_name, google_lang_code)
LANGUAGES = [
    ("values-fr", "fr"),    # French
    ("values-es", "es"),    # Spanish
    ("values-de", "de"),    # German
    ("values-pt", "pt"),    # Portuguese
    ("values-it", "it"),    # Italian
    ("values-tr", "tr"),    # Turkish
    ("values-uk", "uk"),    # Ukrainian
    ("values-pl", "pl"),    # Polish
    ("values-zh", "zh-CN"), # Chinese (Simplified)
    ("values-ja", "ja"),    # Japanese
    ("values-ko", "ko"),    # Korean
    ("values-ar", "ar"),    # Arabic
]

# Keys that must NOT be translated (brand names, technical URLs, package names, etc.)
DO_NOT_TRANSLATE_KEYS = {
    "app_name",
    "dns_adguard",
    "dns_cloudflare",
    "dns_quad9",
    "dns_google",
    "dns_control_d",
    "search_youtube_query",
    "game_2048_url",
    "game_flappy_url",
    "game_snake_url",
    "game_hexxagon_url",
    "game_tetris_url",
    "game_pacman_url"
}

def escape_android_xml(text):
    if not text:
        return ""
    # Android XML escaping
    # Replace & first if not already an entity
    text = re.sub(r'&(?!(amp|lt|gt|quot|apos);)', '&amp;', text)
    text = text.replace('<', '&lt;').replace('>', '&gt;')
    # Escape single quotes if not escaped
    text = re.sub(r"(?<!\\)'", r"\'", text)
    # Escape double quotes if not escaped
    text = re.sub(r'(?<!\\)"', r'\"', text)
    # Escape @ and ? at the beginning of a string
    if text.startswith('@') or text.startswith('?'):
        text = '\\' + text
    return text

def parse_source():
    tree = ET.parse(SOURCE_XML)
    root = tree.getroot()
    entries = []
    for elem in root.findall('string'):
        name = elem.get('name')
        text = elem.text or ""
        entries.append((name, text))
    return entries

def translate_batch(texts, target_lang):
    if not texts:
        return []
    
    # Protect placeholders like %s, %1$s, %d, %2$d
    clean_texts = []
    placeholders_map = []
    
    for text in texts:
        phs = re.findall(r'%\d*\$?[a-zA-Z]', text)
        placeholders_map.append(phs)
        temp = text
        for i, ph in enumerate(phs):
            temp = temp.replace(ph, f'__PH_{i}__', 1)
        clean_texts.append(temp)

    # Join with separator
    delimiter = "\n[[[DELIM]]]\n"
    combined = delimiter.join(clean_texts)

    url = f"https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl={target_lang}&dt=t&q=" + urllib.parse.quote(combined)
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
    
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=15) as res:
                data = json.loads(res.read().decode('utf-8'))
                full_translated = "".join([part[0] for part in data[0] if part[0]])
                
                parts = re.split(r'\s*\[\[\[\s*DELIM\s*\]\]\]\s*', full_translated)
                if len(parts) == len(texts):
                    results = []
                    for part, phs in zip(parts, placeholders_map):
                        t = part.strip()
                        for i, ph in enumerate(phs):
                            t = re.sub(rf'__\s*PH_{i}\s*__', ph, t)
                        results.append(t)
                    return results
                else:
                    # Delimiter count mismatch fallback: translate one by one for this batch
                    break
        except Exception as e:
            time.sleep(1.0)

    # Fallback: translate individually
    fallback_results = []
    for text, phs in zip(clean_texts, placeholders_map):
        try:
            single_url = f"https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl={target_lang}&dt=t&q=" + urllib.parse.quote(text)
            single_req = urllib.request.Request(single_url, headers={'User-Agent': 'Mozilla/5.0'})
            with urllib.request.urlopen(single_req, timeout=10) as sres:
                sdata = json.loads(sres.read().decode('utf-8'))
                res_text = "".join([p[0] for p in sdata[0] if p[0]]).strip()
                for i, ph in enumerate(phs):
                    res_text = re.sub(rf'__\s*PH_{i}\s*__', ph, res_text)
                fallback_results.append(res_text)
        except Exception:
            fallback_results.append(text)
        time.sleep(0.05)
    return fallback_results

def main():
    entries = parse_source()
    print(f"Loaded {len(entries)} keys from source English strings.xml")

    for folder_name, lang_code in LANGUAGES:
        target_dir = os.path.join(BASE_DIR, folder_name)
        os.makedirs(target_dir, exist_ok=True)
        target_file = os.path.join(target_dir, "strings.xml")

        print(f"\nProcessing {folder_name} (Language: {lang_code})...")

        translated_entries = []
        batch_keys = []
        batch_texts = []

        for name, text in entries:
            if name in DO_NOT_TRANSLATE_KEYS or not text.strip():
                translated_entries.append((name, text))
            else:
                batch_keys.append(name)
                batch_texts.append(text)

        # Translate in batches of 20
        batch_size = 20
        all_translated_texts = []
        for i in range(0, len(batch_texts), batch_size):
            chunk = batch_texts[i:i + batch_size]
            trans_chunk = translate_batch(chunk, lang_code)
            all_translated_texts.extend(trans_chunk)
            print(f"  Translated {len(all_translated_texts)} / {len(batch_texts)} keys...", end="\r")
            time.sleep(0.2)

        trans_map = dict(zip(batch_keys, all_translated_texts))

        # Build output XML
        xml_lines = [
            '<?xml version="1.0" encoding="utf-8"?>',
            '<!-- Automated adaptive translation bundle for Orbit TV Launcher -->',
            '<resources>'
        ]

        for name, orig_text in entries:
            val = trans_map.get(name, orig_text)
            val_escaped = escape_android_xml(val)
            xml_lines.append(f'    <string name="{name}">{val_escaped}</string>')

        xml_lines.append('</resources>')
        xml_lines.append('')

        with open(target_file, "w", encoding="utf-8") as f:
            f.write("\n".join(xml_lines))

        # Validate well-formedness
        try:
            check_tree = ET.parse(target_file)
            check_root = check_tree.getroot()
            count = len(check_root.findall('string'))
            print(f"  [SUCCESS] {target_file} written with {count} verified keys.")
        except Exception as e:
            print(f"  [ERROR] XML syntax error in {target_file}: {e}")

if __name__ == "__main__":
    main()
