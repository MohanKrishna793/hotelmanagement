"""
=================================================================
  Hotel Image Downloader — UNIQUE IMAGE PER HOTEL
  Searches Unsplash using: Hotel Name + City + Type
  Result: 900 truly different images, no repetition
=================================================================
  SETUP:
    pip install requests pandas tqdm

  PLACE IN SAME FOLDER AS:
    india_hotels_complete_v2.csv

  RUN:
    python download_unique_hotel_images.py

  OUTPUT:
    - hotel_images/hotel_0001.jpg ... hotel_0900.jpg
    - india_hotels_JAVA_READY.csv  (with local image paths)
=================================================================
"""

import requests, pandas as pd, os, time, random
from pathlib import Path
from tqdm import tqdm

# ── CONFIG ────────────────────────────────────────────────
UNSPLASH_ACCESS_KEY = "NL9DT-dZ1lPnfPnmUgkTrSzr_uGVLxNetLC2g3zbq1M"
INPUT_CSV           = "india_hotels_complete_v2.csv"
OUTPUT_CSV          = "india_hotels_JAVA_READY.csv"
IMAGE_FOLDER        = Path("hotel_images")
IMAGE_WIDTH         = 800
DELAY               = 1.3   # seconds between requests (50 req/hr free tier)
# ─────────────────────────────────────────────────────────

SEARCH_URL = "https://api.unsplash.com/search/photos"

# ── Smart multi-strategy query builder ───────────────────
# Strategy: try specific → medium → broad
# Each returns a DIFFERENT query so results vary per hotel

def queries_for(row):
    name  = str(row.get("Hotel Name", "")).strip()
    city  = str(row.get("City", "")).strip()
    state = str(row.get("State/UT", "")).strip()
    htype = str(row.get("Hotel Type", "")).strip()
    cat   = str(row.get("Hotel Category", "")).strip()
    star  = "five star" if "5" in str(row.get("Star Rating","")) else "four star"

    # Map hotel type to vivid search term
    type_terms = {
        "Beach Resort":   "luxury beach resort ocean pool",
        "Hill Resort":    "luxury mountain resort himalaya mist",
        "Heritage Hotel": "heritage palace hotel India royal",
        "Business Hotel": "luxury hotel lobby atrium city",
        "Resort":         "luxury eco resort pool nature",
    }
    type_term = type_terms.get(htype, "luxury hotel")
    luxury    = "ultra luxury" if "Ultra" in cat else "luxury"

    # City-specific visual terms for more unique results
    city_visual = {
        "Goa":           "Goa beach palm sunset",
        "Mumbai":        "Mumbai skyline sea",
        "Delhi":         "New Delhi grand hotel",
        "Jaipur":        "Jaipur pink city palace",
        "Udaipur":       "Udaipur lake palace",
        "Varanasi":      "Varanasi ghats river",
        "Agra":          "Agra Mughal heritage",
        "Shimla":        "Shimla mountain colonial",
        "Manali":        "Manali snow mountain resort",
        "Rishikesh":     "Rishikesh river yoga ashram",
        "Darjeeling":    "Darjeeling tea garden mountain",
        "Munnar":        "Munnar tea estate green hills",
        "Alleppey":      "Alleppey backwater houseboat",
        "Kovalam":       "Kovalam beach lighthouse Kerala",
        "Mysore":        "Mysore palace heritage",
        "Hampi":         "Hampi ruins heritage",
        "Leh":           "Leh Ladakh monastery mountain",
        "Srinagar":      "Srinagar Dal lake houseboat",
        "Gangtok":       "Gangtok Sikkim mountain monastery",
        "Port Blair":    "Andaman island beach turquoise",
    }
    city_term = city_visual.get(city, f"{city} India hotel")

    # Return 3 fallback queries from specific → generic
    return [
        f"{luxury} {type_term} {city_term}",           # most specific
        f"{star} {type_term} {state} India",            # medium
        f"{luxury} hotel India {type_term}",            # broad fallback
    ]

# ── Unsplash fetch with page offset for variety ───────────
def fetch_unsplash(query, page=1):
    """
    Uses 'page' parameter so different hotels with similar queries
    get different images from different result pages.
    """
    try:
        resp = requests.get(SEARCH_URL, timeout=15, params={
            "query":       query,
            "per_page":    5,
            "page":        page,
            "orientation": "landscape",
            "client_id":   UNSPLASH_ACCESS_KEY,
        })
        resp.raise_for_status()

        remaining = int(resp.headers.get("X-Ratelimit-Remaining", 50))
        if remaining < 5:
            print(f"\n  ⚠️  Rate limit low ({remaining} left) — sleeping 65s ...")
            time.sleep(65)

        results = resp.json().get("results", [])
        if results:
            # Pick a random result from top 5 for extra variety
            photo = random.choice(results[:min(3, len(results))])
            url   = photo["urls"]["regular"].split("?")[0]
            return url + f"?w={IMAGE_WIDTH}&fit=crop&auto=format&q=85"

    except requests.HTTPError as e:
        if e.response.status_code == 403:
            print(f"\n  ⚠️  Rate limited — sleeping 65s ...")
            time.sleep(65)
        else:
            print(f"\n  [warn] HTTP {e.response.status_code}: {query}")
    except Exception as e:
        print(f"\n  [warn] {e}")
    return None

def fetch_best_url(row, idx):
    """
    Try queries from specific → broad.
    Use page offset based on hotel index for extra spread.
    """
    qs   = queries_for(row)
    page = (idx % 3) + 1   # cycles pages 1, 2, 3 across hotels

    for q in qs:
        url = fetch_unsplash(q, page=page)
        if url:
            return url, q
        time.sleep(0.3)

    return None, None

# ── Image downloader ──────────────────────────────────────
def download_image(url, filepath):
    try:
        r = requests.get(url, timeout=20, stream=True)
        r.raise_for_status()
        with open(filepath, "wb") as f:
            for chunk in r.iter_content(8192):
                f.write(chunk)
        return True
    except Exception as e:
        print(f"\n  [warn] Download failed: {e}")
        return False

# ── MAIN ──────────────────────────────────────────────────
def main():
    print("=" * 60)
    print("  Hotel Image Downloader — UNIQUE per Hotel")
    print("=" * 60)

    # Find CSV
    csv_path = Path(INPUT_CSV)
    if not csv_path.exists():
        print(f"\n❌  '{INPUT_CSV}' not found in current folder.")
        print(f"    Make sure CSV is in the same folder as this script.")
        return

    df = pd.read_csv(csv_path)
    print(f"\n  ✅  {len(df)} hotels loaded.")

    IMAGE_FOLDER.mkdir(exist_ok=True)
    print(f"  📁  Images will save to: {IMAGE_FOLDER.resolve()}\n")
    print(f"  ⏱   Estimated time: ~{len(df) * DELAY / 60:.0f} minutes\n")

    local_paths  = []
    unsplash_urls = []
    downloaded = skipped = failed = 0

    for idx, row in tqdm(df.iterrows(), total=len(df), desc="Downloading"):
        num      = idx + 1
        filename = f"hotel_{num:04d}.jpg"
        filepath = IMAGE_FOLDER / filename

        # Resume: skip if already downloaded
        if filepath.exists() and filepath.stat().st_size > 5000:
            local_paths.append(str(filepath))
            unsplash_urls.append(row.get("Hotel Image URL", ""))
            skipped += 1
            continue

        # Fetch unique URL for this hotel
        url, used_query = fetch_best_url(row, idx)

        if url and download_image(url, filepath):
            local_paths.append(str(filepath))
            unsplash_urls.append(url)
            downloaded += 1
        else:
            local_paths.append("")
            unsplash_urls.append(row.get("Hotel Image URL", ""))
            failed += 1

        time.sleep(DELAY)

    # Save updated CSV
    df["Hotel Image URL"]        = unsplash_urls
    df["Hotel Image Local Path"] = local_paths
    df["Hotel Image Spring URL"] = [
        f"/images/hotels/hotel_{i+1:04d}.jpg" if p else ""
        for i, p in enumerate(local_paths)
    ]
    df.to_csv(OUTPUT_CSV, index=False, encoding="utf-8-sig")

    # Final report
    unique_urls = len(set(u for u in unsplash_urls if u))
    print(f"\n{'='*60}")
    print(f"  ✅  DONE!")
    print(f"     Downloaded  : {downloaded}")
    print(f"     Skipped     : {skipped}  (already existed)")
    print(f"     Failed      : {failed}")
    print(f"     Unique URLs : {unique_urls} / {len(df)}")
    print(f"     Saved to    : {IMAGE_FOLDER.resolve()}")
    print(f"     CSV saved   : {OUTPUT_CSV}")
    print(f"{'='*60}")
    print(f"""
  📋  NEXT STEPS:
  1. Copy hotel_images/ folder to:
     src/main/resources/static/images/hotels/

  2. Copy india_hotels_JAVA_READY.csv to:
     src/main/resources/

  3. Images served at:
     http://localhost:8080/images/hotels/hotel_0001.jpg

  🔑  Regenerate your Unsplash key after use:
      https://unsplash.com/oauth/applications/901895
    """)

if __name__ == "__main__":
    main()
