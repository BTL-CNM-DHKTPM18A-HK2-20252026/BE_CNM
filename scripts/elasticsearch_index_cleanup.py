"""
Script dọn dẹp index Elasticsearch (chạy với Elasticsearch trên Docker).

Chức năng chính:
1) Liệt kê tất cả index.
2) Hiển thị ngày tạo index và số ngày tồn tại.
3) Xóa index "không còn sử dụng" theo rule an toàn:
   - Không phải system index (không bắt đầu bằng dấu ".")
   - Không nằm trong danh sách keep patterns
   - Có tuổi >= --min-age-days
   - Và (docs_count == 0 hoặc status == close)

4) Có chế độ menu tương tác để xóa nhanh:
     - Xóa tất cả index không protected
     - Xóa theo ngày tạo (YYYY-MM-DD)
     - Xóa theo tháng tạo (YYYY-MM)
     - Chọn tay nhiều index để xóa

5) Có tùy chọn dọn dữ liệu MongoDB cũ để giữ lại dữ liệu mới.

Mặc định script chạy ở chế độ preview (dry-run), KHÔNG xóa thật.

Ví dụ:
  python elasticsearch_index_cleanup.py
    python elasticsearch_index_cleanup.py --menu
  python elasticsearch_index_cleanup.py --url http://localhost:9200 --keep users,messages,documents
  python elasticsearch_index_cleanup.py --execute --yes --min-age-days 14 --keep users,messages,documents
"""

from __future__ import annotations

import argparse
import fnmatch
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from typing import List

from elasticsearch import Elasticsearch
from pymongo import MongoClient


@dataclass
class IndexInfo:
    """Thông tin đã chuẩn hóa của một index."""

    name: str
    health: str
    status: str
    docs_count: int
    store_size: str
    creation_date: datetime | None
    age_days: int | None


def parse_args() -> argparse.Namespace:
    """Đọc tham số dòng lệnh để script linh hoạt khi chạy local/Docker."""
    parser = argparse.ArgumentParser(
        description="Liệt kê và dọn dẹp index Elasticsearch không còn sử dụng"
    )
    parser.add_argument(
        "--url",
        default="http://localhost:9200",
        help="URL Elasticsearch (Docker thường map ra localhost:9200)",
    )
    parser.add_argument("--username", default="", help="Username (nếu có bảo mật)")
    parser.add_argument("--password", default="", help="Password (nếu có bảo mật)")
    parser.add_argument(
        "--timeout",
        type=int,
        default=30,
        help="Request timeout (giây)",
    )
    parser.add_argument(
        "--min-age-days",
        type=int,
        default=30,
        help="Chỉ coi là cũ khi index có tuổi >= số ngày này",
    )
    parser.add_argument(
        "--keep",
        default="users,messages,documents",
        help=(
            "Danh sách pattern index không được xóa, cách nhau bởi dấu phẩy. "
            "Hỗ trợ wildcard (*), ví dụ: users,messages,documents,.kibana*"
        ),
    )
    parser.add_argument(
        "--execute",
        action="store_true",
        help="Bật xóa thật. Nếu không có cờ này, script chỉ preview.",
    )
    parser.add_argument(
        "--yes",
        action="store_true",
        help="Bỏ qua bước confirm khi dùng --execute",
    )
    parser.add_argument(
        "--menu",
        action="store_true",
        help="Mở menu tương tác để chọn kiểu xóa index/MongoDB",
    )
    parser.add_argument(
        "--mongo-uri",
        default="mongodb://localhost:27017",
        help="MongoDB URI cho chức năng dọn dữ liệu cũ",
    )
    parser.add_argument(
        "--mongo-db",
        default="fruvia_db",
        help="Tên database MongoDB cho chức năng dọn dữ liệu cũ",
    )
    parser.add_argument(
        "--mongo-time-field",
        default="createdAt",
        help="Tên field thời gian mặc định khi dọn MongoDB",
    )
    return parser.parse_args()


def connect_client(args: argparse.Namespace) -> Elasticsearch:
    """Kết nối Elasticsearch và kiểm tra health cơ bản bằng ping."""
    basic_auth = None
    if args.username:
        basic_auth = (args.username, args.password)

    client = Elasticsearch(
        hosts=[args.url],
        basic_auth=basic_auth,
        request_timeout=args.timeout,
    )

    try:
        if client.ping():
            return client

        # Một số trường hợp ping() trả False nhưng API khác có thể ném lỗi chi tiết hơn,
        # ví dụ lệch major version giữa elasticsearch-py và Elasticsearch server.
        client.info()
        return client
    except Exception as ex:  # noqa: BLE001
        error_text = str(ex)
        if "compatible-with=9" in error_text and "Accept version must be either version 8 or 7" in error_text:
            raise RuntimeError(
                "Phát hiện lệch phiên bản: elasticsearch-py 9.x không tương thích Elasticsearch 8.x. "
                "Hãy cài lại client 8.x: pip install \"elasticsearch>=8.14,<9\" --upgrade"
            ) from ex

        raise RuntimeError(
            "Không kết nối được Elasticsearch. Kiểm tra URL/port, trạng thái container, "
            "và thông tin auth (username/password)."
        ) from ex


def safe_int(value: str | int | None) -> int:
    """Chuyển giá trị bất kỳ về int an toàn, lỗi thì trả 0."""
    try:
        return int(value) if value is not None else 0
    except (TypeError, ValueError):
        return 0


def to_datetime_utc(epoch_ms: str | int | None) -> datetime | None:
    """Đổi epoch milliseconds sang datetime UTC."""
    ms = safe_int(epoch_ms)
    if ms <= 0:
        return None
    return datetime.fromtimestamp(ms / 1000, tz=timezone.utc)


def load_indices(client: Elasticsearch) -> List[IndexInfo]:
    """
    Bước 1 + 2:
    - Lấy toàn bộ index qua cat API.
    - Lấy settings để đọc ngày tạo (index.creation_date).
    - Chuẩn hóa thành danh sách IndexInfo để xử lý tiếp.
    """
    rows = client.cat.indices(
        index="*",
        format="json",
        bytes="mb",
        expand_wildcards="all",
    )

    settings_map = client.indices.get_settings(index="*", flat_settings=True)
    now = datetime.now(timezone.utc)

    infos: List[IndexInfo] = []
    for row in rows:
        name = row.get("index", "")
        if not name:
            continue

        setting = settings_map.get(name, {})
        creation_ms = (
            setting.get("settings", {}).get("index.creation_date")
            if isinstance(setting, dict)
            else None
        )
        created_at = to_datetime_utc(creation_ms)
        age_days = (now - created_at).days if created_at else None

        infos.append(
            IndexInfo(
                name=name,
                health=str(row.get("health", "")),
                status=str(row.get("status", "")),
                docs_count=safe_int(row.get("docs.count")),
                store_size=str(row.get("store.size", "")),
                creation_date=created_at,
                age_days=age_days,
            )
        )

    infos.sort(key=lambda item: item.name)
    return infos


def print_indices_table(infos: List[IndexInfo]) -> None:
    """In danh sách index dạng bảng text để dễ đọc trong terminal."""
    print("\n=== DANH SACH TAT CA INDEX ===")
    if not infos:
        print("(Khong co index nao)")
        return

    header = (
        f"{'INDEX':36} {'HEALTH':8} {'STATUS':8} {'DOCS':10} "
        f"{'SIZE(MB)':10} {'CREATED_AT(UTC)':24} {'AGE_DAYS':8}"
    )
    print(header)
    print("-" * len(header))

    for item in infos:
        created = item.creation_date.isoformat() if item.creation_date else "N/A"
        age = str(item.age_days) if item.age_days is not None else "N/A"
        print(
            f"{item.name[:36]:36} "
            f"{item.health[:8]:8} "
            f"{item.status[:8]:8} "
            f"{item.docs_count:10d} "
            f"{item.store_size[:10]:10} "
            f"{created[:24]:24} "
            f"{age:8}"
        )


def split_keep_patterns(raw_keep: str) -> List[str]:
    """Tách chuỗi keep patterns (ngăn cách dấu phẩy) thành list sạch."""
    return [token.strip() for token in raw_keep.split(",") if token.strip()]


def is_protected_index(index_name: str, keep_patterns: List[str]) -> bool:
    """
    Index được bảo vệ nếu:
    - Là system index (bat dau bang '.')
    - Khớp bất kỳ pattern nào trong --keep
    """
    if index_name.startswith("."):
        return True
    return any(fnmatch.fnmatch(index_name, pattern) for pattern in keep_patterns)


def non_protected_indices(infos: List[IndexInfo], keep_patterns: List[str]) -> List[IndexInfo]:
    """Lọc ra các index có thể cân nhắc xóa (không phải protected)."""
    return [item for item in infos if not is_protected_index(item.name, keep_patterns)]


def find_indices_by_day(
    infos: List[IndexInfo],
    keep_patterns: List[str],
    target_day: date,
) -> List[tuple[IndexInfo, str]]:
    """Tìm index có ngày tạo đúng bằng YYYY-MM-DD."""
    candidates: List[tuple[IndexInfo, str]] = []
    for item in non_protected_indices(infos, keep_patterns):
        if item.creation_date is None:
            continue
        if item.creation_date.date() == target_day:
            candidates.append((item, f"created_on = {target_day.isoformat()}"))
    return candidates


def find_indices_by_month(
    infos: List[IndexInfo],
    keep_patterns: List[str],
    year: int,
    month: int,
) -> List[tuple[IndexInfo, str]]:
    """Tìm index có ngày tạo thuộc một tháng cụ thể (YYYY-MM)."""
    candidates: List[tuple[IndexInfo, str]] = []
    for item in non_protected_indices(infos, keep_patterns):
        if item.creation_date is None:
            continue
        if item.creation_date.year == year and item.creation_date.month == month:
            candidates.append((item, f"created_in = {year:04d}-{month:02d}"))
    return candidates


def find_all_non_protected(infos: List[IndexInfo], keep_patterns: List[str]) -> List[tuple[IndexInfo, str]]:
    """Lấy toàn bộ index không protected (dùng khi user muốn xóa hàng loạt)."""
    return [(item, "delete_all_non_protected") for item in non_protected_indices(infos, keep_patterns)]


def find_unused_indices(
    infos: List[IndexInfo],
    keep_patterns: List[str],
    min_age_days: int,
) -> List[tuple[IndexInfo, str]]:
    """
    Rule xác định index "không còn sử dụng" (mức an toàn):
    - Không protected
    - Tuổi >= min_age_days
    - Và (docs_count == 0 hoặc status == close)
    """
    candidates: List[tuple[IndexInfo, str]] = []

    for item in infos:
        if is_protected_index(item.name, keep_patterns):
            continue

        if item.age_days is None or item.age_days < min_age_days:
            continue

        status_lower = item.status.lower()
        if item.docs_count == 0:
            candidates.append((item, "docs_count = 0"))
            continue

        if status_lower == "close":
            candidates.append((item, "status = close"))

    return candidates


def print_candidates(candidates: List[tuple[IndexInfo, str]]) -> None:
    """In danh sách index dự kiến xóa và lý do."""
    print("\n=== INDEX DU KIEN XOA (UNUSED) ===")
    if not candidates:
        print("Khong tim thay index nao can xoa theo rule hien tai.")
        return

    for item, reason in candidates:
        created = item.creation_date.isoformat() if item.creation_date else "N/A"
        print(
            f"- {item.name} | reason: {reason} | docs: {item.docs_count} | "
            f"status: {item.status} | age_days: {item.age_days} | created: {created}"
        )


def ask_yes_no(prompt: str, default_no: bool = True) -> bool:
    """Hỏi xác nhận yes/no từ người dùng."""
    default_hint = "[y/N]" if default_no else "[Y/n]"
    answer = input(f"{prompt} {default_hint}: ").strip().lower()
    if not answer:
        return not default_no
    return answer == "y"


def parse_month_input(raw_month: str) -> tuple[int, int] | None:
    """Parse chuỗi YYYY-MM, trả về (year, month) hoặc None."""
    try:
        year_str, month_str = raw_month.split("-")
        year = int(year_str)
        month = int(month_str)
        if month < 1 or month > 12:
            return None
        return year, month
    except (ValueError, AttributeError):
        return None


def parse_int_list(raw: str) -> List[int]:
    """Parse chuỗi '1,2,5' thành list số nguyên duy nhất."""
    values: List[int] = []
    seen = set()
    for token in raw.split(","):
        token = token.strip()
        if not token:
            continue
        if not token.isdigit():
            continue
        value = int(token)
        if value in seen:
            continue
        seen.add(value)
        values.append(value)
    return values


def print_numbered_indices(items: List[IndexInfo]) -> None:
    """In danh sách index có đánh số để user chọn tay."""
    if not items:
        print("Khong co index nao hop le de chon.")
        return

    print("\nDanh sach index co the xoa:")
    for idx, item in enumerate(items, start=1):
        created = item.creation_date.isoformat() if item.creation_date else "N/A"
        print(
            f"{idx:>3}. {item.name} | docs={item.docs_count} | status={item.status} | "
            f"created={created}"
        )


def delete_indices(
    client: Elasticsearch,
    candidates: List[tuple[IndexInfo, str]],
    execute: bool,
    assume_yes: bool,
) -> None:
    """
    Bước 3: Xóa index.
    - Nếu chưa có --execute: chỉ preview.
    - Nếu có --execute: hỏi confirm (trừ khi có --yes).
    """
    if not candidates:
        return

    if not execute:
        print("\n[DRY-RUN] Chua xoa gi ca. Dung --execute de xoa that.")
        return

    if not assume_yes:
        answer = input("\nBan co chac chan muon xoa cac index tren? [y/N]: ").strip().lower()
        if answer != "y":
            print("Da huy xoa index.")
            return

    print("\nDang xoa index...")
    deleted = 0
    failed = 0

    for item, reason in candidates:
        try:
            client.indices.delete(index=item.name)
            deleted += 1
            print(f"[OK] Deleted: {item.name} ({reason})")
        except Exception as ex:  # noqa: BLE001
            failed += 1
            print(f"[ERR] Delete failed: {item.name} -> {ex}")

    print(f"\nHoan tat. Deleted={deleted}, Failed={failed}")


def run_mongodb_cleanup_menu(args: argparse.Namespace) -> None:
    """Menu dọn dữ liệu MongoDB cũ để giữ lại dữ liệu mới."""
    print("\n=== MONGODB CLEANUP ===")
    try:
        mongo_client = MongoClient(args.mongo_uri, serverSelectionTimeoutMS=5000)
        mongo_client.admin.command("ping")
    except Exception as ex:  # noqa: BLE001
        print(f"[ERR] Khong ket noi duoc MongoDB: {ex}")
        return

    db = mongo_client[args.mongo_db]
    collections = sorted(db.list_collection_names())
    if not collections:
        print("Khong co collection nao trong database.")
        return

    print(f"Database: {args.mongo_db}")
    print("Chon collection:")
    for idx, name in enumerate(collections, start=1):
        print(f"{idx:>3}. {name}")

    raw_pick = input("Nhap so thu tu hoac ten collection: ").strip()
    selected_collection = ""
    if raw_pick.isdigit():
        pos = int(raw_pick)
        if 1 <= pos <= len(collections):
            selected_collection = collections[pos - 1]
    elif raw_pick in collections:
        selected_collection = raw_pick

    if not selected_collection:
        print("Lua chon collection khong hop le.")
        return

    time_field = input(
        f"Nhap ten field thoi gian [{args.mongo_time_field}]: "
    ).strip() or args.mongo_time_field

    print("\nChon kieu xoa MongoDB:")
    print("1. Xoa du lieu cu, giu lai N ngay gan nhat")
    print("2. Xoa du lieu cu truoc ngay cu the (YYYY-MM-DD)")
    mode = input("Lua chon (1/2): ").strip()

    filter_query = {}
    reason = ""
    if mode == "1":
        raw_days = input("Nhap so ngay muon giu lai (vd 30): ").strip()
        if not raw_days.isdigit() or int(raw_days) <= 0:
            print("So ngay khong hop le.")
            return
        keep_days = int(raw_days)
        cutoff = datetime.utcnow() - timedelta(days=keep_days)
        filter_query = {time_field: {"$lt": cutoff}}
        reason = f"{time_field} < now - {keep_days} days"
    elif mode == "2":
        raw_date = input("Nhap ngay (YYYY-MM-DD): ").strip()
        try:
            cutoff = datetime.strptime(raw_date, "%Y-%m-%d")
        except ValueError:
            print("Ngay khong dung dinh dang YYYY-MM-DD.")
            return
        filter_query = {time_field: {"$lt": cutoff}}
        reason = f"{time_field} < {raw_date}"
    else:
        print("Lua chon khong hop le.")
        return

    collection = db[selected_collection]
    try:
        to_delete = collection.count_documents(filter_query)
    except Exception as ex:  # noqa: BLE001
        print(f"[ERR] Khong dem duoc document: {ex}")
        return

    print(
        f"\nCollection: {selected_collection} | Rule: {reason} | "
        f"Du kien xoa: {to_delete} document"
    )
    if to_delete == 0:
        print("Khong co du lieu can xoa theo rule hien tai.")
        return

    if not ask_yes_no("Ban co chac chan muon xoa du lieu MongoDB nay?", default_no=True):
        print("Da huy xoa MongoDB.")
        return

    try:
        result = collection.delete_many(filter_query)
        print(f"[OK] Da xoa {result.deleted_count} document khoi {selected_collection}.")
    except Exception as ex:  # noqa: BLE001
        print(f"[ERR] Xoa MongoDB that bai: {ex}")


def run_interactive_menu(
    client: Elasticsearch,
    args: argparse.Namespace,
    keep_patterns: List[str],
) -> None:
    """Menu tương tác để user chọn kiểu xóa nhanh."""
    while True:
        infos = load_indices(client)

        print("\n================ MENU CLEANUP ================")
        print("1. Xem danh sach tat ca index")
        print("2. Xoa tat ca index khong protected")
        print("3. Xoa index theo ngay tao (YYYY-MM-DD)")
        print("4. Xoa index theo thang tao (YYYY-MM)")
        print("5. Chon tay index de xoa")
        print("6. Xoa index theo rule an toan mac dinh (docs=0/close + min-age-days)")
        print("7. Doi keep patterns hien tai")
        print("8. Doi min-age-days hien tai")
        print("9. Dọn MongoDB du lieu cu (giu du lieu moi)")
        print("10. [NUCLEAR] Xoa TUYET DOI TAT CA index (ke ca system, ke ca protected)")
        print("0. Thoat")
        print("=============================================\n")

        choice = input("Nhap lua chon: ").strip()

        if choice == "0":
            print("Thoat menu.")
            break

        if choice == "1":
            print_indices_table(infos)
            continue

        if choice == "2":
            candidates = find_all_non_protected(infos, keep_patterns)
            print_candidates(candidates)
            delete_indices(client, candidates, execute=True, assume_yes=False)
            continue

        if choice == "3":
            raw_date = input("Nhap ngay tao can xoa (YYYY-MM-DD): ").strip()
            try:
                target_day = datetime.strptime(raw_date, "%Y-%m-%d").date()
            except ValueError:
                print("Ngay khong dung dinh dang YYYY-MM-DD.")
                continue

            candidates = find_indices_by_day(infos, keep_patterns, target_day)
            print_candidates(candidates)
            delete_indices(client, candidates, execute=True, assume_yes=False)
            continue

        if choice == "4":
            raw_month = input("Nhap thang can xoa (YYYY-MM): ").strip()
            parsed_month = parse_month_input(raw_month)
            if parsed_month is None:
                print("Thang khong dung dinh dang YYYY-MM.")
                continue

            year, month = parsed_month
            candidates = find_indices_by_month(infos, keep_patterns, year, month)
            print_candidates(candidates)
            delete_indices(client, candidates, execute=True, assume_yes=False)
            continue

        if choice == "5":
            selectable = non_protected_indices(infos, keep_patterns)
            if not selectable:
                print("Khong co index nao de chon.")
                continue

            print_numbered_indices(selectable)
            raw_pick = input("Nhap so thu tu can xoa, cach nhau bang dau phay (vd 1,3,4): ").strip()
            picked = parse_int_list(raw_pick)
            picked_valid = [p for p in picked if 1 <= p <= len(selectable)]
            if not picked_valid:
                print("Khong co lua chon hop le.")
                continue

            picked_candidates = [
                (selectable[pos - 1], "manual_selection")
                for pos in picked_valid
            ]
            print_candidates(picked_candidates)
            delete_indices(client, picked_candidates, execute=True, assume_yes=False)
            continue

        if choice == "6":
            candidates = find_unused_indices(
                infos=infos,
                keep_patterns=keep_patterns,
                min_age_days=args.min_age_days,
            )
            print_candidates(candidates)
            delete_indices(client, candidates, execute=True, assume_yes=False)
            continue

        if choice == "7":
            new_keep = input("Nhap keep patterns moi (vd users,messages,documents,.kibana*): ").strip()
            if not new_keep:
                print("Gia tri keep patterns khong duoc rong.")
                continue
            keep_patterns[:] = split_keep_patterns(new_keep)
            print(f"Da cap nhat keep patterns: {', '.join(keep_patterns)}")
            continue

        if choice == "8":
            new_min_age = input("Nhap min-age-days moi: ").strip()
            if not new_min_age.isdigit():
                print("Gia tri min-age-days phai la so nguyen duong.")
                continue
            args.min_age_days = int(new_min_age)
            print(f"Da cap nhat min-age-days = {args.min_age_days}")
            continue

        if choice == "9":
            run_mongodb_cleanup_menu(args)
            continue

        if choice == "10":
            infos_all = infos  # load_indices đã lấy hết rồi
            if not infos_all:
                print("Khong co index nao trong Elasticsearch.")
                continue

            print("\n[!!! CANH BAO !!!] Ban sap XOA TUYET DOI TAT CA index, bao gom:")
            for info in infos_all:
                print(f"  - {info.name} (docs={info.docs_count}, health={info.health})")
            print(f"\nTong cong: {len(infos_all)} index se bi xoa vinh vien.")

            confirm1 = input("\nNhap 'XOA HET' (chu hoa) de xac nhan lan 1: ").strip()
            if confirm1 != "XOA HET":
                print("Da huy. Khong xoa gi ca.")
                continue

            confirm2 = input("Nhap 'YES' de xac nhan lan 2 (khong the hoan tac): ").strip()
            if confirm2 != "YES":
                print("Da huy. Khong xoa gi ca.")
                continue

            print("\nDang xoa TUYET DOI TAT CA index...")
            deleted = 0
            failed = 0
            for info in infos_all:
                try:
                    client.indices.delete(index=info.name)
                    deleted += 1
                    print(f"[OK] Deleted: {info.name}")
                except Exception as ex:  # noqa: BLE001
                    failed += 1
                    print(f"[ERR] Delete failed: {info.name} -> {ex}")
            print(f"\nHoan thanh: {deleted} xoa thanh cong, {failed} loi.")
            continue

        print("Lua chon khong hop le. Vui long thu lai.")


def main() -> None:
    """Luồng chính của script."""
    args = parse_args()
    keep_patterns = split_keep_patterns(args.keep)

    print("Dang ket noi Elasticsearch...")
    client = connect_client(args)

    if args.menu:
        run_interactive_menu(client=client, args=args, keep_patterns=keep_patterns)
        return

    # Bước 1 + 2: Liệt kê index + ngày tạo
    infos = load_indices(client)
    print_indices_table(infos)

    # Bước 3: Tìm và xóa index không còn sử dụng
    candidates = find_unused_indices(
        infos=infos,
        keep_patterns=keep_patterns,
        min_age_days=args.min_age_days,
    )
    print_candidates(candidates)
    delete_indices(
        client=client,
        candidates=candidates,
        execute=args.execute,
        assume_yes=args.yes,
    )


if __name__ == "__main__":
    main()
