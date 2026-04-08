// Verify users and create friendship
db.user_auth.updateMany({}, {
    $set: {
        isVerified: true
    }
});

var users = db.user_auth.find({}, {
    email: 1
}).toArray();
var uid1 = users[0]._id;
var uid2 = users[1]._id;
print('User1: ' + uid1 + ' | ' + users[0].email);
print('User2: ' + uid2 + ' | ' + users[1].email);

// Create friendship
db.friendships.insertMany([{
        _id: 'fs-test-001',
        userId: uid1,
        friendId: uid2,
        status: 'ACCEPTED',
        createdAt: new Date(),
        updatedAt: new Date(),
        _class: 'iuh.fit.entity.Friendship'
    },
    {
        _id: 'fs-test-002',
        userId: uid2,
        friendId: uid1,
        status: 'ACCEPTED',
        createdAt: new Date(),
        updatedAt: new Date(),
        _class: 'iuh.fit.entity.Friendship'
    }
]);

print('Verified & friendship created');