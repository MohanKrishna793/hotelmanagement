import csv
from pathlib import Path


ENRICHED_CSV = Path("src/main/resources/hotelsdata_enriched.csv")
OUT_CSV = Path("src/main/resources/hotelsdata.csv")


def main() -> None:
    if not ENRICHED_CSV.exists():
        raise FileNotFoundError(f"Missing {ENRICHED_CSV}")

    with ENRICHED_CSV.open("r", encoding="utf-8-sig", newline="") as f_in:
        reader = csv.reader(f_in)
        rows = list(reader)

    # Header in enriched CSV:
    # 0 State/UT, 1 Hotel Name, 2 City, 3 Hotel Category, 4 Hotel Type,
    # 5 Star Rating, 6 Amenities, 7 Nearby Tourist Places, ...
    out_rows = []
    out_rows.append(["State", "Hotel Name", "Type", "Amenities", "Nearby Tourist Places"])

    for r in rows[1:]:
        if len(r) < 8:
            continue
        state = (r[0] or "").strip()
        hotel_name = (r[1] or "").strip()
        if not state and not hotel_name:
            continue
        if state.lower() == "state" and hotel_name.lower() == "hotel name":
            continue

        hotel_type = (r[4] or "").strip()
        amenities = (r[6] or "").strip()
        nearby_places = (r[7] or "").strip()

        if not state or not hotel_name:
            continue
        out_rows.append([state, hotel_name, hotel_type, amenities, nearby_places])

    OUT_CSV.write_text("", encoding="utf-8-sig")
    with OUT_CSV.open("w", encoding="utf-8-sig", newline="") as f_out:
        writer = csv.writer(f_out, quoting=csv.QUOTE_MINIMAL)
        writer.writerows(out_rows)


if __name__ == "__main__":
    main()

