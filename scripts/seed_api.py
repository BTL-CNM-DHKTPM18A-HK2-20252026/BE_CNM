"""
Fruvia Chat - Seed API Server
Lightweight Flask server to expose seeding operations via REST API.
Called from the CNM_Dashboard admin panel.

Usage:
    pip install pymongo faker flask flask-cors
    python seed_api.py
"""

import threading
import time
from flask import Flask, jsonify, request
from flask_cors import CORS
from pymongo import MongoClient
from seed_fake_data import seed, clear_seeded_data

app = Flask(__name__)
CORS(app)

# ── State ───────────────────────────────────────────────────────────
seed_status = {
    "running": False,
    "progress": "",
    "step": "",
    "percent": 0,
    "result": None,
    "error": None,
}

MONGO_HOST = "localhost"
MONGO_PORT = 27017
MONGO_DB = "fruvia_db"


def get_db():
    client = MongoClient(MONGO_HOST, MONGO_PORT)
    return client, client[MONGO_DB]


# ── Routes ──────────────────────────────────────────────────────────

@app.route("/api/seed/status", methods=["GET"])
def get_status():
    return jsonify(seed_status)


@app.route("/api/seed/stats", methods=["GET"])
def get_stats():
    """Get current collection counts from MongoDB."""
    try:
        client, db = get_db()
        stats = {
            "users": db.user_auth.count_documents({}),
            "friendships": db.friendships.count_documents({}),
            "conversations": db.conversations.count_documents({}),
            "messages": db.messages.count_documents({}),
            "conv_members": db.conversation_member.count_documents({}),
            "user_details": db.user_detail.count_documents({}),
            "user_settings": db.user_setting.count_documents({}),
        }
        client.close()
        return jsonify({"success": True, "data": stats})
    except Exception as e:
        return jsonify({"success": False, "error": str(e)}), 500


@app.route("/api/seed/run", methods=["POST"])
def run_seed():
    """Start seeding in a background thread."""
    global seed_status

    if seed_status["running"]:
        return jsonify({"success": False, "error": "Seeding already in progress"}), 409

    body = request.json or {}
    num_users = min(int(body.get("users", 10000)), 50000)
    num_private = min(int(body.get("private", 5000)), 30000)
    num_groups = min(int(body.get("groups", 200)), 2000)
    msgs_private = min(int(body.get("msgsPrivate", 15)), 100)
    msgs_group = min(int(body.get("msgsGroup", 50)), 200)

    seed_status = {
        "running": True,
        "progress": "Starting...",
        "step": "init",
        "percent": 0,
        "result": None,
        "error": None,
    }

    def run():
        global seed_status
        try:
            seed_status["step"] = "seeding"
            seed_status["progress"] = "Generating data..."
            result = seed(
                host=MONGO_HOST,
                port=MONGO_PORT,
                db_name=MONGO_DB,
                num_users=num_users,
                num_private=num_private,
                num_groups=num_groups,
                msgs_per_private=msgs_private,
                msgs_per_group=msgs_group,
            )
            seed_status["result"] = result
            seed_status["progress"] = "Complete!"
            seed_status["percent"] = 100
            seed_status["step"] = "done"
        except Exception as e:
            seed_status["error"] = str(e)
            seed_status["progress"] = f"Error: {e}"
            seed_status["step"] = "error"
        finally:
            seed_status["running"] = False

    thread = threading.Thread(target=run, daemon=True)
    thread.start()

    return jsonify({"success": True, "message": "Seeding started"})


@app.route("/api/seed/clear", methods=["POST"])
def run_clear():
    """Clear seeded data."""
    global seed_status

    if seed_status["running"]:
        return jsonify({"success": False, "error": "Operation already in progress"}), 409

    seed_status = {
        "running": True,
        "progress": "Clearing data...",
        "step": "clearing",
        "percent": 50,
        "result": None,
        "error": None,
    }

    def run():
        global seed_status
        try:
            client, db = get_db()
            clear_seeded_data(db)
            client.close()
            seed_status["progress"] = "Clear complete!"
            seed_status["percent"] = 100
            seed_status["step"] = "done"
        except Exception as e:
            seed_status["error"] = str(e)
            seed_status["progress"] = f"Error: {e}"
            seed_status["step"] = "error"
        finally:
            seed_status["running"] = False

    thread = threading.Thread(target=run, daemon=True)
    thread.start()

    return jsonify({"success": True, "message": "Clear started"})


if __name__ == "__main__":
    print("🚀 Seed API Server running on http://localhost:5000")
    app.run(host="0.0.0.0", port=5000, debug=False)
