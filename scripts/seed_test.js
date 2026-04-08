// Full seed: 2 users + friendship for testing message flow
var uid1 = '550e8400-e29b-41d4-a716-446655440001';
var uid2 = '550e8400-e29b-41d4-a716-446655440002';

// BCrypt hash of "Test@1234" ($2b -> $2a for Spring compatibility)
var bcryptHash = '$2a$12$G/XybCpDuG4WYZ4rMOsgDeZ2y1z4P4pbsxHLIqzjfdHKh5BH3cP46';

var now = new Date();

db.user_auth.insertMany([{
        _id: uid1,
        email: 'nguyenquanghuy@fruvia.test',
        passwordHash: bcryptHash,
        salt: 'na',
        accountStatus: 'ACTIVE',
        isTwoFactorEnabled: false,
        isVerified: true,
        createdAt: now,
        updatedAt: now,
        lastLoginAt: now,
        isDeleted: false,
        _class: 'iuh.fit.entity.UserAuth'
    },
    {
        _id: uid2,
        email: 'tranvantest@fruvia.test',
        passwordHash: bcryptHash,
        salt: 'na',
        accountStatus: 'ACTIVE',
        isTwoFactorEnabled: false,
        isVerified: true,
        createdAt: now,
        updatedAt: now,
        lastLoginAt: now,
        isDeleted: false,
        _class: 'iuh.fit.entity.UserAuth'
    }
]);

db.user_detail.insertMany([{
        _id: uid1,
        displayName: 'Nguyen Quang Huy',
        firstName: 'Huy',
        lastName: 'Nguyen',
        avatarUrl: '/default/image1.jpg',
        coverPhotoUrl: '/background/image1.jpg',
        bio: 'Test',
        gender: 'MALE',
        createdAt: now,
        updatedAt: now,
        _class: 'iuh.fit.entity.UserDetail'
    },
    {
        _id: uid2,
        displayName: 'Tran Van Test',
        firstName: 'Test',
        lastName: 'Tran',
        avatarUrl: '/default/image2.jpg',
        coverPhotoUrl: '/background/image2.jpg',
        bio: 'Test',
        gender: 'MALE',
        createdAt: now,
        updatedAt: now,
        _class: 'iuh.fit.entity.UserDetail'
    }
]);

db.user_setting.insertMany([{
        _id: uid1,
        allowFriendRequests: true,
        whoCanSeeProfile: 'PUBLIC',
        whoCanSeePost: 'PUBLIC',
        whoCanTagMe: 'PUBLIC',
        whoCanSendMessages: 'PUBLIC',
        showOnlineStatus: true,
        showReadReceipts: true,
        blockStrangerMessages: false,
        _class: 'iuh.fit.entity.UserSetting'
    },
    {
        _id: uid2,
        allowFriendRequests: true,
        whoCanSeeProfile: 'PUBLIC',
        whoCanSeePost: 'PUBLIC',
        whoCanTagMe: 'PUBLIC',
        whoCanSendMessages: 'PUBLIC',
        showOnlineStatus: true,
        showReadReceipts: true,
        blockStrangerMessages: false,
        _class: 'iuh.fit.entity.UserSetting'
    }
]);

db.friendships.insertMany([{
        _id: 'fs-001',
        userId: uid1,
        friendId: uid2,
        status: 'ACCEPTED',
        createdAt: now,
        updatedAt: now,
        _class: 'iuh.fit.entity.Friendship'
    },
    {
        _id: 'fs-002',
        userId: uid2,
        friendId: uid1,
        status: 'ACCEPTED',
        createdAt: now,
        updatedAt: now,
        _class: 'iuh.fit.entity.Friendship'
    }
]);

print('=== SEED COMPLETE ===');
print('User 1: ' + uid1 + ' | nguyenquanghuy@fruvia.test');
print('User 2: ' + uid2 + ' | tranvantest@fruvia.test');
print('Password: Test@1234 | Verified: YES | Friends: YES');