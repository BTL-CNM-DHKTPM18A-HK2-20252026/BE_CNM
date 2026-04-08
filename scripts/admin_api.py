"""
Fruvia Chat - Admin Dashboard API Server
Serves real MongoDB data for the CNM_Dashboard admin panel.
Uses only Python stdlib + pymongo (no Flask needed).

Usage:
    python admin_api.py [--port 5000] [--host localhost] [--db fruvia_db]
"""

import argparse
import json
import math
import re
from datetime import datetime, timedelta
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

from pymongo import MongoClient

# ── MongoDB Connection ──────────────────────────────────────────────
MONGO_HOST = "localhost"
MONGO_PORT = 27017
MONGO_DB = "fruvia_db"

client = None
db = None


def get_db():
    global client, db
    if client is None:
        client = MongoClient(MONGO_HOST, MONGO_PORT)
        db = client[MONGO_DB]
    return db


# ── JSON Serializer ─────────────────────────────────────────────────
class MongoEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, datetime):
            return obj.isoformat()
        return super().default(obj)


def json_response(data):
    return json.dumps(data, cls=MongoEncoder, ensure_ascii=False)


# ── API Handlers ────────────────────────────────────────────────────

def handle_stats():
    """GET /api/admin/stats - Dashboard overview statistics."""
    db = get_db()

    total_users = db.user_auth.count_documents({"isDeleted": {"$ne": True}})
    total_groups = db.conversations.count_documents({
        "conversationType": "GROUP", "isDeleted": {"$ne": True}
    })
    total_private = db.conversations.count_documents({
        "conversationType": "PRIVATE", "isDeleted": {"$ne": True}
    })
    total_messages = db.messages.count_documents({"isDeleted": {"$ne": True}})
    total_friendships = db.friendships.count_documents({"status": "ACCEPTED"})

    # User status breakdown
    active_users = db.user_auth.count_documents({"accountStatus": "ACTIVE", "isDeleted": {"$ne": True}})
    locked_users = db.user_auth.count_documents({"accountStatus": "LOCKED", "isDeleted": {"$ne": True}})
    banned_users = db.user_auth.count_documents({"accountStatus": "BANNED", "isDeleted": {"$ne": True}})

    # User growth by month (last 7 months)
    now = datetime.now()
    user_growth = []
    for i in range(6, -1, -1):
        month_start = (now.replace(day=1) - timedelta(days=30 * i)).replace(day=1, hour=0, minute=0, second=0)
        if i > 0:
            month_end = (now.replace(day=1) - timedelta(days=30 * (i - 1))).replace(day=1, hour=0, minute=0, second=0)
        else:
            month_end = now
        count = db.user_auth.count_documents({
            "createdAt": {"$gte": month_start, "$lt": month_end}
        })
        month_label = f"T{month_start.month}"
        user_growth.append({"month": month_label, "users": count})

    # Messages by day of week (last 7 days)
    message_stats = []
    day_names_vi = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"]
    for i in range(6, -1, -1):
        day_start = (now - timedelta(days=i)).replace(hour=0, minute=0, second=0, microsecond=0)
        day_end = day_start + timedelta(days=1)
        private_count = db.messages.count_documents({
            "createdAt": {"$gte": day_start, "$lt": day_end},
            "isDeleted": {"$ne": True},
            "conversationId": {"$in": [
                c["_id"] for c in db.conversations.find(
                    {"conversationType": "PRIVATE"}, {"_id": 1}
                ).limit(50000)
            ]}
        })
        group_count = db.messages.count_documents({
            "createdAt": {"$gte": day_start, "$lt": day_end},
            "isDeleted": {"$ne": True},
            "conversationId": {"$in": [
                c["_id"] for c in db.conversations.find(
                    {"conversationType": "GROUP"}, {"_id": 1}
                ).limit(50000)
            ]}
        })
        weekday = day_start.weekday()
        day_label = day_names_vi[(weekday + 1) % 7]
        message_stats.append({
            "day": day_label,
            "messages": private_count,
            "groups": group_count
        })

    # User status distribution for pie chart
    user_status = [
        {"name": "Active", "value": active_users, "color": "#10B981"},
        {"name": "Locked", "value": locked_users, "color": "#F59E0B"},
        {"name": "Banned", "value": banned_users, "color": "#EF4444"},
    ]

    # Recent activity (last 10 users created + last messages)
    recent_users = list(db.user_detail.find(
        {}, {"_id": 1, "displayName": 1, "createdAt": 1}
    ).sort("createdAt", -1).limit(6))

    recent_activities = []
    for u in recent_users:
        recent_activities.append({
            "user": u.get("displayName", "Unknown"),
            "action": "joined",
            "target": "Fruvia Chat",
            "time": u.get("createdAt"),
            "type": "join"
        })

    return {
        "success": True,
        "data": {
            "totalUsers": total_users,
            "activeUsers": active_users,
            "lockedUsers": locked_users,
            "bannedUsers": banned_users,
            "totalGroups": total_groups,
            "totalPrivate": total_private,
            "totalMessages": total_messages,
            "totalFriendships": total_friendships,
            "userGrowth": user_growth,
            "messageStats": message_stats,
            "userStatus": user_status,
            "recentActivities": recent_activities,
        }
    }


def handle_users(params):
    """GET /api/admin/users - Paginated user list."""
    db = get_db()

    page = int(params.get("page", ["0"])[0])
    size = int(params.get("size", ["20"])[0])
    search = params.get("search", [""])[0]
    status = params.get("status", ["all"])[0]  # all, ACTIVE, LOCKED, BANNED

    # Build query
    query = {"isDeleted": {"$ne": True}}
    if status != "all":
        query["accountStatus"] = status.upper()

    if search:
        # Search in user_auth (phone, email) and user_detail (displayName)
        search_regex = {"$regex": re.escape(search), "$options": "i"}
        auth_ids_by_phone = [u["_id"] for u in db.user_auth.find(
            {"$or": [{"phoneNumber": search_regex}, {"email": search_regex}]},
            {"_id": 1}
        )]
        detail_ids_by_name = [u["_id"] for u in db.user_detail.find(
            {"displayName": search_regex}, {"_id": 1}
        )]
        all_matching_ids = list(set(auth_ids_by_phone + detail_ids_by_name))
        if all_matching_ids:
            query["_id"] = {"$in": all_matching_ids}
        else:
            return {"success": True, "data": {"users": [], "total": 0, "page": page, "size": size, "totalPages": 0}}

    total = db.user_auth.count_documents(query)
    total_pages = math.ceil(total / size) if size > 0 else 0

    user_auths = list(db.user_auth.find(query).sort("createdAt", -1).skip(page * size).limit(size))

    user_ids = [u["_id"] for u in user_auths]
    details_map = {d["_id"]: d for d in db.user_detail.find({"_id": {"$in": user_ids}})}

    users = []
    for auth in user_auths:
        uid = auth["_id"]
        detail = details_map.get(uid, {})
        users.append({
            "id": uid,
            "displayName": detail.get("displayName", "Unknown"),
            "firstName": detail.get("firstName", ""),
            "lastName": detail.get("lastName", ""),
            "email": auth.get("email", ""),
            "phone": auth.get("phoneNumber", ""),
            "avatar": detail.get("avatarUrl", ""),
            "coverPhoto": detail.get("coverPhotoUrl", ""),
            "bio": detail.get("bio", ""),
            "gender": detail.get("gender", ""),
            "city": detail.get("city", ""),
            "education": detail.get("education", ""),
            "accountStatus": auth.get("accountStatus", "ACTIVE"),
            "createdAt": auth.get("createdAt"),
            "lastLoginAt": auth.get("lastLoginAt"),
            "isTwoFactorEnabled": auth.get("isTwoFactorEnabled", False),
        })

    return {
        "success": True,
        "data": {
            "users": users,
            "total": total,
            "page": page,
            "size": size,
            "totalPages": total_pages,
        }
    }


def handle_groups(params):
    """GET /api/admin/groups - Paginated group conversation list."""
    db = get_db()

    page = int(params.get("page", ["0"])[0])
    size = int(params.get("size", ["20"])[0])
    search = params.get("search", [""])[0]

    query = {"conversationType": "GROUP", "isDeleted": {"$ne": True}}

    if search:
        query["conversationName"] = {"$regex": re.escape(search), "$options": "i"}

    total = db.conversations.count_documents(query)
    total_pages = math.ceil(total / size) if size > 0 else 0

    groups_raw = list(db.conversations.find(query).sort("lastMessageTime", -1).skip(page * size).limit(size))

    groups = []
    for g in groups_raw:
        conv_id = g["_id"]
        member_count = db.conversation_member.count_documents({"conversationId": conv_id})
        message_count = db.messages.count_documents({"conversationId": conv_id, "isDeleted": {"$ne": True}})

        # Today's messages
        today_start = datetime.now().replace(hour=0, minute=0, second=0, microsecond=0)
        today_messages = db.messages.count_documents({
            "conversationId": conv_id,
            "createdAt": {"$gte": today_start},
            "isDeleted": {"$ne": True}
        })

        groups.append({
            "id": conv_id,
            "name": g.get("conversationName", "Unnamed Group"),
            "avatar": g.get("avatarUrl", ""),
            "memberCount": member_count,
            "messageCount": message_count,
            "todayMessages": today_messages,
            "creatorId": g.get("creatorId", ""),
            "createdAt": g.get("createdAt"),
            "lastMessageTime": g.get("lastMessageTime"),
            "lastMessageContent": g.get("lastMessageContent", ""),
            "description": g.get("groupDescription", ""),
        })

    # Summary stats
    all_groups_count = db.conversations.count_documents({
        "conversationType": "GROUP", "isDeleted": {"$ne": True}
    })

    pipeline = [
        {"$match": {"conversationType": "GROUP", "isDeleted": {"$ne": True}}},
        {"$lookup": {
            "from": "conversation_member",
            "localField": "_id",
            "foreignField": "conversationId",
            "as": "members"
        }},
        {"$project": {"memberCount": {"$size": "$members"}}},
        {"$group": {"_id": None, "totalMembers": {"$sum": "$memberCount"}}}
    ]
    agg_result = list(db.conversations.aggregate(pipeline))
    total_members = agg_result[0]["totalMembers"] if agg_result else 0

    return {
        "success": True,
        "data": {
            "groups": groups,
            "total": total,
            "page": page,
            "size": size,
            "totalPages": total_pages,
            "summary": {
                "totalGroups": all_groups_count,
                "totalMembers": total_members,
            }
        }
    }


def handle_seed_stats():
    """GET /api/seed/stats - Collection counts (reuse from seed_api)."""
    db = get_db()
    stats = {
        "users": db.user_auth.count_documents({}),
        "friendships": db.friendships.count_documents({}),
        "conversations": db.conversations.count_documents({}),
        "messages": db.messages.count_documents({}),
        "conv_members": db.conversation_member.count_documents({}),
        "user_details": db.user_detail.count_documents({}),
        "user_settings": db.user_setting.count_documents({}),
    }
    return {"success": True, "data": stats}


def handle_seed_run(body):
    """POST /api/seed/run - Run seeder in background."""
    import threading
    from seed_fake_data import seed

    global seed_status
    if seed_status["running"]:
        return {"success": False, "error": "Already running"}

    num_users = min(int(body.get("users", 10000)), 50000)
    num_private = min(int(body.get("private", 5000)), 30000)
    num_groups = min(int(body.get("groups", 200)), 2000)
    msgs_private = min(int(body.get("msgsPrivate", 15)), 100)
    msgs_group = min(int(body.get("msgsGroup", 50)), 200)

    seed_status = {"running": True, "progress": "Starting...", "step": "init", "percent": 0, "result": None, "error": None}

    def run():
        global seed_status
        try:
            result = seed(
                host=MONGO_HOST, port=MONGO_PORT, db_name=MONGO_DB,
                num_users=num_users, num_private=num_private, num_groups=num_groups,
                msgs_per_private=msgs_private, msgs_per_group=msgs_group,
            )
            seed_status.update({"result": result, "progress": "Complete!", "percent": 100, "step": "done"})
        except Exception as e:
            seed_status.update({"error": str(e), "progress": f"Error: {e}", "step": "error"})
        finally:
            seed_status["running"] = False

    threading.Thread(target=run, daemon=True).start()
    return {"success": True, "message": "Seeding started"}


def handle_seed_clear():
    """POST /api/seed/clear - Clear seeded data."""
    import threading
    from seed_fake_data import clear_seeded_data

    global seed_status
    if seed_status["running"]:
        return {"success": False, "error": "Already running"}

    seed_status = {"running": True, "progress": "Clearing...", "step": "clearing", "percent": 50, "result": None, "error": None}

    def run():
        global seed_status
        try:
            clear_seeded_data(get_db())
            seed_status.update({"progress": "Clear complete!", "percent": 100, "step": "done"})
        except Exception as e:
            seed_status.update({"error": str(e), "step": "error"})
        finally:
            seed_status["running"] = False

    threading.Thread(target=run, daemon=True).start()
    return {"success": True, "message": "Clear started"}


# ── Seed status (shared state) ──────────────────────────────────────
seed_status = {"running": False, "progress": "", "step": "", "percent": 0, "result": None, "error": None}


# ── HTTP Request Handler ────────────────────────────────────────────

class AdminAPIHandler(BaseHTTPRequestHandler):

    def _send_json(self, data, status=200):
        body = json_response(data).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path
        params = parse_qs(parsed.query)

        try:
            if path == "/api/admin/stats":
                self._send_json(handle_stats())
            elif path == "/api/admin/users":
                self._send_json(handle_users(params))
            elif path == "/api/admin/groups":
                self._send_json(handle_groups(params))
            elif path == "/api/seed/stats":
                self._send_json(handle_seed_stats())
            elif path == "/api/seed/status":
                self._send_json(seed_status)
            else:
                self._send_json({"error": "Not found"}, 404)
        except Exception as e:
            self._send_json({"error": str(e)}, 500)

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path

        content_length = int(self.headers.get("Content-Length", 0))
        body = {}
        if content_length > 0:
            raw = self.rfile.read(content_length)
            try:
                body = json.loads(raw)
            except json.JSONDecodeError:
                body = {}

        try:
            if path == "/api/seed/run":
                self._send_json(handle_seed_run(body))
            elif path == "/api/seed/clear":
                self._send_json(handle_seed_clear())
            else:
                self._send_json({"error": "Not found"}, 404)
        except Exception as e:
            self._send_json({"error": str(e)}, 500)

    def log_message(self, format, *args):
        print(f"[{datetime.now().strftime('%H:%M:%S')}] {args[0] if args else ''}")


# ── Main ────────────────────────────────────────────────────────────

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Fruvia Admin Dashboard API")
    parser.add_argument("--host", default="localhost")
    parser.add_argument("--port", type=int, default=5000)
    parser.add_argument("--mongo-host", default="localhost")
    parser.add_argument("--mongo-port", type=int, default=27017)
    parser.add_argument("--db", default="fruvia_db")
    args = parser.parse_args()

    MONGO_HOST = args.mongo_host
    MONGO_PORT = args.mongo_port
    MONGO_DB = args.db

    server = HTTPServer((args.host, args.port), AdminAPIHandler)
    print(f"""
╔══════════════════════════════════════════════════════════╗
║        🚀 Fruvia Admin Dashboard API Server             ║
╠══════════════════════════════════════════════════════════╣
║  Server:   http://{args.host}:{args.port:<33}║
║  MongoDB:  {args.mongo_host}:{args.mongo_port}/{args.db:<26}║
╠══════════════════════════════════════════════════════════╣
║  Endpoints:                                             ║
║    GET  /api/admin/stats         Dashboard stats        ║
║    GET  /api/admin/users         User list (paginated)  ║
║    GET  /api/admin/groups        Group list (paginated)  ║
║    GET  /api/seed/stats          DB collection counts   ║
║    GET  /api/seed/status         Seed job status        ║
║    POST /api/seed/run            Start seeding          ║
║    POST /api/seed/clear          Clear seeded data      ║
╚══════════════════════════════════════════════════════════╝
""")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n🛑 Server stopped.")
        server.server_close()
