import urllib.request
import re
import json

def get_play_icon(package_name):
    url = f"https://play.google.com/store/apps/details?id={package_name}&hl=en"
    req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
    try:
        html = urllib.request.urlopen(req, timeout=10).read().decode('utf-8', errors='ignore')
        # Google play icons usually match play-lh.googleusercontent.com/...
        # or look for meta property="og:image"
        og = re.search(r'<meta property="og:image" content="([^"]+)"', html)
        if og:
            return og.group(1)
        matches = re.findall(r'https://play-lh\.googleusercontent\.com/[a-zA-Z0-9_\-]+', html)
        if matches:
            return matches[0]
    except Exception as e:
        return f"Error: {e}"
    return None

apps = [
    ("AirScreen", "com.ionitech.airscreen"),
    ("Proton VPN", "ch.protonvpn.android"),
    ("VLC", "org.videolan.vlc"),
    ("Nova", "org.courville.nova"),
    ("Kodi", "org.xbmc.kodi")
]

for name, pkg in apps:
    icon_url = get_play_icon(pkg)
    print(f"{name} ({pkg}): {icon_url}")
