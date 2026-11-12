import urllib.request
import urllib.error
import re
import socket
import concurrent.futures
import sys
import xml.etree.ElementTree as ET

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')


# Timeout for testing each candidate stream
STREAM_TIMEOUT = 4.0
USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

CANDIDATE_PLAYLISTS = [
    ("Ukraine", "https://iptv-org.github.io/iptv/countries/ua.m3u"),
    ("Kazakhstan", "https://iptv-org.github.io/iptv/countries/kz.m3u"),
    ("Moldova", "https://iptv-org.github.io/iptv/countries/md.m3u"),
    ("News", "https://iptv-org.github.io/iptv/categories/news.m3u"),
    ("Sports", "https://iptv-org.github.io/iptv/categories/sports.m3u"),
    ("Music", "https://iptv-org.github.io/iptv/categories/music.m3u"),
    ("Movies", "https://iptv-org.github.io/iptv/categories/movies.m3u"),
    ("Animation", "https://iptv-org.github.io/iptv/categories/animation.m3u"),
    ("Documentary", "https://iptv-org.github.io/iptv/categories/documentary.m3u"),
]

# Curated candidates known to be high quality
CURATED_CANDIDATES = [
    # Sports
    ("redbull.tv", "Red Bull TV HD", "Sports", "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8", "https://raw.githubusercontent.com/iptv-org/epg/master/sites/redbull.com/redbull.com.png"),
    ("unbeaten.sports", "Unbeaten Sports HD", "Sports", "https://stream-sports.canaloff.globo.com/live/sports/master.m3u8", "https://i.imgur.com/8rVp9u7.png"),
    ("edge.sport", "Edge Sport HD", "Sports", "https://edgesport-samsunguk.amagi.tv/playlist.m3u8", "https://i.imgur.com/uG9Xb7G.png"),
    
    # News
    ("dw.news", "DW News English HD", "News", "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8", "https://raw.githubusercontent.com/iptv-org/epg/master/sites/dw.com/dw.com.png"),
    ("skynews", "Sky News UK HD", "News", "https://dash4.antik.sk/live/test_skynews/playlist.m3u8", "https://d2n0069hmnqmmx.cloudfront.net/epgdata/1.0/newchanlogos/512/512/skychb1404.png"),
    ("euronews.en", "Euronews English HD", "News", "https://dash4.antik.sk/live/test_euronews/playlist.m3u8", "https://jiotvimages.cdn.jio.com/dare_images/images/Euro_News.png"),
    ("aljazeera.en", "Al Jazeera English HD", "News", "https://live-hls-apps-aje-fa.getaj.net/AJE/index.m3u8", "https://i.imgur.com/7bRVpnu.png"),
    ("france24.en", "France 24 English", "News", "https://stream.france24.com/hls/live/2034309/F24_EN_LO_HLS/master.m3u8", "https://raw.githubusercontent.com/iptv-org/epg/master/sites/france24.com/france24.com.png"),

    # Ukraine & CIS
    ("1plus1.marafon", "1+1 Марафон HD", "Ukraine", "https://dash2.antik.sk/live/1plus1_marathon/playlist.m3u8", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/1%2B1_channel_logo.svg/512px-1%2B1_channel_logo.svg.png"),
    ("1plus1.int", "1+1 International HD", "Ukraine", "https://dash2.antik.sk/live/test_one_plus_one_int_tizen/playlist.m3u8", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cf/1%2B1_channel_logo.svg/512px-1%2B1_channel_logo.svg.png"),
    ("24tv.ua", "24 Канал HD", "Ukraine", "https://streamvideol1.luxnet.ua/news24/livenews.m3u8", "https://streamvideol1.luxnet.ua/news24/logo.png"),
    ("rada.ua", "Телеканал Рада HD", "Ukraine", "https://dash2.antik.sk/live/test_rada/playlist.m3u8", "https://upload.wikimedia.org/wikipedia/commons/3/30/Rada_TV_Channel_Logo.png"),
    ("dim.ua", "ДІМ Телеканал HD", "Ukraine", "https://dash2.antik.sk/live/test_kanal_dom/playlist.m3u8", "https://i.imgur.com/miq8O1Q.png"),
    ("freedom.ua", "FREEДОМ HD", "Ukraine", "https://dash2.antik.sk/live/test_uatv/playlist.m3u8", "https://i.imgur.com/5UxU4zc.png"),
    ("habar24.kz", "Хабар 24 HD", "CIS", "https://stream.khabar.kz/24kz/index.m3u8", "https://i.imgur.com/Qh0n8tQ.png"),
    ("qazaqstan.kz", "Qazaqstan TV", "CIS", "https://stream.qazaqstan.tv/qazaqstan/index.m3u8", "https://i.imgur.com/o7b89t1.png"),
    ("moldova1.md", "Moldova 1 HD", "CIS", "https://live.trm.md/hls/m1_1080p/index.m3u8", "https://i.imgur.com/H1tU4P1.png"),
    ("jurnal.md", "Jurnal TV HD", "CIS", "https://live.jurnaltv.md/live/jurnaltv.m3u8", "https://i.imgur.com/yG1r4a0.png"),

    # Music
    ("clubbing.tv", "Clubbing TV HD", "Music", "https://stream.clubbingtv.com/hls/live/master.m3u8", "https://i.imgur.com/Y3aA5l6.png"),
    ("musicbox.ua", "Music Box HD", "Music", "https://dash2.antik.sk/live/test_music_box/playlist.m3u8", "https://i.imgur.com/7Xq5F7X.png"),
    ("party.tv", "Party TV Live", "Music", "https://party-tv-live.samsung.wurl.tv/playlist.m3u8", "https://i.imgur.com/8t9mdg9.png"),
    ("stingray.cmusic", "Stingray CMusic", "Music", "https://stingraycmusic-rakuten.amagi.tv/playlist.m3u8", "https://i.imgur.com/miq8O1Q.png"),

    # Movies / Series / Entertainment
    ("rakuten.action", "Rakuten TV Action Movies", "Movies", "https://rakuten-actionmovies-1-eu.rakuten.wurl.tv/playlist.m3u8", "https://i.imgur.com/miq8O1Q.png"),
    ("rakuten.comedy", "Rakuten TV Comedy", "Movies", "https://rakuten-comedy-1-eu.rakuten.wurl.tv/playlist.m3u8", "https://i.imgur.com/miq8O1Q.png"),
    ("samsung.movies", "Pluto TV Movies", "Movies", "https://service-stitcher.clusters.pluto.tv/v1/stitch/hls/channel/5d8b846e16f3ea8a688a221f/master.m3u8?deviceType=samsung-tvplus", "https://i.imgur.com/8rVp9u7.png"),
    ("filmrise.free", "FilmRise Free Movies", "Movies", "https://filmrise-action-samsungus.amagi.tv/playlist.m3u8", "https://i.imgur.com/Y3aA5l6.png"),

    # Documentary / Nature
    ("nature.relax", "Nature Relax 4K", "Documentary", "https://stream-relay.naturevisiontv.com/hls/nature/master.m3u8", "https://i.imgur.com/YQvLPT1.png"),
    ("nasa.live", "NASA TV Space", "Documentary", "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8", "https://raw.githubusercontent.com/iptv-org/epg/master/sites/nasa.gov/nasa.gov.png"),
    ("wildearth.tv", "WildEarth Safari Live", "Documentary", "https://wildearth-samsung.amagi.tv/playlist.m3u8", "https://i.imgur.com/8rVp9u7.png"),
]

def test_stream_url(stream_info):
    chan_id, name, group, url, logo = stream_info
    try:
        req = urllib.request.Request(
            url,
            headers={
                "User-Agent": USER_AGENT,
                "Accept": "*/*"
            }
        )
        with urllib.request.urlopen(req, timeout=STREAM_TIMEOUT) as response:
            code = response.getcode()
            if code != 200:
                return None
            
            # Read first 1024 bytes to verify it's valid HLS
            chunk = response.read(1024).decode('utf-8', errors='ignore')
            if "#EXTM3U" not in chunk and "#EXT-X-" not in chunk:
                return None
            
            # Check for common geo-block / error indicators
            lower_chunk = chunk.lower()
            if "geoblock" in lower_chunk or "access denied" in lower_chunk or "forbidden" in lower_chunk:
                return None
            
            return (chan_id, name, group, url, logo)
    except Exception as e:
        return None

def fetch_and_test_playlist(country_name, m3u_url, max_scan=40):
    print(f"Scanning {country_name} from {m3u_url}...")
    try:
        req = urllib.request.Request(m3u_url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=8.0) as resp:
            content = resp.read().decode('utf-8', errors='ignore')
    except Exception as e:
        print(f"Failed to fetch {country_name}: {e}")
        return []

    lines = content.splitlines()
    candidates = []
    current_extinf = None
    
    logo_re = re.compile(r'tvg-logo="([^"]+)"')
    id_re = re.compile(r'tvg-id="([^"]+)"')
    group_re = re.compile(r'group-title="([^"]+)"')

    for line in lines:
        line = line.strip()
        if line.startswith("#EXTINF:"):
            current_extinf = line
        elif line and not line.startswith("#"):
            stream_url = line
            extinf = current_extinf or ""
            
            # Skip if explicitly geo-blocked in tag
            if "[geo-blocked]" in extinf.lower():
                current_extinf = None
                continue
                
            m_id = id_re.search(extinf)
            m_logo = logo_re.search(extinf)
            m_group = group_re.search(extinf)
            
            comma = extinf.rfind(',')
            name = extinf[comma+1:].strip() if comma != -1 else "Live TV"
            name = re.sub(r'\s*\(\d+p\)', '', name) # remove resolution suffix
            
            cid = m_id.group(1) if m_id else f"chan_{len(candidates)}"
            logo = m_logo.group(1) if m_logo else "https://raw.githubusercontent.com/iptv-org/epg/master/sites/redbull.com/redbull.com.png"
            grp = m_group.group(1) if m_group else country_name
            
            candidates.append((cid, name, grp, stream_url, logo))
            current_extinf = None
            if len(candidates) >= max_scan:
                break

    print(f"Testing {len(candidates)} streams for {country_name}...")
    valid_streams = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
        results = executor.map(test_stream_url, candidates)
        for r in results:
            if r:
                valid_streams.append(r)
                print(f"  [OK] {r[1]} ({r[2]}) -> {r[3][:40]}...")
                
    return valid_streams

def main():
    print("=== STARTING FULL STREAM VERIFICATION ===")
    verified_channels = []

    # 1. Test curated channels
    print(f"Testing {len(CURATED_CANDIDATES)} hand-curated channels...")
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        results = executor.map(test_stream_url, CURATED_CANDIDATES)
        for r in results:
            if r:
                verified_channels.append(r)
                print(f"  [OK CURATED] {r[1]} -> {r[2]}")

    # 2. Fetch extra from playlists to cover all categories
    for country, url in CANDIDATE_PLAYLISTS:
        extras = fetch_and_test_playlist(country, url, max_scan=25)
        for item in extras:
            # Dedup by URL
            if not any(v[3] == item[3] for v in verified_channels):
                # Clean group name
                cid, name, grp, stream_url, logo = item
                if country in ["Ukraine"]:
                    grp = "Ukraine"
                elif country in ["Kazakhstan", "Moldova"]:
                    grp = "CIS"
                elif country in ["News"]:
                    grp = "News"
                elif country in ["Sports"]:
                    grp = "Sports"
                elif country in ["Music"]:
                    grp = "Music"
                elif country in ["Movies"]:
                    grp = "Movies"
                elif country in ["Animation"]:
                    grp = "Kids"
                elif country in ["Documentary"]:
                    grp = "Documentary"
                
                verified_channels.append((cid, name, grp, stream_url, logo))

    print(f"\nTOTAL 100% VERIFIED STREAMS: {len(verified_channels)}")

    # Sort channels by group and name
    group_order = {"Ukraine": 1, "CIS": 2, "News": 3, "Sports": 4, "Movies": 5, "Music": 6, "Kids": 7, "Documentary": 8}
    verified_channels.sort(key=lambda x: (group_order.get(x[2], 99), x[1]))

    # Generate default_channels.m3u
    m3u_lines = ["#EXTM3U"]
    for idx, (cid, name, grp, url, logo) in enumerate(verified_channels, start=1):
        num_str = f"{idx:03d}"
        display_title = f"{num_str} {name}"
        m3u_lines.append(f'#EXTINF:-1 tvg-id="{cid}" tvg-name="{name}" tvg-logo="{logo}" group-title="{grp}",{display_title}')
        m3u_lines.append(url)

    m3u_content = "\n".join(m3u_lines) + "\n"
    with open("app/src/main/assets/default_channels.m3u", "w", encoding="utf-8") as f:
        f.write(m3u_content)
    print("Wrote app/src/main/assets/default_channels.m3u")

    # Generate default_epg.xml
    epg_lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<tv generator-info-name="OrbitLeanbackEPG">'
    ]
    for cid, name, grp, url, logo in verified_channels:
        epg_lines.append(f'  <channel id="{cid}">')
        epg_lines.append(f'    <display-name>{name}</display-name>')
        epg_lines.append('  </channel>')

    # Generate 24h schedule blocks for today
    for cid, name, grp, url, logo in verified_channels:
        epg_lines.append(f'  <programme start="20261112000000 +0000" stop="20261112060000 +0000" channel="{cid}">')
        epg_lines.append(f'    <title lang="ru">{name} • Ночной эфир</title>')
        epg_lines.append(f'    <desc lang="ru">Прямая трансляция канала {name}</desc>')
        epg_lines.append('  </programme>')
        epg_lines.append(f'  <programme start="20261112060000 +0000" stop="20261112120000 +0000" channel="{cid}">')
        epg_lines.append(f'    <title lang="ru">{name} • Утренний блок</title>')
        epg_lines.append(f'    <desc lang="ru">Тематические передачи и новости</desc>')
        epg_lines.append('  </programme>')
        epg_lines.append(f'  <programme start="20261112120000 +0000" stop="20261112180000 +0000" channel="{cid}">')
        epg_lines.append(f'    <title lang="ru">{name} • Дневной эфир</title>')
        epg_lines.append(f'    <desc lang="ru">Главные программы и актуальные события</desc>')
        epg_lines.append('  </programme>')
        epg_lines.append(f'  <programme start="20261112180000 +0000" stop="20261112235959 +0000" channel="{cid}">')
        epg_lines.append(f'    <title lang="ru">{name} • Вечерний прайм</title>')
        epg_lines.append(f'    <desc lang="ru">Вечерние премьеры и специальный эфир</desc>')
        epg_lines.append('  </programme>')

    epg_lines.append('</tv>\n')
    with open("app/src/main/assets/default_epg.xml", "w", encoding="utf-8") as f:
        f.write("\n".join(epg_lines))
    print("Wrote app/src/main/assets/default_epg.xml")

if __name__ == "__main__":
    main()
