"""
Fruvia Chat - Fake Data Seeder
Generates realistic fake data directly into MongoDB.

Usage:
    pip install pymongo faker
    python seed_fake_data.py [--users 10000] [--host localhost] [--port 27017] [--db fruvia_db]
    python seed_fake_data.py --clear   # Clear all seeded data

Features:
    - Creates users with auth, detail, settings
    - Creates friendships between users
    - Creates private & group conversations
    - Generates messages in conversations
"""

import argparse
import hashlib
import os
import random
import sys
import time
import uuid
from datetime import datetime, timedelta

try:
    from pymongo import MongoClient, InsertOne
    from faker import Faker
except ImportError:
    print("❌ Missing dependencies. Install with:")
    print("   pip install pymongo faker")
    sys.exit(1)

# ── Config ──────────────────────────────────────────────────────────
fake_vi = Faker("vi_VN")
fake_en = Faker("en_US")
Faker.seed(42)
random.seed(42)

DEFAULT_AVATARS = [f"/default/image{i}.jpg" for i in range(1, 9)]
DEFAULT_COVERS = [f"/background/image{i}.jpg" for i in range(1, 4)]

GENDERS = ["MALE", "FEMALE", "OTHER"]
BIOS = [
    "Yêu thích công nghệ 💻", "Coffee addict ☕", "Travel lover ✈️",
    "Foodie 🍜", "Music is life 🎵", "Code all day 🚀",
    "Student life 📚", "Just chilling 😎", "Love cats 🐱",
    "Gym rat 💪", "Photographer 📸", "Gamer 🎮",
    "Designer 🎨", "Bookworm 📖", "Movie buff 🎬",
    "", "", ""  # some users have empty bio
]

MESSAGE_TEMPLATES = [
    "Chào bạn, khỏe không?", "Hôm nay thời tiết đẹp quá!", "Bạn đang làm gì vậy?",
    "Tối nay đi ăn không?", "Gửi cho mình tài liệu được không?", "Ok bạn nhé 👍",
    "Cảm ơn bạn nhiều!", "Để mình xem lại nhé", "Hẹn gặp lại!",
    "Mình đang bận, lát nữa nhé", "Wow, tuyệt vời quá! 🎉", "Ê, có rảnh không?",
    "Mình vừa xong task rồi", "Check email đi bạn", "Deadline mai kìa 😱",
    "Hello!", "Good morning ☀️", "See you later!",
    "Thanks!", "No problem 😊", "Let's go! 🚀",
    "Bạn ơi check code giúp mình với", "Merge request xong chưa?",
    "Bug fix rồi nha", "Deploy production chưa?", "Meeting lúc mấy giờ?",
    "Đi cafe không?", "Cuối tuần làm gì?", "Xem phim không bạn?",
    "Mình gửi file rồi nha", "Link đây nè", "Xong rồi!",
]

GROUP_NAMES = [
    "Nhóm học tập CNM", "Team Project Spring Boot", "Bạn bè IUH",
    "Gia đình yêu thương", "Đồ án tốt nghiệp K20", "Club Lập trình",
    "Nhóm đi phượt", "Đội thi ACM", "Gaming Squad",
    "Nhóm ôn thi", "Lớp DHKTPM17B", "React Developers VN",
    "Java Lovers", "Coffee & Code", "Startup Ideas",
    "Nhóm bóng đá", "Movie Night", "Book Club",
    "Nhóm chạy bộ", "Fitness Goals", "Travel Vietnam",
]


def generate_salt():
    return os.urandom(16).hex()


def hash_password(password: str, salt: str) -> str:
    return hashlib.sha256((password + salt).encode()).hexdigest()


def random_datetime(start_days_ago=365, end_days_ago=0):
    start = datetime.now() - timedelta(days=start_days_ago)
    end = datetime.now() - timedelta(days=end_days_ago)
    delta = end - start
    random_seconds = random.randint(0, int(delta.total_seconds()))
    return start + timedelta(seconds=random_seconds)


def generate_phone():
    prefixes = ["03", "05", "07", "08", "09"]
    return random.choice(prefixes) + "".join([str(random.randint(0, 9)) for _ in range(8)])


# ── Generators ──────────────────────────────────────────────────────

def create_users(num_users: int, progress_callback=None):
    """Generate user_auth, user_detail, user_setting documents."""
    user_auths = []
    user_details = []
    user_settings = []
    user_ids = []
    used_phones = set()
    used_emails = set()

    for i in range(num_users):
        user_id = str(uuid.uuid4())
        user_ids.append(user_id)

        # Unique phone & email
        phone = generate_phone()
        while phone in used_phones:
            phone = generate_phone()
        used_phones.add(phone)

        email = f"user{i+1}_{fake_en.unique.user_name()}@fruvia.test"
        while email in used_emails:
            email = f"user{i+1}_{uuid.uuid4().hex[:6]}@fruvia.test"
        used_emails.add(email)

        salt = generate_salt()
        created_at = random_datetime(365, 30)

        # UserAuth
        user_auths.append({
            "_id": user_id,
            "phoneNumber": phone,
            "email": email,
            "passwordHash": hash_password("password123", salt),
            "salt": salt,
            "accountStatus": random.choices(["ACTIVE", "LOCKED", "BANNED"], weights=[95, 3, 2])[0],
            "isTwoFactorEnabled": random.choice([True, False]),
            "createdAt": created_at,
            "updatedAt": created_at,
            "lastLoginAt": random_datetime(7, 0),
            "isDeleted": False,
            "_class": "iuh.fit.entity.UserAuth",
        })

        # UserDetail
        first_name = fake_vi.first_name()
        last_name = fake_vi.last_name()
        display_name = f"{last_name} {first_name}"
        dob = fake_vi.date_of_birth(minimum_age=18, maximum_age=40)

        user_details.append({
            "_id": user_id,
            "displayName": display_name,
            "firstName": first_name,
            "lastName": last_name,
            "avatarUrl": random.choice(DEFAULT_AVATARS),
            "coverPhotoUrl": random.choice(DEFAULT_COVERS),
            "bio": random.choice(BIOS),
            "dob": datetime.combine(dob, datetime.min.time()),
            "gender": random.choice(GENDERS),
            "address": fake_vi.street_address(),
            "city": fake_vi.city(),
            "education": random.choice(["IUH", "HCMUT", "UIT", "FPT", "HUTECH", "HCMUS", "TDT", ""]),
            "occupation": random.choice(["Sinh viên", "Lập trình viên", "Designer", "Kỹ sư", "Freelancer", ""]),
            "createdAt": created_at,
            "updatedAt": created_at,
            "_class": "iuh.fit.entity.UserDetail",
        })

        # UserSetting
        user_settings.append({
            "_id": user_id,
            "allowFriendRequests": True,
            "whoCanSeeProfile": random.choice(["PUBLIC", "FRIEND_ONLY"]),
            "whoCanSeePost": random.choice(["PUBLIC", "FRIEND_ONLY"]),
            "whoCanTagMe": random.choice(["PUBLIC", "FRIEND_ONLY"]),
            "whoCanSendMessages": random.choice(["PUBLIC", "FRIEND_ONLY"]),
            "showOnlineStatus": random.choice([True, False]),
            "showReadReceipts": True,
            "_class": "iuh.fit.entity.UserSetting",
        })

        if progress_callback and (i + 1) % 500 == 0:
            progress_callback("users", i + 1, num_users)

    return user_ids, user_auths, user_details, user_settings


def create_friendships(user_ids: list, avg_friends=20, progress_callback=None):
    """Generate friendship documents."""
    friendships = []
    friendship_set = set()
    num_users = len(user_ids)
    total = 0

    for i, uid in enumerate(user_ids):
        num_friends = min(random.randint(5, avg_friends * 2), num_users - 1)
        friend_indices = random.sample(
            [j for j in range(num_users) if j != i], min(num_friends, num_users - 1)
        )

        for fi in friend_indices:
            pair = tuple(sorted([uid, user_ids[fi]]))
            if pair in friendship_set:
                continue
            friendship_set.add(pair)

            created = random_datetime(300, 10)
            status = random.choices(["ACCEPTED", "PENDING", "DECLINED"], weights=[85, 10, 5])[0]

            friendships.append({
                "_id": str(uuid.uuid4()),
                "requesterId": pair[0],
                "receiverId": pair[1],
                "status": status,
                "message": random.choice(["Kết bạn nhé!", "Hi!", "Hello!", "Mình là bạn cùng lớp", ""]),
                "createdAt": created,
                "updatedAt": created,
                "_class": "iuh.fit.entity.Friendship",
            })
            total += 1

        if progress_callback and (i + 1) % 500 == 0:
            progress_callback("friendships", i + 1, num_users)

    return friendships


def create_conversations_and_messages(user_ids: list, num_private=5000, num_groups=200,
                                       msgs_per_private=15, msgs_per_group=50,
                                       progress_callback=None):
    """Generate conversations, conversation_members, and messages."""
    conversations = []
    conv_members = []
    messages = []
    num_users = len(user_ids)

    # ── Private conversations ──
    private_pairs = set()
    for i in range(min(num_private, num_users * (num_users - 1) // 2)):
        while True:
            a, b = random.sample(range(num_users), 2)
            pair = tuple(sorted([a, b]))
            if pair not in private_pairs:
                private_pairs.add(pair)
                break

        conv_id = str(uuid.uuid4())
        created = random_datetime(300, 5)
        participants = sorted([user_ids[pair[0]], user_ids[pair[1]]])

        # Generate messages for this conversation
        num_msgs = random.randint(3, msgs_per_private * 2)
        conv_messages = []
        msg_time = created

        for _ in range(num_msgs):
            msg_time += timedelta(minutes=random.randint(1, 480))
            if msg_time > datetime.now():
                break
            sender = random.choice(participants)
            msg_id = str(uuid.uuid4())
            content = random.choice(MESSAGE_TEMPLATES)

            conv_messages.append({
                "_id": msg_id,
                "conversationId": conv_id,
                "senderId": sender,
                "messageType": "TEXT",
                "content": content,
                "replyToMessageId": None,
                "createdAt": msg_time,
                "updatedAt": msg_time,
                "isDeleted": False,
                "isRecalled": False,
                "isEdited": False,
                "editHistory": [],
                "localDeletedBy": [],
                "_class": "iuh.fit.entity.Message",
            })

        last_msg = conv_messages[-1] if conv_messages else None

        conversations.append({
            "_id": conv_id,
            "conversationType": "PRIVATE",
            "conversationStatus": "NORMAL",
            "participants": participants,
            "conversationName": None,
            "avatarUrl": None,
            "creatorId": participants[0],
            "createdAt": created,
            "lastMessageId": last_msg["_id"] if last_msg else None,
            "lastMessageContent": last_msg["content"] if last_msg else None,
            "lastMessageTime": last_msg["createdAt"] if last_msg else None,
            "isPinned": False,
            "groupDescription": None,
            "updatedAt": last_msg["createdAt"] if last_msg else created,
            "isDeleted": False,
            "_class": "iuh.fit.entity.Conversations",
        })

        for p in participants:
            conv_members.append({
                "_id": str(uuid.uuid4()),
                "conversationId": conv_id,
                "userId": p,
                "role": "MEMBER",
                "joinedAt": created,
                "nickname": None,
                "lastReadMessageId": last_msg["_id"] if last_msg else None,
                "lastReadAt": last_msg["createdAt"] if last_msg else None,
                "isPinned": random.random() < 0.1,
                "pinnedAt": None,
                "isHidden": False,
                "conversationTag": None,
                "_class": "iuh.fit.entity.ConversationMember",
            })

        messages.extend(conv_messages)

        if progress_callback and (i + 1) % 500 == 0:
            progress_callback("private_convs", i + 1, num_private)

    # ── Group conversations ──
    for i in range(num_groups):
        conv_id = str(uuid.uuid4())
        created = random_datetime(200, 5)
        num_members = random.randint(3, min(50, num_users))
        member_indices = random.sample(range(num_users), num_members)
        participants = sorted([user_ids[mi] for mi in member_indices])
        creator = participants[0]

        # Generate messages
        num_msgs = random.randint(10, msgs_per_group * 2)
        conv_messages = []
        msg_time = created

        for _ in range(num_msgs):
            msg_time += timedelta(minutes=random.randint(1, 240))
            if msg_time > datetime.now():
                break
            sender = random.choice(participants)
            msg_id = str(uuid.uuid4())
            content = random.choice(MESSAGE_TEMPLATES)

            conv_messages.append({
                "_id": msg_id,
                "conversationId": conv_id,
                "senderId": sender,
                "messageType": "TEXT",
                "content": content,
                "replyToMessageId": None,
                "createdAt": msg_time,
                "updatedAt": msg_time,
                "isDeleted": False,
                "isRecalled": False,
                "isEdited": False,
                "editHistory": [],
                "localDeletedBy": [],
                "_class": "iuh.fit.entity.Message",
            })

        last_msg = conv_messages[-1] if conv_messages else None
        group_name = random.choice(GROUP_NAMES) + f" #{i+1}"

        conversations.append({
            "_id": conv_id,
            "conversationType": "GROUP",
            "conversationStatus": "NORMAL",
            "participants": participants,
            "conversationName": group_name,
            "avatarUrl": random.choice(DEFAULT_AVATARS),
            "creatorId": creator,
            "createdAt": created,
            "lastMessageId": last_msg["_id"] if last_msg else None,
            "lastMessageContent": last_msg["content"] if last_msg else None,
            "lastMessageTime": last_msg["createdAt"] if last_msg else None,
            "isPinned": False,
            "groupDescription": f"Nhóm chat {group_name}",
            "updatedAt": last_msg["createdAt"] if last_msg else created,
            "isDeleted": False,
            "_class": "iuh.fit.entity.Conversations",
        })

        for idx, p in enumerate(participants):
            role = "ADMIN" if p == creator else ("DEPUTY" if idx == 1 else "MEMBER")
            conv_members.append({
                "_id": str(uuid.uuid4()),
                "conversationId": conv_id,
                "userId": p,
                "role": role,
                "joinedAt": created + timedelta(minutes=idx),
                "nickname": None,
                "lastReadMessageId": last_msg["_id"] if last_msg else None,
                "lastReadAt": last_msg["createdAt"] if last_msg else None,
                "isPinned": False,
                "pinnedAt": None,
                "isHidden": False,
                "conversationTag": None,
                "_class": "iuh.fit.entity.ConversationMember",
            })

        messages.extend(conv_messages)

        if progress_callback and (i + 1) % 50 == 0:
            progress_callback("group_convs", i + 1, num_groups)

    return conversations, conv_members, messages


# ── Database Operations ─────────────────────────────────────────────

def bulk_insert(collection, docs, batch_size=5000):
    """Insert documents in batches."""
    total = len(docs)
    inserted = 0
    for i in range(0, total, batch_size):
        batch = docs[i:i + batch_size]
        collection.insert_many(batch, ordered=False)
        inserted += len(batch)
    return inserted


def clear_seeded_data(db):
    """Clear all seeded (fake) data from database."""
    print("\n🗑️  Clearing seeded data...")

    # Clear by the test email pattern
    result = db.user_auth.delete_many({"email": {"$regex": r"@fruvia\.test$"}})
    print(f"   Deleted {result.deleted_count} user_auth records")

    # Get IDs of deleted users to clean related data
    # Since we already deleted, we clean by pattern
    result = db.user_detail.delete_many({"education": {"$in": ["IUH", "HCMUT", "UIT", "FPT", "HUTECH", "HCMUS", "TDT"]}})
    print(f"   Deleted {result.deleted_count} user_detail records")

    result = db.user_setting.delete_many({})
    print(f"   Deleted {result.deleted_count} user_setting records")

    result = db.friendships.delete_many({"message": {"$in": ["Kết bạn nhé!", "Hi!", "Hello!", "Mình là bạn cùng lớp", ""]}})
    print(f"   Deleted {result.deleted_count} friendship records")

    result = db.conversations.delete_many({"isDeleted": False})
    print(f"   Deleted {result.deleted_count} conversation records")

    result = db.conversation_member.delete_many({})
    print(f"   Deleted {result.deleted_count} conversation_member records")

    result = db.messages.delete_many({"isDeleted": False})
    print(f"   Deleted {result.deleted_count} message records")

    print("✅ Clear complete!")


def seed(host="localhost", port=27017, db_name="fruvia_db",
         num_users=10000, num_private=5000, num_groups=200,
         msgs_per_private=15, msgs_per_group=50):
    """Main seeding function."""

    print(f"""
╔══════════════════════════════════════════════════════════╗
║           🌱 Fruvia Chat - Data Seeder                  ║
╠══════════════════════════════════════════════════════════╣
║  MongoDB:      {host}:{port}/{db_name:<26}║
║  Users:        {num_users:<40}║
║  Private Convs:{num_private:<40}║
║  Group Convs:  {num_groups:<40}║
║  Msgs/Private: ~{msgs_per_private:<39}║
║  Msgs/Group:   ~{msgs_per_group:<39}║
╚══════════════════════════════════════════════════════════╝
""")

    client = MongoClient(host, port)
    db = client[db_name]

    start_time = time.time()

    def progress(step, current, total):
        pct = current / total * 100
        print(f"   [{step}] {current}/{total} ({pct:.0f}%)")

    # ── Step 1: Generate Users ──
    print("👥 Step 1/4: Generating users...")
    t0 = time.time()
    user_ids, user_auths, user_details, user_settings = create_users(num_users, progress)
    print(f"   Generated {len(user_auths)} users in {time.time()-t0:.1f}s")

    print("   Inserting into MongoDB...")
    t0 = time.time()
    bulk_insert(db.user_auth, user_auths)
    bulk_insert(db.user_detail, user_details)
    bulk_insert(db.user_setting, user_settings)
    print(f"   Inserted in {time.time()-t0:.1f}s")

    # ── Step 2: Generate Friendships ──
    print("\n🤝 Step 2/4: Generating friendships...")
    t0 = time.time()
    friendships = create_friendships(user_ids, progress_callback=progress)
    print(f"   Generated {len(friendships)} friendships in {time.time()-t0:.1f}s")

    print("   Inserting into MongoDB...")
    t0 = time.time()
    bulk_insert(db.friendships, friendships)
    print(f"   Inserted in {time.time()-t0:.1f}s")

    # ── Step 3: Generate Conversations & Messages ──
    print("\n💬 Step 3/4: Generating conversations & messages...")
    t0 = time.time()
    conversations, conv_members, messages_list = create_conversations_and_messages(
        user_ids, num_private, num_groups, msgs_per_private, msgs_per_group, progress
    )
    print(f"   Generated {len(conversations)} conversations, {len(conv_members)} members, {len(messages_list)} messages in {time.time()-t0:.1f}s")

    print("   Inserting into MongoDB...")
    t0 = time.time()
    bulk_insert(db.conversations, conversations)
    bulk_insert(db.conversation_member, conv_members)
    bulk_insert(db.messages, messages_list)
    print(f"   Inserted in {time.time()-t0:.1f}s")

    # ── Step 4: Summary ──
    elapsed = time.time() - start_time
    print(f"""
╔══════════════════════════════════════════════════════════╗
║                 ✅ Seeding Complete!                     ║
╠══════════════════════════════════════════════════════════╣
║  Users:          {len(user_auths):<38}║
║  Friendships:    {len(friendships):<38}║
║  Conversations:  {len(conversations):<38}║
║  Conv Members:   {len(conv_members):<38}║
║  Messages:       {len(messages_list):<38}║
║  Total Time:     {elapsed:.1f}s{' '*(37-len(f'{elapsed:.1f}s'))}║
╚══════════════════════════════════════════════════════════╝
""")

    client.close()

    return {
        "users": len(user_auths),
        "friendships": len(friendships),
        "conversations": len(conversations),
        "conv_members": len(conv_members),
        "messages": len(messages_list),
        "elapsed": round(elapsed, 1),
    }


# ── CLI ─────────────────────────────────────────────────────────────

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Fruvia Chat - Fake Data Seeder")
    parser.add_argument("--host", default="localhost", help="MongoDB host")
    parser.add_argument("--port", type=int, default=27017, help="MongoDB port")
    parser.add_argument("--db", default="fruvia_db", help="Database name")
    parser.add_argument("--users", type=int, default=10000, help="Number of users")
    parser.add_argument("--private", type=int, default=5000, help="Number of private conversations")
    parser.add_argument("--groups", type=int, default=200, help="Number of group conversations")
    parser.add_argument("--msgs-private", type=int, default=15, help="Avg messages per private conv")
    parser.add_argument("--msgs-group", type=int, default=50, help="Avg messages per group conv")
    parser.add_argument("--clear", action="store_true", help="Clear seeded data instead of seeding")

    args = parser.parse_args()

    if args.clear:
        client = MongoClient(args.host, args.port)
        clear_seeded_data(client[args.db])
        client.close()
    else:
        seed(
            host=args.host,
            port=args.port,
            db_name=args.db,
            num_users=args.users,
            num_private=args.private,
            num_groups=args.groups,
            msgs_per_private=args.msgs_private,
            msgs_per_group=args.msgs_group,
        )
