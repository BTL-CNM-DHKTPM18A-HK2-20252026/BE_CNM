package iuh.fit.service.presence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import iuh.fit.entity.UserAuth;
import iuh.fit.repository.FriendshipRepository;
import iuh.fit.repository.UserAuthRepository;
import iuh.fit.repository.UserSettingRepository;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private UserAuthRepository userAuthRepository;

    @Mock
    private UserSettingRepository userSettingRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PresenceService presenceService;

    @BeforeEach
    void setUp() {
        presenceService = new PresenceService(
                stringRedisTemplate,
                messagingTemplate,
                friendshipRepository,
                userAuthRepository,
                userSettingRepository);
    }

    @Test
    void heartbeatShouldUseLocalFallbackWhenRedisIsDown() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("Redis down"));
        RedisConnectionFailureException redisException = new RedisConnectionFailureException("Redis down");
        org.mockito.Mockito.doThrow(redisException)
                .when(valueOperations)
                .set(anyString(), anyString(), anyLong(), any(TimeUnit.class));

        assertDoesNotThrow(() -> presenceService.heartbeat("user-1"));
        assertTrue(presenceService.isOnline("user-1"));
    }

    @Test
    void userDisconnectedShouldRemoveLocalPresenceEvenWhenRedisIsDown() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("Redis down"));
        RedisConnectionFailureException redisException = new RedisConnectionFailureException("Redis down");
        org.mockito.Mockito.doThrow(redisException)
                .when(valueOperations)
                .set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        when(stringRedisTemplate.delete(anyString())).thenThrow(new RedisConnectionFailureException("Redis down"));
        when(friendshipRepository.findAllAcceptedFriends(anyString())).thenReturn(Collections.emptyList());
        when(userSettingRepository.findById(anyString())).thenReturn(Optional.empty());
        when(userAuthRepository.findById(anyString())).thenReturn(Optional.of(new UserAuth()));

        presenceService.heartbeat("user-2");
        assertTrue(presenceService.isOnline("user-2"));

        assertDoesNotThrow(() -> presenceService.userDisconnected("user-2"));
        assertFalse(presenceService.isOnline("user-2"));
        verify(userAuthRepository).save(any(UserAuth.class));
    }
}